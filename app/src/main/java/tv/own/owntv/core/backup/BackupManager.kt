package tv.own.owntv.core.backup

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.FOLLOW_GLOBAL_LATENCY_SECS
import tv.own.owntv.core.database.entity.FOLLOW_GLOBAL_PREROLL
import tv.own.owntv.core.model.HlsSupport
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.transaction
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.stalker.StalkerClient
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Backup & restore of the painful-to-re-enter setup: **sources** (URLs + credentials + per-source UA),
 * **customizations** (hidden/renamed/reordered categories & channels), favorites, watch history,
 * resume positions and manual Move positions — as a JSON file. The user picks which [Section]s to
 * include on export and which to apply on restore. Content (channels) is NOT backed up — it's large
 * and re-syncs from the sources after restore. Source ids are preserved on restore, so
 * customization keys stay valid.
 *
 * The app has no profiles any more (live-only redesign): the file no longer carries profile rows,
 * profile-source links, profile pictures, per-profile settings maps or per-profile PIN hashes. Old
 * files that still hold those blocks import as well as they can — the profile-shaped parts are
 * silently ignored, the sources and user data are applied to the single user.
 */
class BackupManager(
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val userData: UserDataResolver,
    private val epgSources: tv.own.owntv.core.epg.EpgSourceStore,
    private val db: tv.own.owntv.core.database.OwnTVDatabase,
    /** `filesDir/backgrounds` — where the background picture lives, so a backup can carry it. */
    private val backgroundsDir: File,
) {
    /** What a backup can contain; the user multi-selects these for export and restore. */
    enum class Section {
        SOURCES,
        CUSTOMIZE,
        FAVORITES,
        HISTORY,
        MANUAL_REORDER,
        SETTINGS,
    }

    /**
     * Writes the chosen [sections] into [folder] as `owntv-backup.own`; returns the file path.
     *
     * The output is a [BackupContainer]: the same backup JSON this class has always produced, plus
     * the background picture's actual bytes when one is set. Exporting bare `.json` is gone (the
     * path in it was device-local and its contents were readable to anyone) — restore still accepts
     * old `.json` files, and always will.
     *
     * Secret fields (source passwords, proxy password) are NEVER written as plaintext. When
     * [backupPassword] is a non-blank passphrase, they are encrypted field-by-field (AES-GCM), a
     * root `crypto` block records the KDF params, **and the whole container is sealed with the same
     * passphrase** so nothing inside — URLs, usernames, history, the file list — is readable without
     * it. When it is null/blank, secrets are simply omitted and the container is a plain ZIP; the
     * caller is expected to have warned the user that passwords must be re-entered after restore.
     */
    suspend fun export(
        folder: File,
        sections: Set<Section> = Section.entries.toSet(),
        backupPassword: String? = null,
        /**
         * False for a Local sync transfer: that file is a sealed hand-over in the cache, deleted after the
         * session, not a backup the user can restore from — counting it hid "Never backed up".
         */
        recordAsBackup: Boolean = true,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceIds = sourceDao.getAllOnce().map { it.id }.toSet()
            // Set up field encryption only if a passphrase was provided.
            val pass = backupPassword?.takeIf { it.isNotBlank() }
            val salt = if (pass != null) BackupCrypto.newSalt() else null
            val key = if (pass != null && salt != null) BackupCrypto.deriveKey(pass.toCharArray(), salt, BackupCrypto.ITERATIONS) else null
            val seal: ((String) -> JSONObject)? = key?.let { k -> { plain -> BackupCrypto.encrypt(k, plain) } }

            val root = JSONObject().apply {
                // Format v26 — the last version ever WRITTEN. The profile-era blocks of older
                // files ("profiles", "links", "activeProfileId", avatar entries, "startupModes",
                // "startupChannels", "preferredLanguages", "sportsConfigs", "lastCategories",
                // "settingsPins", "customizePins", "settingsLockMenus", "homeConfigs",
                // "hideNewCategories", playbackPrefs "p", userData/tombstone "p") are no longer
                // written and are silently ignored on import. The version number is deliberately
                // not raised: there are zero installs to negotiate with during the redesign.
                put("version", 26)
                put("sections", JSONArray().apply { sections.forEach { put(it.name) } })
                if (salt != null) put("crypto", BackupCrypto.cryptoBlock(salt))
                if (Section.SOURCES in sections) {
                    put("sources", JSONArray().apply { sourceDao.getAllOnce().forEach { put(sourceJson(it, seal)) } })
                    put("epgSources", epgSources.exportJson()) // standalone EPG feeds ride with sources
                    // Per-source auto-refresh selections + default source, keyed by the preserved ids.
                    // Scoped to the sources in this file: an id belonging to a source that is NOT in
                    // this file cannot be remapped on restore, and an unmapped numeric id silently
                    // matches whatever unrelated source happens to hold it on the target device.
                    put("playlistAutoRefresh", filterBySourceId(settings.exportPlaylistAutoRefresh(), sourceIds))
                    put("epgAutoRefresh", settings.exportEpgAutoRefresh())
                    put("epgUseLogos", settings.exportEpgUseLogos())
                    settings.currentDefaultSourceId()?.takeIf { it in sourceIds }?.let { put("defaultSourceId", it) }
                }
                if (Section.CUSTOMIZE in sections) {
                    put(
                        "customizations",
                        JSONObject().apply {
                            customize.exportAll().forEach { (k, v) -> put(k, v) }
                        },
                    )
                }
                // Favorites / history / resume positions, exported with stable keys (see UserDataResolver).
                val kinds = kindsFor(sections)
                if (kinds.isNotEmpty()) {
                    put("userData", userData.exportAll(kinds))
                    // The deletions that go with them (v21). A file carrying only the surviving rows
                    // is indistinguishable from one written before the user removed anything, so a
                    // merge — which is what both restore and local sync are — silently reinstates
                    // every favorite and history entry they have ever deleted.
                    userData.exportTombstones(kinds.intersect(UserDataResolver.TOMBSTONE_KINDS))
                        .takeIf { it.length() > 0 }?.let { put("tombstones", it) }
                }
                if (Section.SETTINGS in sections) {
                    val s = settings.exportSettings() // non-secret keys, incl. proxy host/port/user/enabled
                    // Proxy password rides here as an encrypted object (key not in the settings whitelist,
                    // so importSettings ignores it); omitted entirely when there is no passphrase.
                    val proxyPass = settings.currentProxyPassword()
                    if (seal != null && proxyPass.isNotEmpty()) s.put("proxy_pass_enc", seal(proxyPass))
                    put("settings", s)
                    // Which device wrote this, so a restore can hold back the hardware settings when
                    // it lands somewhere else (see DeviceIdentity).
                    put("device", settings.deviceIdentity().toJson())
                    // The per-item stores are keyed by [tv.own.owntv.core.player.contentKey] —
                    // "<sourceId>:<MEDIA_TYPE>:<remoteId>" — so the source id DOES have to be remapped
                    // on restore (an older comment claimed they were stream-URL keyed; that stopped
                    // being true when the stable key was introduced, and an unremapped id silently
                    // moved values onto the wrong source's items). Keys still in the legacy
                    // stream-URL shape carry no source id and frequently embed the account's username
                    // and password, so they ride only when there is a passphrase.
                    val urlKeysAllowed = seal != null
                    // Per-item zoom / volume / track languages (DB v32). Same key shape as every
                    // per-item store — [tv.own.owntv.core.player.contentKey] —
                    // scoped to the sources in this file for the same reason as the key rule above.
                    run {
                        val rows = runCatching { db.playbackPrefsDao().getAllOnce() }.getOrDefault(emptyList())
                        put("playbackPrefs", JSONArray().apply {
                            rows.filter { row ->
                                filterContentKeys(listOf(row.contentKey), sourceIds, urlKeysAllowed).isNotEmpty()
                            }.forEach { row ->
                                put(
                                    JSONObject().apply {
                                        put("k", row.contentKey)
                                        row.zoomMode?.let { put("z", it) }
                                        row.volumeBoost?.let { put("v", it) }
                                        row.audioLang?.let { put("al", it) }
                                        row.subtitleLang?.let { put("sl", it) }
                                    },
                                )
                            }
                        })
                        // v23: sound-only marks and audio delays, shared by every stream — scoped and
                        // key-guarded exactly like the rows above.
                        val quirks = runCatching { db.playbackQuirkDao().getAllOnce() }.getOrDefault(emptyList())
                        put("playbackQuirks", JSONArray().apply {
                            quirks.filter { q ->
                                (q.audioOnly == true || q.audioDelayMs != null) &&
                                    filterContentKeys(listOf(q.contentKey), sourceIds, urlKeysAllowed).isNotEmpty()
                            }.forEach { q ->
                                put(
                                    JSONObject().apply {
                                        put("k", q.contentKey)
                                        put("t", q.mediaType)
                                        if (q.audioOnly == true) put("a", true)
                                        q.audioDelayMs?.let { put("d", it) }
                                    },
                                )
                            }
                        })
                    }
                }
            }

            // The wallpaper's bytes, not its path. `settings.bg_image_path` still rides in the settings
            // block, but it points into THIS device's filesDir — restoring it verbatim gave the next
            // device a dangling path and a blank background. Import re-derives the path from this entry.
            // (Profile pictures are gone with the profiles; old containers that carry them simply
            // leave those bytes unpacked.)
            val wallpaper = if (Section.SETTINGS in sections) currentWallpaper() else null
            wallpaper?.let { root.put("wallpaper", it.name) }

            if (!folder.exists()) folder.mkdirs()
            val target = File(folder, BACKUP_FILENAME)
            val path = writeAtomically(
                target,
                BackupContainer.pack(
                    BackupContainer.Payload(root.toString(2), wallpaper),
                    backupPassword,
                ),
            )
            // Recorded here and nowhere else: after the atomic rename, so a throw anywhere above
            // leaves the previous date standing rather than claiming a backup that does not exist.
            if (recordAsBackup) settings.recordBackup(
                at = System.currentTimeMillis(),
                bytes = target.length(),
                encrypted = pass != null,
                // Where it went, so "is my backup on the USB stick or in app storage?" has an answer
                // without opening a file browser.
                path = target.absolutePath,
            )
            path
        }
    }

    /** The current background picture as a container asset, or null when unset/missing/oversized. */
    private suspend fun currentWallpaper(): BackupContainer.Asset? {
        val path = settings.bgImagePath.first().trim()
        if (path.isEmpty()) return null
        val file = File(path)
        // Size cap: the wallpaper is a user-picked file and a backup the user may send over the
        // companion link. A 100 MB TIFF must not silently become a 100 MB backup.
        if (!file.isFile || file.length() !in 1..MAX_WALLPAPER_BYTES) return null
        return runCatching { BackupContainer.Asset(file.name, file.readBytes()) }.getOrNull()
    }

    /**
     * Writes the wallpaper carried by a restored container into `filesDir/backgrounds` and points the
     * setting at it. Mirrors `ingestBackgroundImage`: the folder is wiped first so it never
     * accumulates, and the filename keeps a fresh timestamp so Coil's path-keyed cache and the
     * settings Flow both see a genuinely new value.
     *
     * When the file carries no wallpaper (legacy `.json`, or a backup made with no background set),
     * the restored `bg_image_path` is a path from another device: cleared unless it happens to exist.
     */
    private suspend fun applyWallpaper(asset: BackupContainer.Asset?) {
        if (asset == null) {
            val restored = settings.bgImagePath.first().trim()
            if (restored.isNotEmpty() && !File(restored).isFile) settings.setBgImagePath("")
            return
        }
        runCatching {
            if (!backgroundsDir.exists()) backgroundsDir.mkdirs()
            backgroundsDir.listFiles()?.forEach { it.delete() }
            val ext = File(asset.name).extension.ifBlank { "png" }.lowercase()
            val dest = File(backgroundsDir, "background_${System.currentTimeMillis()}.$ext")
            dest.writeBytes(asset.bytes)
            settings.setBgImagePath(dest.absolutePath)
        }.onFailure { Log.w(TAG, "Wallpaper restore failed: ${it.message}") }
    }

    /**
     * Read a backup file in any supported format ([BackupContainer.Kind]), falling back to the
     * rotated `.bak` when the primary is unreadable — the case an interrupted pre-atomic export used
     * to leave behind. A [WrongPasswordException] from a sealed container is rethrown as-is rather
     * than triggering the `.bak` fallback: the file is fine, the password isn't.
     */
    private fun readBackup(file: File, password: String?): Pair<JSONObject, BackupContainer.Payload> {
        fun read(f: File): Pair<JSONObject, BackupContainer.Payload> =
            BackupContainer.open(f, password).let { JSONObject(it.json) to it }

        val primary = runCatching { read(file) }
        primary.getOrNull()?.let { return it }
        (primary.exceptionOrNull() as? WrongPasswordException)?.let { throw it }
        // The kind only — never the message, which could quote file content.
        Log.w(TAG, "Backup unreadable: ${primary.exceptionOrNull()?.javaClass?.simpleName}")
        val bak = File(file.parentFile, "${file.name}$BAK_SUFFIX")
        if (bak.exists()) {
            runCatching { read(bak) }.getOrNull()?.let { return it }
        }
        throw primary.exceptionOrNull() ?: IllegalStateException()
    }

    /**
     * Result of inspecting a backup file: which sections it holds, whether secrets are encrypted, and
     * whether the whole file was sealed ([sealed]).
     *
     * [sealed] drives the restore UI's order. A field-encrypted backup can be inspected without the
     * password (only the secrets are opaque), so the user picks sections first and the password
     * afterwards — and may skip it. A sealed container reveals nothing at all until it is decrypted,
     * so the password comes FIRST and cannot be skipped.
     */
    data class Inspection(
        val sections: Set<Section>,
        val encrypted: Boolean,
        val sealed: Boolean = false,
        /**
         * The file was written on another device (or before backups recorded which). Its hardware
         * settings are then kept out of a restore unless the user ticks them — see [import]'s
         * `deviceSettings`. Only meaningful when [sections] has [Section.SETTINGS].
         */
        val fromOtherDevice: Boolean = true,
    )

    /** True when [file] is a container that cannot be inspected at all without the backup password. */
    suspend fun isSealed(file: File): Boolean = withContext(Dispatchers.IO) {
        BackupContainer.probe(file) == BackupContainer.Kind.ENCRYPTED_CONTAINER
    }

    /**
     * Why a file is being applied. The two are not the same instruction and must not share one.
     *
     * [MERGE] reconciles two timelines: a deletion here beats an older record from there, which is
     * what stops an unfavorited channel coming back on every sync. [RESTORE] is the user pointing at
     * a file and saying "my data is what this says" — so a deletion made *after* that file was
     * written is no longer true, and this device's markers are dropped before it is applied. The
     * file's own deletions still apply: they are part of the snapshot.
     *
     * Getting this wrong is not loud. Restoring onto a *fresh* device works either way (no markers
     * exist yet), so a merge-shaped restore looks perfect right up until someone restores onto the
     * device they just cleared, and is told "N items restored" while nothing comes back.
     */
    enum class ImportMode { RESTORE, MERGE }

    data class ImportSummary(
        val items: Int,
        val skippedSources: Int = 0,
        /** Optional locale field was present but not in the SupportedLocales catalogue. */
        val invalidLocale: Boolean = false,
    )

    /** Thrown when a backup is encrypted and the supplied passphrase is wrong (or missing where required). */
    class WrongPasswordException : Exception()

    /**
     * What a backup file contains + whether it carries encrypted secrets (older files have no
     * "sections"). [password] is required only for a sealed container — see [isSealed].
     */
    suspend fun sectionsIn(file: File, password: String? = null): Result<Inspection> = withContext(Dispatchers.IO) {
        runCatching {
            val sealed = BackupContainer.probe(file) == BackupContainer.Kind.ENCRYPTED_CONTAINER
            val (root, _) = readBackup(file, password)
            val out = mutableSetOf<Section>()
            // "sources" marks the Sources section (it always has, for every version still readable).
            if (root.has("sources")) out += Section.SOURCES
            if (root.optJSONObject("customizations")?.keys()?.hasNext() == true) out += Section.CUSTOMIZE
            if (root.optJSONObject("settings")?.keys()?.hasNext() == true) out += Section.SETTINGS
            root.optJSONArray("userData")?.let { arr ->
                for (i in 0 until arr.length()) {
                    when (arr.getJSONObject(i).optString("kind")) {
                        "fav" -> out += Section.FAVORITES
                        "his" -> out += Section.HISTORY
                        "order", "member" -> out += Section.MANUAL_REORDER
                    }
                }
            }
            if (out.isEmpty()) error("backup_invalid")
            Inspection(
                out,
                encrypted = root.has("crypto"),
                sealed = sealed,
                fromOtherDevice = !DeviceIdentity.sameDevice(DeviceIdentity.fromJson(root.optJSONObject("device")), settings.deviceIdentity()),
            )
        }
    }

    /**
     * What [import] would change, counted without changing anything.
     *
     * Local sync exists to move a household's data between two devices that both have real data on
     * them, and the one outcome that must never happen is someone tapping the wrong direction and
     * finding out afterwards. So the counts here are per section and phrased as *change*, not as
     * volume: how many rows would appear that are not here now, and how many would be removed.
     *
     * Reads only. Every lookup mirrors what [import] does — sources by type/URL/username — so the
     * numbers are the same ones the apply will produce, minus anything that lands after a sync of
     * the catalogue (a favorite whose channel this device has not downloaded yet counts as new, and
     * heals into place later exactly as a restore's does).
     */
    data class Preview(
        val newSources: Int = 0,
        val newFavorites: Int = 0,
        val newHistory: Int = 0,
        val newReorder: Int = 0,
        val changedSettings: Int = 0,
        val hasCustomizations: Boolean = false,
        /** Rows this device would LOSE, because the other device deleted them more recently. */
        val deletions: Int = 0,
        /**
         * The file comes from another device and carries its hardware settings —
         * which [import] keeps out unless `deviceSettings` is passed. The confirm step offers them.
         */
        val hasDeviceSettings: Boolean = false,
    ) {
        val isEmpty: Boolean
            get() = newSources == 0 && newFavorites == 0 && newHistory == 0 &&
                newReorder == 0 && changedSettings == 0 && !hasCustomizations && deletions == 0
    }

    suspend fun previewImport(
        file: File,
        sections: Set<Section> = Section.entries.toSet(),
        backupPassword: String? = null,
    ): Result<Preview> = withContext(Dispatchers.IO) {
        runCatching {
            val (root, _) = readBackup(file, backupPassword)
            // Secrets stay sealed: a preview never needs to read a password, so it never derives a key.
            val noSecrets: (Any?) -> String? = { v -> (v as? String)?.takeIf { it.isNotEmpty() } }

            var newSources = 0
            val sourceIdByFileId = HashMap<Long, Long>()
            if (Section.SOURCES in sections) {
                val candidates = sourceDao.getAllOnce().toMutableList()
                val fileSources = root.optJSONArray("sources") ?: JSONArray()
                for (i in 0 until fileSources.length()) {
                    val incoming = sourceFrom(fileSources.getJSONObject(i), noSecrets) ?: continue
                    val existing = matchSourceForRestore(candidates, incoming)
                    if (existing == null) {
                        newSources++
                    } else {
                        candidates.remove(existing)
                        sourceIdByFileId[incoming.id] = existing.id
                    }
                }
            } else {
                // Not restoring sources, but the user-data counts still need the id mapping to look
                // anything up — so build it without counting anything as new.
                val candidates = sourceDao.getAllOnce().toMutableList()
                val fileSources = root.optJSONArray("sources") ?: JSONArray()
                for (i in 0 until fileSources.length()) {
                    val incoming = sourceFrom(fileSources.getJSONObject(i), noSecrets) ?: continue
                    matchSourceForRestore(candidates, incoming)?.let {
                        candidates.remove(it)
                        sourceIdByFileId[incoming.id] = it.id
                    }
                }
            }

            val kinds = kindsFor(sections)
            val fileVersion = root.optInt("version", 0)
            val counts = HashMap<String, Int>()
            var deletions = 0
            if (kinds.isNotEmpty()) {
                root.optJSONArray("userData")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        val kind = e.optString("kind")
                        if (kind !in kinds) continue
                        if (isOldListOrder(e, fileVersion)) continue
                        sourceIdByFileId[e.optLong("src", -1)]?.let { e.put("src", it) }
                        if (!userData.wouldAdd(kind, e)) continue
                        counts[kind] = (counts[kind] ?: 0) + 1
                    }
                }
                root.optJSONArray("tombstones")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        if (e.optString("kind") !in kinds) continue
                        sourceIdByFileId[e.optLong("src", -1)]?.let { e.put("src", it) }
                        if (userData.wouldRemove(e)) deletions++
                    }
                }
            }

            var changedSettings = 0
            if (Section.SETTINGS in sections) {
                root.optJSONObject("settings")?.let { incoming ->
                    val mine = settings.exportSettings()
                    incoming.keys().forEach { k ->
                        if (!mine.has(k) || mine.get(k).toString() != incoming.get(k).toString()) changedSettings++
                    }
                }
            }

            Preview(
                newSources = newSources,
                newFavorites = counts["fav"] ?: 0,
                newHistory = counts["his"] ?: 0,
                newReorder = (counts["order"] ?: 0) + (counts["member"] ?: 0),
                changedSettings = changedSettings,
                hasCustomizations = Section.CUSTOMIZE in sections &&
                    root.optJSONObject("customizations")?.keys()?.hasNext() == true,
                deletions = deletions,
                hasDeviceSettings = Section.SETTINGS in sections &&
                    !DeviceIdentity.sameDevice(DeviceIdentity.fromJson(root.optJSONObject("device")), settings.deviceIdentity()) &&
                    root.optJSONObject("settings")?.let(settings::carriesDeviceSettings) == true,
            )
        }
    }

    /**
     * Applies the chosen [sections] of the file (only those it actually contains) as a MERGE — a
     * restore never deletes anything that already exists on the device:
     *
     * - Sources are matched by type+URL+username (plus the MAC for Stalker, whose playlists share a
     *   portal URL and have no username): matches keep the device row (secrets updated when the file
     *   carries them); unknown sources are added, and each device row is claimed at most once.
     *   Nothing is wiped.
     * - Every id in the file (source/EPG-source) is remapped to its device id before any
     *   per-source data (favorites/history, customizations, auto-refresh) is applied.
     *
     * For encrypted backups: if [backupPassword] is provided it is validated BEFORE any write — a
     * wrong passphrase fails fast with [WrongPasswordException] and changes nothing. If the
     * passphrase is null/blank on an encrypted backup, non-secret data still restores and secret
     * fields are left blank (the caller tells the user to re-enter passwords). Legacy v5 backups with
     * plaintext passwords import exactly as before (no `crypto` block ⇒ strings treated as plaintext).
     */
    suspend fun import(
        file: File,
        sections: Set<Section> = Section.entries.toSet(),
        backupPassword: String? = null,
        mode: ImportMode = ImportMode.RESTORE,
        /**
         * The user asked for the hardware settings of *another* device (decoders, frame rate,
         * HDR, surround). A restore of this device's own
         * backup applies them anyway; a file from elsewhere — a restore or a local sync alike — keeps
         * this device's unless this is true. Local sync follows the same rule.
         */
        deviceSettings: Boolean = false,
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val (root, payload) = readBackup(file, backupPassword)
            val wallpaper = payload.wallpaper
            // Schema version of the FILE (not of this build). Only needed where a block moved between
            // sections: up to v16 some blocks rode with SOURCES, from v17 with SETTINGS. Readers
            // accept both places, so either file restores whichever section the user ticks.
            val fileVersion = root.optInt("version", 0)
            val crypto = root.optJSONObject("crypto")
            val pass = backupPassword?.takeIf { it.isNotBlank() }

            // Derive + validate the key up front, before any destructive write.
            val key = if (crypto != null && pass != null) {
                BackupCrypto.deriveKey(pass, crypto) ?: throw WrongPasswordException()
            } else null
            if (key != null && !validatePassphrase(root, key)) throw WrongPasswordException()
            // unseal: decrypt an encrypted secret object; null key (skip) or legacy plaintext returns as-is.
            val unseal: (Any?) -> String? = { v ->
                when {
                    BackupCrypto.isEncrypted(v) -> if (key != null) runCatching { BackupCrypto.decrypt(key, v as JSONObject) }.getOrNull() else null
                    v is String -> v.takeIf { it.isNotEmpty() }
                    else -> null
                }
            }

            var count = 0
            // Sources whose "type" this build can't parse: skipped rather than coerced, and
            // reported back so the restore message can say what was left out.
            var skippedSources = 0
            // Locale is deliberately deferred until every other restore operation and the marker
            // clear have completed. Publishing it earlier can recreate the Activity while the
            // database/DataStore merge is still in flight.
            var pendingLocaleTag: String? = null
            var localeFieldPresent = false
            var invalidLocale = false

            // Id remapping (file id → device id). Filled during the source merge below. When
            // the SOURCES section isn't being restored, matching still runs read-only so the other
            // sections can attach to the right device rows; unmatched ids fall through unchanged.
            val sourceIdMap = HashMap<Long, Long>()
            var epgIdMap: Map<Long, Long> = emptyMap()

            val fileSources = root.optJSONArray("sources") ?: JSONArray()
            val applySources = Section.SOURCES in sections && root.has("sources")

            // Interrupted-restore marker: set before the first write of any kind, cleared only
            // after the last one. A restore spans the database AND several DataStore files, so no
            // single transaction can cover it; the marker is what makes a half-applied restore
            // visible instead of silent (the shell prompts about it at the next launch).
            settings.markRestoreStarted("${file.name} · ${sections.joinToString(", ") { it.name }}")
            Log.i(
                TAG,
                "Restore start file=${file.name} sections=${sections.joinToString(",") { it.name }} " +
                    "encrypted=${crypto != null} key=${key != null} sources=${fileSources.length()}",
            )

            // MERGE-restore: a restore never deletes existing sources.
            // One transaction wraps the source row writes — the part where a partial
            // apply would be actively wrong. The DataStore-backed sections below (settings,
            // customizations) can't join it, and neither can the user-data resolve, which is
            // deliberately chunked so a large restore doesn't hold one write transaction for its
            // whole duration.
            db.transaction {
                // --- sources: match by type+URL+username, plus the MAC for Stalker. Match → keep the
                // device row (refresh the secrets/extras the file carries); unknown → insert.
                // Nothing is wiped.
                val deviceSources = sourceDao.getAllOnce()
                // Claim-once: a device row leaves this list as soon as an incoming source merges onto
                // it, so two playlists in the file can never land on the same row.
                val unclaimedSources = deviceSources.toMutableList()
                val takenSourceIds = deviceSources.map { it.id }.toMutableSet()
                for (i in 0 until fileSources.length()) {
                    val srcJson = fileSources.getJSONObject(i)
                    val incoming = sourceFrom(srcJson, unseal)
                    if (incoming == null) {
                        skippedSources++
                        continue
                    }
                    val existing = matchSourceForRestore(unclaimedSources, incoming)
                        ?.also { unclaimedSources.remove(it) }
                    when {
                        existing != null -> {
                            if (applySources) {
                                // Non-secret playlist settings are applied from the FILE when it carries
                                // them. They used to be skipped, so restoring onto an install that
                                // already had the playlist kept the device's values and silently lost
                                // the backup's name, per-section sync scope, Prefer HLS and per-playlist
                                // Pre-buffer. `has()` rather than a value check, so a backup written by
                                // an older build (no such key) still leaves the device's value alone.
                                sourceDao.update(
                                    existing.copy(
                                        name = if (srcJson.has("name")) incoming.name else existing.name,
                                        // A Stalker login is not part of its match key (see sourceMatchKey).
                                        username = incoming.username ?: existing.username,
                                        password = incoming.password ?: existing.password,
                                        mac = incoming.mac ?: existing.mac,
                                        stalkerSerialNumber = incoming.stalkerSerialNumber ?: existing.stalkerSerialNumber,
                                        stalkerDeviceId = incoming.stalkerDeviceId ?: existing.stalkerDeviceId,
                                        stalkerDeviceId2 = incoming.stalkerDeviceId2 ?: existing.stalkerDeviceId2,
                                        stalkerSignature = incoming.stalkerSignature ?: existing.stalkerSignature,
                                        userAgent = incoming.userAgent ?: existing.userAgent,
                                        epgUrl = incoming.epgUrl ?: existing.epgUrl,
                                        syncLive = if (srcJson.has("syncLive")) incoming.syncLive else existing.syncLive,
                                        importPortalEpg = if (srcJson.has("importPortalEpg")) incoming.importPortalEpg else existing.importPortalEpg,
                                        syncMovies = if (srcJson.has("syncMovies")) incoming.syncMovies else existing.syncMovies,
                                        syncSeries = if (srcJson.has("syncSeries")) incoming.syncSeries else existing.syncSeries,
                                        preferHls = if (srcJson.has("preferHls")) incoming.preferHls else existing.preferHls,
                                        livePrerollSecs = if (srcJson.has("livePrerollSecs")) incoming.livePrerollSecs else existing.livePrerollSecs,
                                        liveEnginePreference = if (srcJson.has("liveEnginePreference")) incoming.liveEnginePreference else existing.liveEnginePreference,
                                        liveLatencyMode = if (srcJson.has("liveLatencyMode")) incoming.liveLatencyMode else existing.liveLatencyMode,
                                        // v44 per-playlist overrides: the file wins only where it says anything.
                                        catchupTimezone = if (srcJson.has("catchupTimezone")) incoming.catchupTimezone else existing.catchupTimezone,
                                        catchupOffsetMin = if (srcJson.has("catchupOffsetMin")) incoming.catchupOffsetMin else existing.catchupOffsetMin,
                                        liveTuneTimeoutSecs = if (srcJson.has("liveTuneTimeoutSecs")) incoming.liveTuneTimeoutSecs else existing.liveTuneTimeoutSecs,
                                        httpReferer = if (srcJson.has("httpReferer")) incoming.httpReferer else existing.httpReferer,
                                        cleanChannelNames = if (srcJson.has("cleanChannelNames")) incoming.cleanChannelNames else existing.cleanChannelNames,
                                        channelNameStrip = if (srcJson.has("channelNameStrip")) incoming.channelNameStrip else existing.channelNameStrip,
                                        ignoreConnectionLimit = if (srcJson.has("ignoreConnectionLimit")) incoming.ignoreConnectionLimit else existing.ignoreConnectionLimit,
                                        liveLatencyCustomSecs = if (srcJson.has("liveLatencyCustomSecs")) incoming.liveLatencyCustomSecs else existing.liveLatencyCustomSecs,
                                        // A measurement this device already took beats the file's: it
                                        // was taken on this network, against this provider, now.
                                        maxConnections = if (existing.maxConnections > 0) existing.maxConnections else incoming.maxConnections,
                                        maxConnectionsProbedAt = maxOf(existing.maxConnectionsProbedAt, incoming.maxConnectionsProbedAt),
                                        // hlsSupported is a sync-time detection hint, not a user choice:
                                        // the device's own last answer wins, and UNKNOWN adopts the
                                        // file's so a fresh row is not left blank until the next sync.
                                        hlsSupported = if (existing.hlsSupported == HlsSupport.UNKNOWN) incoming.hlsSupported else existing.hlsSupported,
                                    ),
                                )
                            }
                            sourceIdMap[incoming.id] = existing.id
                        }
                        applySources -> {
                            val keepId = incoming.id > 0 && incoming.id !in takenSourceIds
                            val toInsert = if (keepId) incoming else incoming.copy(id = 0)
                            // A restored backup starts with empty catalog tables. Nullifying lastSyncAt ensures
                            // the first sync is treated as a fresh sync, preserving the restored category settings.
                            val rowId = sourceDao.insert(toInsert.copy(lastSyncAt = null))
                            val deviceId = if (keepId) incoming.id else rowId
                            takenSourceIds += deviceId
                            sourceIdMap[incoming.id] = deviceId
                        }
                    }
                }
            }

            if (applySources) {
                // EPG sources merge by URL; the returned map remaps the auto-refresh keys below.
                epgIdMap = epgSources.mergeJson(root.optString("epgSources").takeIf { it.isNotBlank() })

                // Auto-refresh maps + default source, remapped to device ids; entries whose ids didn't
                // survive the merge are dropped; the device's other choices are kept (merge).
                val sourceIds = sourceDao.getAllOnce().map { it.id }.toSet()
                root.optJSONObject("playlistAutoRefresh")?.let { settings.importPlaylistAutoRefresh(remapKeys(it, sourceIdMap), sourceIds) }
                root.optJSONObject("epgAutoRefresh")?.let { settings.importEpgAutoRefresh(remapKeys(it, epgIdMap), epgSources.getAll().map { s -> s.id }.toSet()) }
                root.optJSONObject("epgUseLogos")?.let { settings.importEpgUseLogos(remapKeys(it, epgIdMap), epgSources.getAll().map { s -> s.id }.toSet()) }
                if (root.has("defaultSourceId")) {
                    // Only a source that actually took part in this restore may become the default.
                    // Falling back to the file's raw id was an id-collision bug: an unmapped id still
                    // passed the `in sourceIds` check whenever some unrelated device source happened
                    // to hold that number, silently repointing the user's default playlist.
                    sourceIdMap[root.getLong("defaultSourceId")]?.let { settings.importDefaultSource(it, sourceIds) }
                }
                count += fileSources.length() - skippedSources
            }

            if (Section.CUSTOMIZE in sections) {
                root.optJSONObject("customizations")?.let { o ->
                    // Remap the "<sourceId>:…" content keys inside each value, then MERGE.
                    // The keys themselves are customization namespaces, not per-profile any more
                    // (the profile-shaped keys of pre-redesign files pass through; they simply stop
                    // matching what this build reads and writes).
                    val cust = HashMap<String, String>()
                    o.keys().forEach { k ->
                        if (!k.startsWith("cust_")) return@forEach
                        cust[k] = remapCustomizationValue(o.getString(k), sourceIdMap)
                    }
                    customize.mergeAll(cust)
                    count += cust.size
                }
            }

            // Favorites/history/progress: stashed as pending records — they attach automatically as
            // the post-restore syncs repopulate the content tables (UserDataResolver.resolvePending).
            // Each record's source ("src") id is remapped to the merged device id.
            val kinds = kindsFor(sections)
            if (kinds.isNotEmpty()) {
                // Both the surviving rows and the deletions are records of the same shape and need the
                // same treatment, so they share one pass.
                fun remapUserData(arr: JSONArray): JSONArray {
                    val filtered = JSONArray()
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        if (e.optString("kind") !in kinds) continue
                        val src = e.optLong("src", -1)
                        sourceIdMap[src]?.let { e.put("src", it) }
                        // Manual-order context keys for provider folders use the same
                        // "<sourceId>:<stable-category-id>" namespace as customization keys.
                        // Custom-category ids ("custom:<uuid>") naturally pass through.
                        if (e.has("ctx")) {
                            e.put("ctx", remapContentContextKey(e.optString("ctx"), sourceIdMap))
                        }
                        filtered.put(e)
                    }
                    return filtered
                }
                // A restore drops this device's own deletion markers BEFORE anything is applied —
                // otherwise they refuse the very rows being restored
                // (UserDataResolver.Resolution.REFUSED) and the next local sync would delete them
                // again. A merge must never do this — see [ImportMode].
                if (mode == ImportMode.RESTORE) userData.clearDeletions()
                // Deletions FIRST (v21): they remove any local row older than the deletion, and they
                // are recorded here, so the very next step cannot re-insert what this one removed.
                root.optJSONArray("tombstones")?.let { userData.applyTombstones(remapUserData(it)) }
                root.optJSONArray("userData")?.let { arr ->
                    val filtered = remapUserData(arr).withoutOldListOrders(fileVersion)
                    // Refused records were dropped for good, not queued — counting them is what let a
                    // restore report "N items restored" having reinstated none of them.
                    val refused = userData.importAll(filtered)
                    count += (filtered.length() - refused).coerceAtLeast(0)
                }
            }

            if (Section.SETTINGS in sections) {
                // Decision 9: another device's hardware settings — the global ones below —
                // only come along when the user ticked them.
                val fromHere = DeviceIdentity.sameDevice(DeviceIdentity.fromJson(root.optJSONObject("device")), settings.deviceIdentity())
                val keepDeviceSettings = !fromHere && !deviceSettings
                root.optJSONObject("settings")?.let { s ->
                    // non-secret keys (incl. proxy host/port/user/enabled)
                    val importedSettings = settings.importSettings(s, keepDeviceSettings)
                    if (importedSettings.localePresent) {
                        localeFieldPresent = true
                        pendingLocaleTag = importedSettings.localeTag
                        invalidLocale = importedSettings.invalidLocale
                    }
                    // Proxy password: decrypt if we have a key; if encrypted but no key, leave blank.
                    if (s.has("proxy_pass_enc")) {
                        unseal(s.opt("proxy_pass_enc"))?.let { settings.setProxyPassword(it) }
                    }
                    count += s.length()
                }
                // After importSettings, because that is what wrote the file's (device-local) bg path.
                applyWallpaper(wallpaper)
                // Older files carry a "compatMode" block of per-item engine pins; the engine it pinned
                // to is gone, so the block is read by nobody now — an old file simply loses those pins.
                // Per-item zoom / volume / audio delay. Merged in (REPLACE on the same key), so a restore
                // never drops what this device already remembers for other items.
                root.optJSONArray("playbackPrefs")?.let { arr ->
                    val rows = ArrayList<tv.own.owntv.core.database.entity.PlaybackPrefsEntity>()
                    for (i in 0 until arr.length()) {
                        val e = arr.optJSONObject(i) ?: continue
                        // Same [tv.own.owntv.core.player.contentKey] shape as every per-item store —
                        // remap the source id, or the
                        // restored zoom/volume lands on the wrong item (or on nothing at all).
                        val key2 = e.optString("k").takeIf { it.isNotBlank() }
                            ?.let { remapContentKey(it, sourceIdMap) } ?: continue
                        val zoom = e.optString("z").takeIf { it.isNotBlank() }
                        val volume = if (e.has("v")) e.optInt("v").coerceIn(0, 150) else null
                        val audioLang = e.optString("al").takeIf { it.isNotBlank() }
                        // "" is a real answer — subtitles off — so only an absent field means "nothing".
                        val subtitleLang = if (e.has("sl")) e.optString("sl") else null
                        // v20–v22 files carry the delay per profile; since v44 it is the stream's, shared.
                        if (e.has("d")) {
                            val delay = e.optInt("d").coerceIn(-5_000, 5_000)
                            runCatching {
                                db.playbackQuirkDao().setAudioDelay(key2, tv.own.owntv.core.player.sourceIdOfContentKey(key2), tv.own.owntv.core.player.mediaTypeOfContentKey(key2) ?: "LIVE", delay)
                            }
                        }
                        if (zoom == null && volume == null && audioLang == null && subtitleLang == null) continue
                        rows += tv.own.owntv.core.database.entity.PlaybackPrefsEntity(
                            contentKey = key2, zoomMode = zoom, volumeBoost = volume,
                            sourceId = tv.own.owntv.core.player.sourceIdOfContentKey(key2),
                            audioLang = audioLang, subtitleLang = subtitleLang,
                        )
                    }
                    if (rows.isNotEmpty()) runCatching { db.playbackPrefsDao().insertAll(rows) }
                }
                // v23: sound-only marks and audio delays, shared by every stream. Remapped like every
                // per-item key; a key whose playlist did not come along is dropped.
                root.optJSONArray("playbackQuirks")?.let { arr ->
                    val dao = db.playbackQuirkDao()
                    for (i in 0 until arr.length()) {
                        val e = arr.optJSONObject(i) ?: continue
                        val key2 = e.optString("k").takeIf { it.isNotBlank() }
                            ?.let { remapContentKey(it, sourceIdMap) } ?: continue
                        val sourceId = tv.own.owntv.core.player.sourceIdOfContentKey(key2)
                        val type = e.optString("t").takeIf { it.isNotBlank() } ?: tv.own.owntv.core.player.mediaTypeOfContentKey(key2) ?: "LIVE"
                        runCatching {
                            if (e.optBoolean("a", false)) dao.setAudioOnly(key2, sourceId, type, true)
                            if (e.has("d")) dao.setAudioDelay(key2, sourceId, type, e.optInt("d").coerceIn(-5_000, 5_000))
                        }
                    }
                }
            }
            settings.clearRestoreMarker()
            // Locale writes are last by design: all restore work and the interrupted-restore marker
            // are complete before LocalizedContent can observe a script-family change and recreate
            // the Activity. An invalid optional locale is ignored and reported in the summary.
            if (localeFieldPresent) {
                pendingLocaleTag?.let { tag -> settings.applyImportedLocale(tag) }
            }
            Log.i(TAG, "Restore done items=$count skippedSources=$skippedSources invalidLocale=$invalidLocale")
            ImportSummary(items = count, skippedSources = skippedSources, invalidLocale = invalidLocale)
        }
    }

    /** Confirms the derived key opens at least one encrypted secret in the file (GCM tag check). */
    private fun validatePassphrase(root: JSONObject, key: javax.crypto.SecretKey): Boolean {
        firstEncryptedSecret(root)?.let { sealed ->
            return runCatching { BackupCrypto.decrypt(key, sealed); true }.getOrDefault(false)
        }
        return true // crypto block but no actual encrypted field — nothing to validate against
    }

    /** Finds the first encrypted secret object in the file (a source password, a Stalker MAC, or the proxy/TMDB key). */
    private fun firstEncryptedSecret(root: JSONObject): JSONObject? {
        root.optJSONArray("sources")?.let { arr ->
            for (i in 0 until arr.length()) {
                val src = arr.getJSONObject(i)
                val pw = src.opt("password")
                if (BackupCrypto.isEncrypted(pw)) return pw as JSONObject
                // A Stalker source's MAC is its only secret (password is null), so probe it too —
                // otherwise an all-Stalker backup couldn't validate the passphrase.
                listOf("mac", "stalkerSerialNumber", "stalkerDeviceId", "stalkerDeviceId2", "stalkerSignature").forEach { key ->
                    val value = src.opt(key)
                    if (BackupCrypto.isEncrypted(value)) return value as JSONObject
                }
            }
        }
        root.optJSONObject("settings")?.opt("proxy_pass_enc")?.let { if (BackupCrypto.isEncrypted(it)) return it as JSONObject }
        return null
    }

    private fun kindsFor(sections: Set<Section>): Set<String> = buildSet {
        if (Section.FAVORITES in sections) add("fav")
        if (Section.HISTORY in sections) add("his")
        // "member" (custom category membership) rides with MANUAL_REORDER: it is a
        // per-item ordering preference like the reorder positions, so one tick covers it.
        if (Section.MANUAL_REORDER in sections) { add("order"); add("member") }
    }

    // --- mapping ---
    private fun sourceJson(s: SourceEntity, seal: ((String) -> JSONObject)?) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("type", s.type.name); put("url", s.url)
        put("username", s.username ?: JSONObject.NULL)
        // Password: encrypted object when a passphrase was given, otherwise omitted (never plaintext).
        val pw = s.password?.takeIf { it.isNotEmpty() }
        put("password", if (pw != null && seal != null) seal(pw) else JSONObject.NULL)
        // Stalker MAC: same secret policy as the password — encrypted with a passphrase, else omitted.
        val macVal = s.mac?.takeIf { it.isNotEmpty() }
        put("mac", if (macVal != null && seal != null) seal(macVal) else JSONObject.NULL)
        val serialNumber = s.stalkerSerialNumber?.takeIf { it.isNotEmpty() }
        put("stalkerSerialNumber", if (serialNumber != null && seal != null) seal(serialNumber) else JSONObject.NULL)
        val deviceId = s.stalkerDeviceId?.takeIf { it.isNotEmpty() }
        put("stalkerDeviceId", if (deviceId != null && seal != null) seal(deviceId) else JSONObject.NULL)
        val deviceId2 = s.stalkerDeviceId2?.takeIf { it.isNotEmpty() }
        put("stalkerDeviceId2", if (deviceId2 != null && seal != null) seal(deviceId2) else JSONObject.NULL)
        val signature = s.stalkerSignature?.takeIf { it.isNotEmpty() }
        put("stalkerSignature", if (signature != null && seal != null) seal(signature) else JSONObject.NULL)
        put("userAgent", s.userAgent ?: JSONObject.NULL); put("epgUrl", s.epgUrl ?: JSONObject.NULL)
        put("syncLive", s.syncLive); put("syncMovies", s.syncMovies); put("syncSeries", s.syncSeries)
        put("importPortalEpg", s.importPortalEpg)
        // v17: the two per-playlist player settings. Both are deliberate user choices made in the
        // playlist editor and neither can be re-derived from the provider, so losing them on restore
        // silently reintroduced whatever streaming problem the user had already fixed. `hlsSupported`
        // rides too — it is only a detection hint, but carrying it spares a restored playlist the
        // "unknown until the next sync" gap.
        put("preferHls", s.preferHls); put("livePrerollSecs", s.livePrerollSecs)
        // v19: the per-playlist Live TV engine and Live latency overrides. Same reasoning as the two
        // above — deliberate user choices in the playlist editor that cannot be re-derived.
        put("liveEnginePreference", s.liveEnginePreference ?: JSONObject.NULL)
        put("liveLatencyMode", s.liveLatencyMode ?: JSONObject.NULL)
        put("catchupTimezone", s.catchupTimezone ?: JSONObject.NULL)
        put("catchupOffsetMin", s.catchupOffsetMin ?: JSONObject.NULL)
        put("liveTuneTimeoutSecs", s.liveTuneTimeoutSecs ?: JSONObject.NULL)
        put("httpReferer", s.httpReferer ?: JSONObject.NULL)
        put("cleanChannelNames", s.cleanChannelNames)
        put("channelNameStrip", s.channelNameStrip ?: JSONObject.NULL)
        put("ignoreConnectionLimit", s.ignoreConnectionLimit)
        put("liveLatencyCustomSecs", s.liveLatencyCustomSecs)
        // v22: the measured stream limit and when it was measured.
        //
        // Carried, unlike the provider-published `maxConnections` beside it, because measuring costs
        // up to two minutes and interrupts playback, and because the answer describes the *account* —
        // it is the same on every device the user restores onto. Losing it would mean a restored
        // playlist silently going back to not knowing, and never finding out again: the automatic
        // measurement only runs on a playlist's first sync, which a restored playlist has already had.
        put("maxConnections", s.maxConnections)
        put("maxConnectionsProbedAt", s.maxConnectionsProbedAt)
        put("hlsSupported", s.hlsSupported.code)
        put("createdAt", s.createdAt); put("lastSyncAt", s.lastSyncAt ?: JSONObject.NULL)
    }

    /**
     * Maps one `sources[]` entry, or **null when its `type` isn't a [SourceType] this build knows**
     * It used to coerce an unknown type to [SourceType.M3U], which silently turned a newer
     * build's Stalker/other portal into a broken M3U row pointing at a portal URL — worse than not
     * restoring it, because the user has no way to tell it apart from a real playlist. Callers skip
     * and count these, and the restore summary says how many were left out.
     */
    private fun sourceFrom(o: JSONObject, unseal: (Any?) -> String?): SourceEntity? {
        val type = parseSourceType(o.optString("type")) ?: run {
            Log.w(TAG, "Restore: skipping source '${o.optString("name")}' — unknown type '${o.optString("type")}'")
            return null
        }
        return SourceEntity(
            id = o.getLong("id"), name = o.getString("name"),
            type = type,
            url = o.getString("url"), username = o.optStringOrNull("username"),
            password = if (o.isNull("password")) null else unseal(o.opt("password")),
            // Stalker MAC: restored from its encrypted block when a passphrase was given; null on backups
            // older than v10 (no "mac" key) or when the MAC was omitted (no passphrase).
            mac = if (o.isNull("mac")) null else unseal(o.opt("mac")),
            stalkerSerialNumber = if (o.isNull("stalkerSerialNumber")) null else unseal(o.opt("stalkerSerialNumber")),
            stalkerDeviceId = if (o.isNull("stalkerDeviceId")) null else unseal(o.opt("stalkerDeviceId")),
            stalkerDeviceId2 = if (o.isNull("stalkerDeviceId2")) null else unseal(o.opt("stalkerDeviceId2")),
            stalkerSignature = if (o.isNull("stalkerSignature")) null else unseal(o.opt("stalkerSignature")),
            userAgent = o.optStringOrNull("userAgent"), epgUrl = o.optStringOrNull("epgUrl"),
            // Pre-v13 backups omit the flags — default On so restore matches today's behaviour.
            syncLive = if (o.has("syncLive")) o.optBoolean("syncLive", true) else true,
            // Absent in every backup written before v37; a portal guide is on unless it was turned off.
            importPortalEpg = o.optBoolean("importPortalEpg", true),
            syncMovies = if (o.has("syncMovies")) o.optBoolean("syncMovies", true) else true,
            syncSeries = if (o.has("syncSeries")) o.optBoolean("syncSeries", true) else true,
            // Pre-v17 backups omit these — fall back to the entity defaults (Prefer HLS off, follow
            // the global Pre-buffer, HLS support not yet known), which is what those installs had.
            preferHls = o.optBoolean("preferHls", false),
            livePrerollSecs = o.optInt("livePrerollSecs", FOLLOW_GLOBAL_PREROLL),
            // Pre-v19 backups omit these three, so they restore as "follow the global setting" — which
            // is exactly what those installs did, since the settings did not exist there.
            liveEnginePreference = o.optStringOrNull("liveEnginePreference"),
            liveLatencyMode = o.optStringOrNull("liveLatencyMode"),
            liveLatencyCustomSecs = o.optInt("liveLatencyCustomSecs", FOLLOW_GLOBAL_LATENCY_SECS),
            // v44: absent (older backup) or null restores as "follow the global setting". Files from
            // the two-engine era also carry "vodEnginePreference"; there is no such choice now, so the
            // key is simply not read.
            catchupTimezone = o.optStringOrNull("catchupTimezone"),
            catchupOffsetMin = o.optIntOrNull("catchupOffsetMin"),
            liveTuneTimeoutSecs = o.optIntOrNull("liveTuneTimeoutSecs"),
            httpReferer = o.optStringOrNull("httpReferer"),
            cleanChannelNames = o.optBoolean("cleanChannelNames", false),
            channelNameStrip = o.optStringOrNull("channelNameStrip"),
            ignoreConnectionLimit = o.optBoolean("ignoreConnectionLimit", false),
            // Pre-v22 backups omit both, which restores as "nothing known, never measured" — exactly
            // what those installs had, and what makes a Re-test the obvious next step.
            maxConnections = o.optInt("maxConnections", 0),
            maxConnectionsProbedAt = o.optLong("maxConnectionsProbedAt", 0L),
            hlsSupported = HlsSupport.fromCode(o.optInt("hlsSupported", HlsSupport.UNKNOWN.code)),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            lastSyncAt = if (o.isNull("lastSyncAt")) null else o.optLong("lastSyncAt"),
        )
    }

    companion object {
        internal const val TAG = "BackupManager"

        /**
         * The `sources[].type` rule, kept pure so it's testable: a name this build's [SourceType]
         * knows, or null. Never a fallback — see [sourceFrom] for why coercing to M3U was worse
         * than skipping.
         */
        internal fun parseSourceType(raw: String?): SourceType? =
            raw?.takeIf { it.isNotBlank() }?.let { name -> SourceType.entries.firstOrNull { it.name == name } }

        /** What export writes since v4.2 — a [BackupContainer], not a bare JSON file. */
        const val BACKUP_FILENAME = "owntv-backup.own"

        /** The pre-4.2 name. Export never produces it any more; restore still accepts such files. */
        const val LEGACY_BACKUP_FILENAME = "owntv-backup.json"

        /** File-picker filter for restore: the new container plus every legacy `.json` in the wild. */
        val RESTORE_EXTENSIONS = setOf("own", "json")

        /**
         * Biggest wallpaper we will carry inside a backup (see `currentWallpaper`). Held below the
         * companion upload cap (`CompanionHttpServer.UPLOAD_BODY_LIMIT`, 16 MB) with room for base64's
         * ~33% inflation, so a backup that exports fine can always be sent back over the remote companion link.
         */
        private const val MAX_WALLPAPER_BYTES = 8L * 1024 * 1024

        internal const val TMP_SUFFIX = ".tmp"
        internal const val BAK_SUFFIX = ".bak"

        /**
         * Write [text] to [target] without ever leaving a truncated file behind: content goes to a
         * `.tmp` sibling, is flushed to disk (fsync) so a power cut can't leave an empty-but-renamed
         * file, the previous good backup rotates to `.bak`, and only then does the tmp take the final
         * name. A failure anywhere drops the tmp and leaves the old backup exactly as it was.
         *
         * Returns the **final** path — callers (the export UI, the companion download) rely on that.
         */
        internal fun writeAtomically(target: File, text: String): String =
            writeAtomically(target, text.toByteArray(Charsets.UTF_8))

        /** Byte-level [writeAtomically] — the container is binary, so text can't be the only entry point. */
        internal fun writeAtomically(target: File, bytes: ByteArray): String {
            val tmp = File(target.parentFile, "${target.name}$TMP_SUFFIX")
            try {
                java.io.FileOutputStream(tmp).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                    fos.fd.sync()
                }
                if (target.exists()) {
                    val bak = File(target.parentFile, "${target.name}$BAK_SUFFIX")
                    bak.delete()
                    target.renameTo(bak) // best effort: a failed rotation must not block the new write
                }
                if (!tmp.renameTo(target)) error("backup_finalize_failed")
            } catch (e: Throwable) {
                tmp.delete()
                throw e
            }
            return target.absolutePath
        }
    }
}

