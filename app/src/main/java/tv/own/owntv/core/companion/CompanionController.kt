package tv.own.owntv.core.companion

import android.content.Context
import android.util.Log
import java.io.File
import java.net.BindException
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.myowntv.R
import tv.own.owntv.core.i18n.LocaleStore

/**
 * Owns the Remote companion HTTP listener for the app's lifetime. Registered as a Koin `single` so
 * the Settings screens share one server instead of each carrying duplicate networking code.
 *
 * The remote pages are the backup restore/download pair and the background-image upload; local sync
 * between two OwnTV devices rides the same listener without a web page. Remote add/edit-source pages
 * are gone — the app has one built-in provider and nothing to type into a web form.
 *
 * Uploaded backups and images are saved to the cache and emitted on [backups]/[images] for the
 * corresponding screens.
 *
 * [start]/[stop] are safe to call from the UI thread; the server does its I/O on its own dispatcher.
 */
class CompanionController(context: Context, localeStore: LocaleStore) {

    private val appContext = context.applicationContext
    private val server = CompanionHttpServer(appContext, localeStore)

    private val _state = MutableStateFlow<CompanionServerState>(CompanionServerState.Idle)
    val state: StateFlow<CompanionServerState> = _state.asStateFlow()

    /** Backup files uploaded from the remote device in [startForBackupRestore] mode, saved to cache and emitted here. */
    private val _backups = MutableSharedFlow<File>(extraBufferCapacity = 4)
    val backups: SharedFlow<File> = _backups.asSharedFlow()

    /** Cache folder the remote-export flow writes the backup into before serving it for download. */
    val backupExportDir: File get() = File(appContext.cacheDir, "remote-backup-export").apply { mkdirs() }

    /** Image files uploaded from the remote device in [startForImageUpload] mode, saved to cache and emitted here. */
    private val _images = MutableSharedFlow<File>(extraBufferCapacity = 4)
    val images: SharedFlow<File> = _images.asSharedFlow()

    /** A fresh 6-digit PIN per [startForBackupRestore], so a leaked code is short-lived. */
    @Volatile private var currentPin: String = ""

    /**
     * A device waiting for the answer to "may I sync with you automatically?".
     *
     * The request names the device rather than its secret: the secret never leaves the server's auth
     * check, and all the person answering needs to see is which paired device is asking, and what it
     * would sync ([theirSections]).
     */
    data class AutoSyncRequest(
        val id: String,
        val deviceId: String,
        val deviceName: String,
        val theirSections: Set<tv.own.owntv.core.backup.BackupManager.Section> = emptySet(),
    )

    private val _autoSyncRequests = MutableStateFlow<List<AutoSyncRequest>>(emptyList())

    /** The requests still waiting for an answer, oldest first. Usually zero or one. */
    val autoSyncRequests: StateFlow<List<AutoSyncRequest>> = _autoSyncRequests.asStateFlow()

    /** The answer each request is waiting on, keyed by [AutoSyncRequest.id]. */
    private val autoSyncAnswers = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /**
     * Puts [deviceName]'s request on screen and suspends until the person here answers, or until the
     * caller's own timeout would run out.
     *
     * Answering `false` on timeout is deliberate: a request nobody saw must not enable anything, and
     * the device that asked is told so rather than left waiting for an answer that is not coming.
     */
    suspend fun askAutoSync(
        deviceId: String,
        deviceName: String,
        theirSections: Set<tv.own.owntv.core.backup.BackupManager.Section> = emptySet(),
    ): Boolean {
        // A device that asks again is asking a fresh question: its previous request belongs to a
        // screen that has already stopped waiting, and leaving it up would put two prompts for the
        // same device in front of the person — who could answer the stale one and believe they
        // answered the new one.
        _autoSyncRequests.update { requests ->
            requests.filter { it.deviceId == deviceId }.forEach { stale ->
                autoSyncAnswers.remove(stale.id)?.complete(false)
            }
            requests.filterNot { it.deviceId == deviceId }
        }
        val request = AutoSyncRequest(UUID.randomUUID().toString(), deviceId, deviceName, theirSections)
        val answer = CompletableDeferred<Boolean>()
        autoSyncAnswers[request.id] = answer
        _autoSyncRequests.update { it + request }
        return try {
            withTimeoutOrNull(AUTO_SYNC_ANSWER_TIMEOUT_MS) { answer.await() } ?: false
        } finally {
            autoSyncAnswers.remove(request.id)
            _autoSyncRequests.update { requests -> requests.filterNot { it.id == request.id } }
        }
    }

