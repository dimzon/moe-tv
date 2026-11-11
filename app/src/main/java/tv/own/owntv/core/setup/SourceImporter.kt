package tv.own.owntv.core.setup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.ImportFinalizer
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.sync.SyncWarning
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.util.FriendlySyncFailure
import tv.own.owntv.core.util.classifySyncFailure
import java.io.File

/**
 * Onboarding: attach the one built-in source, sync it, finalize it, enqueue whatever was deferred —
 * and, when any of that fails, undo exactly as much as is safe to undo. RuTube is the only provider
 * and needs no credentials or address, so there is nothing to import or type in; the wizard calls
 * [ensureRutube] and the canvas drives the same [ImportState] the old add-source flows did.
 *
 * Backup restore (from the wizard's "from another device") rides here too: it goes through
 * [BackupManager] and re-syncs whatever the restore brought back.
 *
 * Every function here suspends and the **caller owns the job**, because "Run in background" is the
 * whole reason the job outlives its screen: the host launches into a scope that survives the wizard
 * (an activity-scoped ViewModel), sets [backgroundHandoff], and walks away while the import finishes.
 * Cancelling the caller's job cancels the import and runs the same cleanup.
 */
class SourceImporter(
    private val sourceDao: SourceDao,
    private val sourceRepository: SourceRepository,
    private val backup: BackupManager,
    private val connectivity: ConnectivityObserver,
    private val importFinalizer: ImportFinalizer,
    private val catalogSyncScheduler: CatalogSyncScheduler,
) {

    sealed interface SetupFailure {
        data object BackupRead : SetupFailure
        data object Restore : SetupFailure
        data class Sync(val failure: FriendlySyncFailure) : SetupFailure
    }

    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        data class Success(
            val counts: SyncCounts? = null,
            val warnings: List<SyncWarning> = emptyList(),
            val remainder: SyncContentTypes = SyncContentTypes(live = false),
            val restoredItems: Int? = null,
            /** Kept for the result screen's shape; sources carry no passwords any more. */
            val passwordsOmitted: Boolean = false,
            /** Kept for the result screen's shape; nothing waits for a password any more. */
            val needPassword: List<String> = emptyList(),
            val skippedSources: Int = 0,
            val invalidLocale: Boolean = false,
            /** The source as it stands after the sync — the host uses it to offer a guide sync. */
            val source: SourceEntity? = null,
        ) : ImportState
        data class Failed(val failure: SetupFailure) : ImportState
        /** Encrypted backup needs the backup password before restoring; [retry] after a wrong attempt.
         *  [sealed] marks a whole-file-encrypted `.own`, where the password is mandatory — there is
         *  nothing to restore without it, so the host hides "Skip". */
        data class NeedPassword(val file: File, val retry: Boolean = false, val sealed: Boolean = false) : ImportState
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _progress = MutableStateFlow<ImportStage?>(null)
    val progress: StateFlow<ImportStage?> = _progress.asStateFlow()

    /**
     * Set when the user left the wizard with "Run in background". A late failure must not silently
     * DELETE the source they think they added — keep it, and wipe only the partial content.
     */
    var backgroundHandoff = false

    /**
     * RuTube: attach the single built-in source and sync it. No credentials, no address —
     * the public API is the whole provider. The canvas drives the same [ImportState]
     * (Running → Success), so the wizard needs no special screen.
     */
    suspend fun ensureRutube() {
        runImport(requiresNetwork = true) {
            sourceRepository.ensureRutube()
        }
    }

    private suspend fun runImport(
        requiresNetwork: Boolean = true,
        addSource: suspend () -> SourceEntity,
    ) {
        _state.value = ImportState.Running
        _progress.value = null
        var source: SourceEntity? = null
        try {
            if (requiresNetwork && !connectivity.isOnlineNow()) {
                _state.value = ImportState.Failed(SetupFailure.Sync(classifySyncFailure(null, online = false)))
                return
            }
            source = addSource()
            val freshSync = source.lastSyncAt == null
            when (val result = sourceRepository.sync(source, onProgress = { _progress.value = it })) {
                is SyncResult.Success -> {
                    // Just the catalogue — the guide is a separate prompt (Settings → EPG).
                    val counts = importFinalizer.finalize(source, deferIndexes = freshSync)
                    val syncedSource = sourceDao.getById(source.id) ?: source
                    if (freshSync) catalogSyncScheduler.enqueueContentIndexBuild(reason = "fresh_add")
                    _state.value = ImportState.Success(
                        counts = counts,
                        warnings = result.warnings,
                        source = syncedSource,
                    )
                }
                is SyncResult.Failed -> {
                    cleanupFailedAdd(source)
                    _state.value = ImportState.Failed(SetupFailure.Sync(classifySyncFailure(result.message, connectivity.isOnlineNow())))
                }
                SyncResult.Cancelled -> {
                    cleanupFailedAdd(source)
                    _state.value = ImportState.Idle
                }
            }
        } catch (c: CancellationException) {
            cleanupFailedAdd(source)
            _state.value = ImportState.Idle
            _progress.value = null
            throw c
        } catch (e: Exception) {
            cleanupFailedAdd(source)
            _state.value = ImportState.Failed(SetupFailure.Sync(classifySyncFailure(e.message, connectivity.isOnlineNow())))
        }
    }

    /**
     * The playlist already on this device, for a "use an existing playlist" step: the source is
     * global, so there is nothing to link — the step only re-syncs it.
     */
    suspend fun availableExistingSources(): List<SourceEntity> = sourceDao.getAllOnce()

    /**
     * Re-sync the existing source so its catalog is fresh — exactly like adding a brand-new one.
     * Drives the same [state] and [progress] as an add, so the host shows one import screen.
     */
    suspend fun linkExisting(sourceIds: Set<Long>) {
        _state.value = ImportState.Running
        _progress.value = null
        try {
            val sources = sourceDao.getAllOnce().filter { it.id in sourceIds }
            var total = SyncCounts(0, 0)
            var failure: String? = null
            val warnings = mutableListOf<SyncWarning>()
            for (source in sources) {
                when (val result = sourceRepository.sync(source, onProgress = { _progress.value = it })) {
                    is SyncResult.Success -> {
                        val c = importFinalizer.finalize(source)
                        total = SyncCounts(total.channels + c.channels, total.epg + c.epg)
                        warnings += result.warnings
                    }
                    is SyncResult.Failed -> failure = result.message
                    SyncResult.Cancelled -> {}
                }
            }
            _state.value = failure?.let { ImportState.Failed(SetupFailure.Sync(classifySyncFailure(it, connectivity.isOnlineNow()))) }
                ?: ImportState.Success(counts = total, warnings = warnings)
        } catch (c: CancellationException) {
            _state.value = ImportState.Idle
            _progress.value = null
            throw c
        } catch (e: Exception) {
            _state.value = ImportState.Failed(SetupFailure.Sync(classifySyncFailure(e.message, connectivity.isOnlineNow())))
        }
    }

    /**
     * Restore from a backup file. Encrypted backups first ask for the backup password via
     * [ImportState.NeedPassword]; returns true only when data was actually restored.
     *
     * [sections] is what the user chose to bring back, and defaults to all of it. The caller carries
     * the choice across [ImportState.NeedPassword] and hands it back to [restoreWithPassword],
     * because a sealed file is chosen from before it is opened.
     */
    suspend fun importBackup(
        file: File,
        sections: Set<BackupManager.Section> = BackupManager.Section.entries.toSet(),
        /**
         * Vestigial: the settings block (with the hardware settings of another device) left the
         * backup payload with the sync matrix — user data is all a restore carries now. The
         * parameter stays so the wizard's call shape holds.
         */
        deviceSettings: Boolean = false,
    ): Boolean {
        _state.value = ImportState.Running
        // A sealed .own reveals nothing before it is decrypted — ask for the password first.
        if (backup.isSealed(file)) {
            _state.value = ImportState.NeedPassword(file, sealed = true)
            return false
        }
        val inspection = backup.sectionsIn(file).getOrElse {
            _state.value = ImportState.Failed(SetupFailure.BackupRead)
            return false
        }
        if (inspection.encrypted) {
            _state.value = ImportState.NeedPassword(file)
            return false
        }
        return doRestore(file, null, sections, deviceSettings)
    }

    /** Continue an encrypted restore once the user provides (or skips, password = null) the passphrase. */
    suspend fun restoreWithPassword(
        file: File,
        password: String?,
        sections: Set<BackupManager.Section> = BackupManager.Section.entries.toSet(),
        deviceSettings: Boolean = false,
    ): Boolean {
        _state.value = ImportState.Running
        return doRestore(file, password, sections, deviceSettings)
    }

    private suspend fun doRestore(
        file: File,
        password: String?,
        sections: Set<BackupManager.Section>,
        deviceSettings: Boolean,
    ): Boolean =
        backup.import(
            file, sections, backupPassword = password,
        ).fold(
            onSuccess = { summary ->
                // A restore brings back favorites/history, not the catalogue, and favourites and
                // history only show once a catalogue is there to relink to. So the source is synced
                // now, as an ordinary background sync — instead of an app that opens empty until
                // someone thinks to re-sync. A source that already holds content is left alone.
                syncRestoredSources()
                _state.value = ImportState.Success(
                    restoredItems = summary.items,
                    passwordsOmitted = password.isNullOrBlank(),
                    skippedSources = summary.skippedSources,
                    invalidLocale = summary.invalidLocale,
                )
                true
            },
            onFailure = {
                if (it is BackupManager.WrongPasswordException) {
                    _state.value = ImportState.NeedPassword(file, retry = true, sealed = backup.isSealed(file))
                } else {
                    _state.value = ImportState.Failed(SetupFailure.Restore)
                }
                false
            },
        )

    private suspend fun syncRestoredSources() {
        sourceDao.getAllOnce().forEach { source ->
            val counts = importFinalizer.contentCounts(source.id)
            if (counts.channels > 0) return@forEach
            catalogSyncScheduler.enqueueSync(
                source.id,
                reason = "restore",
                contentTypes = tv.own.owntv.core.sync.SyncContentTypes.enabledOf(source),
            )
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
        _progress.value = null
    }

    /**
     * Completes onboarding: everything a first run adds is already synced. The app has no profile
     * step and no default-playlist choice any more — the shell routes itself by "setup done + a
     * source exists".
     */
    suspend fun finish() = Unit

    private suspend fun cleanupFailedAdd(source: SourceEntity?) {
        if (source == null) return
        withContext(NonCancellable) {
            catalogSyncScheduler.cancelSync(source.id)
            // The built-in RuTube source is the app's only provider and is recreated on demand;
            // never delete it on a transient first-sync failure (a network blip would otherwise
            // leave the app with no way back except another full setup). Wipe only the partial
            // content (a never-synced source re-syncs via insertFresh, which assumes empty tables —
            // leftovers would duplicate).
            runCatching { sourceRepository.clearSourceContent(source.id) }
        }
    }
}