// --- merge-restore id remapping helpers ---
//
// Top-level and `internal` rather than private members of BackupManager: they are pure String/JSON
// functions with no dependency on the manager's DAOs, and a merge-restore that gets one of them
// wrong silently attaches a restored backup's folder customizations to the WRONG source on the
// device. That is worth unit tests, and unit tests need them reachable.

/**
 * Identity a restored source is merged onto. Username is part of the key because one portal commonly
 * serves several accounts; the password is not, so a re-exported backup with a rotated password
 * still updates the existing row instead of duplicating it.
 *
 * The MAC joins the key for **Stalker only**. Several Stalker playlists routinely share one portal
 * URL, so type+url made every Stalker playlist in a backup the same source: they all merged onto one
 * device row, each one overwriting its MAC, and every playlist's favorites/history/customizations
 * collapsed onto that single row. Other source types ignore the argument, so their keys are unchanged.
 *
 * A Stalker key leaves the username out: a portal login is a setting of the MAC's playlist, not its
 * identity, so adding or changing one must not turn the playlist into a different one on restore.
 */
internal fun sourceMatchKey(type: String, url: String, username: String?, mac: String? = null): String {
    if (type == SourceType.STALKER.name) return "$type|${url.trim()}||${normalizeMacForMatch(mac)}"
    return "$type|${url.trim()}|${username.orEmpty()}"
}