    /** The answer from the screen. Unknown or already-timed-out requests are ignored. */
    fun answerAutoSync(requestId: String, accepted: Boolean) {
        autoSyncAnswers[requestId]?.complete(accepted)
    }

    /** Fails every waiting request, so no answer outlives the listener that asked for it. */
    private fun cancelAutoSyncRequests() {
        autoSyncAnswers.values.forEach { it.complete(false) }
        autoSyncAnswers.clear()
        _autoSyncRequests.update { emptyList() }
    }

    /** Bundled Lora TTF bytes, loaded once and served on the web form; null if the resource can't be read. */
    private val loraBytes: ByteArray? by lazy {
        // openRawResource serves any file-backed resource, not just res/raw; a font is exactly that,
        // and we want the TTF bytes verbatim rather than a loaded Typeface. runCatching covers the
        // resource being unavailable, which is the only thing the lint check is protecting against.
        @Suppress("ResourceType")
        runCatching { appContext.resources.openRawResource(R.font.lora_variable).use { it.readBytes() } }
            .onFailure { Log.w(TAG, "Could not load Lora for companion web form; falling back to serif", it) }
            .getOrNull()
    }

    /** Starts the companion server in backup-restore mode: the remote device uploads a backup JSON, emitted on [backups]. */
    fun startForBackupRestore(port: Int) = startInternal(port, CompanionMode.BACKUP_RESTORE)

    /** Starts the companion server in backup-download mode, serving [file] for the remote device to download. */
    fun startForBackupDownload(port: Int, file: File) = startInternal(port, CompanionMode.BACKUP_DOWNLOAD, file)

    /**
     * Starts the companion server for local sync between two OwnTV devices.
     *
     * The only mode that both receives and serves in one session, because a merge does both: an
     * uploaded container arrives on [backups] exactly as it does in backup-restore mode, and [file],
     * this device's own export prepared by the caller, is what the other device downloads.
     *
     * [info] answers "who are you", [onPair] mints a lasting secret for a device that presented the
     * right PIN, and [secrets] are the ones already issued, each accepted in place of the PIN so only
     * the first sync between two devices asks the user for anything. [onAutoSyncAsk] is a paired
     * device asking to set up automatic sync: it gets the asker's terms and returns the answer.
     */
    fun startForLocalSync(
        port: Int,
        file: File?,
        info: () -> String,
        onPair: (name: String, address: String, deviceId: String) -> String?,
        secrets: () -> Map<String, String>,
        onAutoSyncAsk: suspend (deviceId: String, request: org.json.JSONObject) -> org.json.JSONObject,
    ) = startInternal(
        port = port,
        mode = CompanionMode.LOCAL_SYNC,
        downloadFile = file,
        syncInfo = info,
        onPair = onPair,
        pairedSecrets = secrets,
        onAutoSyncAsk = onAutoSyncAsk,
    )

    /** Starts the companion server in image-upload mode: the remote device sends a background image, emitted on [images]. */
    fun startForImageUpload(port: Int) = startInternal(port, CompanionMode.IMAGE_UPLOAD)