/** Compare MACs by value, not by punctuation: `aabb…` and `AA:BB:…` are the same portal login. */
private fun normalizeMacForMatch(mac: String?): String =
    mac?.takeIf { it.isNotBlank() }
        ?.let { StalkerClient.canonicalizeMac(it) ?: it.trim().uppercase() }
        .orEmpty()

/**
 * The device row [incoming] merges onto, or null to insert it as a new source. [candidates] holds
 * only rows no earlier incoming source has already claimed — a device row must never be claimed
 * twice, which is what let several Stalker playlists pile onto one row.
 *
 * Exact key first. A Stalker source then falls back to portal+username when the MAC is unknown on
 * either side: a backup written WITHOUT a passphrase omits the MAC entirely (it is a secret), and
 * such a restore has to keep merging onto the existing playlist rather than duplicating it.
 */
internal fun matchSourceForRestore(candidates: List<SourceEntity>, incoming: SourceEntity): SourceEntity? {
    val key = sourceMatchKey(incoming.type.name, incoming.url, incoming.username, incoming.mac)
    candidates.firstOrNull { sourceMatchKey(it.type.name, it.url, it.username, it.mac) == key }?.let { return it }
    if (incoming.type != SourceType.STALKER) return null
    val portalKey = sourceMatchKey(incoming.type.name, incoming.url, incoming.username)
    return candidates.firstOrNull {
        (incoming.mac.isNullOrBlank() || it.mac.isNullOrBlank()) &&
            sourceMatchKey(it.type.name, it.url, it.username) == portalKey
    }
}