    private fun startInternal(
        port: Int,
        mode: CompanionMode,
        downloadFile: File? = null,
        syncInfo: () -> String = { "{}" },
        onPair: (name: String, address: String, deviceId: String) -> String? = { _, _, _ -> null },
        pairedSecrets: () -> Map<String, String> = { emptyMap() },
        onAutoSyncAsk: suspend (deviceId: String, request: org.json.JSONObject) -> org.json.JSONObject = { _, _ -> org.json.JSONObject() },
    ) {
        if (port !in 1..65535) {
            _state.value = CompanionServerState.Failed(CompanionFailure.InvalidPort)
            return
        }
        _state.value = CompanionServerState.Starting
        try {
            currentPin = generatePin()
            val urls = server.start(
                port = port,
                pin = currentPin,
                fontBytes = loraBytes,
                mode = mode,
                onBackup = ::onBackupUploaded,
                onImage = ::onImageUploaded,
                downloadFile = downloadFile,
                syncInfo = syncInfo,
                onPair = onPair,
                pairedSecrets = pairedSecrets,
                onAutoSyncAsk = onAutoSyncAsk,
                onLocked = {
                    // The server has already stopped itself; just reflect it on the TV so the user
                    // knows why the link went away and that restarting gives them a new PIN.
                    Log.w(TAG, "Companion link locked out after repeated wrong PINs")
                    currentPin = ""
                    _state.value = CompanionServerState.Locked
                },
            )
            _state.value = CompanionServerState.Listening(
                port = port,
                urls = urls,
                pin = currentPin,
                // The QR encodes the plain URL only — the PIN is typed on the remote device's gate page.
                qr = CompanionLink.renderQr(CompanionLink.lanUrl(port)),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start companion listener", t)
            _state.value = CompanionServerState.Failed(friendlyFailure(t, port))
        }
    }

    /**
     * Persists an uploaded backup to a cache file and emits it for the restore UI to inspect.
     *
     * Two wire shapes, because a `.own` container is binary while the old `.json` was not:
     * a **data-URL** (`data:...;base64,…`) is decoded back to bytes — the same trick the background
     * image upload uses, so the socket layer stays text-only — and anything else is a legacy JSON
     * backup pasted through verbatim. The extension follows suit so the restore path's format sniff
     * and the temp file agree.
     */
    private fun onBackupUploaded(text: String) {
        runCatching {
            val base64 = text.substringAfter("base64,", missingDelimiterValue = "")
                .takeIf { text.startsWith("data:") && it.isNotBlank() }
            val bytes = base64?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
            val file = File.createTempFile(
                "owntv-remote-restore",
                if (bytes != null) ".own" else ".json",
                appContext.cacheDir,
            )
            if (bytes != null) file.writeBytes(bytes) else file.writeText(text)
            Log.d(TAG, "Received remote backup (${file.length()} bytes) → ${file.name}")
            file
        }.onSuccess { _backups.tryEmit(it) }
            .onFailure { Log.w(TAG, "Failed to persist uploaded backup", it) }
    }

    /** Persists an uploaded background image to a cache file and emits it for the settings UI to ingest. */
    private fun onImageUploaded(bytes: ByteArray, extension: String) {
        runCatching {
            val file = File.createTempFile("owntv-remote-bg", ".$extension", appContext.cacheDir)
            file.writeBytes(bytes)
            Log.d(TAG, "Received remote background image (${bytes.size} bytes) → ${file.name}")
            file
        }.onSuccess { _images.tryEmit(it) }
            .onFailure { Log.w(TAG, "Failed to persist uploaded image", it) }
    }

    fun stop() {
        server.stop()
        cancelAutoSyncRequests()
        currentPin = ""
        _state.value = CompanionServerState.Idle
    }

    /** Permanent teardown for the app-wide singleton. */
    fun dispose() {
        server.close()
        cancelAutoSyncRequests()
        _state.value = CompanionServerState.Idle
    }

    private fun generatePin(): String = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    private fun friendlyFailure(t: Throwable, port: Int): CompanionFailure = when (t) {
        is BindException -> CompanionFailure.PortInUse(port)
        else -> CompanionFailure.Unavailable
    }

    private companion object {
        const val TAG = "CompanionController"

        /**
         * How long the person here has to answer an auto-sync request. Shorter than the client's
         * 60 s read timeout on purpose: the answer has to be written back before the caller gives up.
         */
        const val AUTO_SYNC_ANSWER_TIMEOUT_MS = 50_000L
    }
}