/** Rewrites an id-keyed JSON map ({"<fileId>": …}) to device ids; unmapped keys pass through. */
internal fun remapKeys(o: JSONObject, idMap: Map<Long, Long>): JSONObject {
    if (idMap.isEmpty()) return o
    val out = JSONObject()
    o.keys().forEach { k ->
        val mapped = k.toLongOrNull()?.let { idMap[it] }?.toString() ?: k
        out.put(mapped, o.get(k))
    }
    return out
}

/** Remaps a provider-folder context key while preserving custom and built-in context keys. */
internal fun remapContentContextKey(contextKey: String, sourceIdMap: Map<Long, Long>): String {
    val sourceId = contextKey.substringBefore(':').toLongOrNull() ?: return contextKey
    val mapped = sourceIdMap[sourceId] ?: return contextKey
    return "$mapped:${contextKey.substringAfter(':')}"
}

/**
 * True for a manual item order from a file older than v26. Those files saved the whole list on every Move, so
 * each item would keep a fixed place and the sort chip could never sort the list again; they are dropped and
 * the list starts from the playlist's order. Favorites has no sort chip, so its order is kept.
 */
internal fun isOldListOrder(record: JSONObject, fileVersion: Int): Boolean =
    fileVersion < 26 && record.optString("kind") == "order" &&
        record.optString("ctx") != tv.own.owntv.core.database.entity.ContentOrderEntity.FAV_CONTEXT

/** [this] without the records [isOldListOrder] drops. */
internal fun JSONArray.withoutOldListOrders(fileVersion: Int): JSONArray {
    if (fileVersion >= 26) return this
    val out = JSONArray()
    for (i in 0 until length()) optJSONObject(i)?.let { if (!isOldListOrder(it, fileVersion)) out.put(it) }
    return out
}

/**
 * Rewrites the "<sourceId>:<rest>" content keys inside one SectionCustomizations JSON blob and its
 * values.
 *
 * Custom category ids ("custom:<uuid>", the `customCats` array and any custom keys in the maps)
 * contain no source id — "custom" never parses as a Long, so the inner [remapContentKey] passes them
 * through verbatim. The `customCats` array holds OBJECTS (id/name/icon) rather than strings, so the
 * array branch handles both element kinds; without this the whole blob fell back unremapped.
 */
internal fun remapCustomizationValue(raw: String, sourceIdMap: Map<Long, Long>): String {
    if (sourceIdMap.isEmpty()) return raw
    fun remapContentKey(k: String): String {
        val sid = k.substringBefore(':').toLongOrNull() ?: return k
        val mapped = sourceIdMap[sid] ?: return k
        return "$mapped:${k.substringAfter(':')}"
    }
    return runCatching {
        val o = JSONObject(raw)
        val out = JSONObject()
        o.keys().forEach { field ->
            when (val v = o.get(field)) {
                is JSONArray -> out.put(field, JSONArray().apply {
                    for (i in 0 until v.length()) {
                        val e = v.opt(i)
                        when (e) {
                            // Keyed list (hiddenCats, catOrder, lockedCats): remap content keys; custom keys pass through.
                            is String -> put(remapContentKey(e))
                            // customCats objects: ids are "custom:<uuid>" (never remapped); copy verbatim.
                            is JSONObject -> put(e)
                            else -> put(e)
                        }
                    }
                })
                is JSONObject -> {
                    if (field == "movedFrom") {
                        // movedFrom values are ORIGIN CATEGORY keys ("<sourceId>:…"), not labels —
                        // unlike every other map (hiddenItems → label, epgMatch → epg id), BOTH
                        // sides are content keys, so the values must be remapped too or the folder
                        // filter never matches after a cross-device restore.
                        out.put(field, JSONObject().apply { v.keys().forEach { k -> put(remapContentKey(k), remapContentKey(v.getString(k))) } })
                    } else {
                        out.put(field, JSONObject().apply { v.keys().forEach { k -> put(remapContentKey(k), v.get(k)) } })
                    }
                }
                else -> out.put(field, v)
            }
        }
        out.toString()
    }.getOrDefault(raw)
}

/** Keeps only the entries of a source-id-keyed JSON map whose source rides in this backup. */
internal fun filterBySourceId(map: JSONObject, sourceIds: Set<Long>): JSONObject {
    val out = JSONObject()
    map.keys().forEach { k -> if (k.toLongOrNull() in sourceIds) out.put(k, map.get(k)) }
    return out
}

/**
 * Remaps the leading source id of a per-item player key — [tv.own.owntv.core.player.contentKey]'s
 * "<sourceId>:<MEDIA_TYPE>:<remoteId>". Used for the per-item zoom/volume and quirk rows, which
 * embed the id of the source the item came from.
 *
 * A key in the older stream-URL shape has no source id to remap ("http" never parses as a Long) and
 * passes through unchanged, as does every key when no sources took part ([sourceIdMap] empty). A key
 * whose source id has no entry in a non-empty map is **dropped** (null): its playlist was not restored,
 * and the same number here may be a different playlist, which would inherit its zoom or delay.
 */
internal fun remapContentKey(key: String, sourceIdMap: Map<Long, Long>): String? {
    if (sourceIdMap.isEmpty()) return key
    val sourceId = key.substringBefore(':').toLongOrNull() ?: return key
    val mapped = sourceIdMap[sourceId] ?: return null
    return "$mapped:${key.substringAfter(':')}"
}

/**
 * Scopes per-item player keys to the sources riding in this backup.
 *
 * [allowUrlKeys] admits the legacy stream-URL keys, and is true only when a passphrase was given:
 * an IPTV stream URL routinely carries the account's username and password in its path or query, so
 * in an unencrypted backup those keys are exactly the credential leak the secret policy exists to
 * prevent. A stable key is only ever "<sourceId>:<TYPE>:<remoteId>" and leaks nothing.
 */
internal fun filterContentKeys(
    keys: Collection<String>,
    sourceIds: Set<Long>,
    allowUrlKeys: Boolean,
): List<String> = keys.filter { key ->
    val sourceId = key.substringBefore(':').toLongOrNull()
    if (sourceId == null) allowUrlKeys else sourceId in sourceIds
}

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

/** An int field that may be absent or JSON null (both mean "not set"). */
private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

/** Reads a JSON array as a list of non-blank strings, tolerating nulls/non-string entries. */
private fun jsonStrings(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    val out = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) {
        val v = arr.opt(i)
        if (v is String && v.isNotBlank()) out += v
    }
    return out
}
