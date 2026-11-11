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
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Backup & restore of the painful-to-re-enter setup: **customizations** (hidden/renamed/reordered
 * categories & channels), favorites, watch history, resume positions and manual Move positions — as a
 * JSON file. The user picks which [Section]s to include on export and which to apply on restore.
 * Content (channels) is NOT backed up — it's large and re-syncs from the provider after restore.
 *
 * Sources themselves are no longer backed up: the app has exactly one provider, the built-in RuTube
 * source, and [tv.own.owntv.core.repository.BuiltInSource.ensure] recreates that row on demand. Older
 * backup files that still carry a `sources` block are accepted — the block is simply ignored — so a
 * restore of a pre-redesign file still applies its user data, customizations and settings.
 * Content keys ("<sourceId>:…") inside user data and customizations pass through unremapped: there is
 * one source and its row is (re)created locally, so the file's ids are accepted as they are.
 *
 * The app has no profiles any more (live-only redesign): the file no longer carries profile rows,
 * profile-source links, profile pictures, per-profile settings maps or per-profile PIN hashes. Old
 * files that still hold those blocks import as well as they can — the profile-shaped parts are
 * silently ignored, the surviving sections apply to the single user.
 */
class BackupManager(
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val userData: UserDataResolver,
    private val db: tv.own.owntv.core.database.OwnTVDatabase,
    /** `filesDir/backgrounds` — where the background picture lives, so a backup can carry it. */
    private val backgroundsDir: File,
) {
    /** What a backup can contain; the user multi-selects these for export and restore. */
    enum class Section {
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
     * Secret fields (the proxy password) are NEVER written as plaintext. When [backupPassword] is a
     * non-blank passphrase, they are encrypted field-by-field (AES-GCM), a root `crypto` block records
     * the KDF params, **and the whole container is sealed with the same passphrase** so nothing inside
     * — history, the file list — is readable without it. When it is null/blank, secrets are simply
     * omitted and the container is a plain ZIP.
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
            // Per-item player rows are keyed by "<sourceId>:<MEDIA_TYPE>:<remoteId>"; the only source
            // this build can have is the built-in RuTube row, so its (device-local) id scopes them.
            val sourceIds = sourceDao.getAllOnce().map { it.id }.toSet()
            // Set up field encryption only if a passphrase was provided.
            val pass = backupPassword?.takeIf { it.isNotBlank() }
            val salt = if (pass != null) BackupCrypto.newSalt() else null
            val key = if (pass != null && salt != null) BackupCrypto.deriveKey(pass.toCharArray(), salt, BackupCrypto.ITERATIONS) else null
            val seal: ((String) -> JSONObject)? = key?.let { k -> { plain -> BackupCrypto.encrypt(k, plain) } }

            val root = JSONObject().apply {
                // Format v26 — the last version ever WRITTEN. The profile-era blocks of older
                // files ("profiles", "links", "activeProfileId", avatar entries, "startupModes",
                // "startupChannels", "preferredLanguages", "lastCategories",
                // "settingsPins", "customizePins", "settingsLockMenus", "homeConfigs",
                // "hideNewCategories", playbackPrefs "p", userData/tombstone "p") are no longer
                // written and are silently ignored on import. The "sources"/"epgSources" blocks are
                // gone the same way: the single RuTube source is recreated on demand. The version
                // number is deliberately not raised: there are zero installs to negotiate with
                // during the redesign.
                put("version", 26)
                put("sections", JSONArray().apply { sections.forEach { put(it.name) } })
                if (salt != null) put("crypto", BackupCrypto.cryptoBlock(salt))
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
                    // "<sourceId>:<MEDIA_TYPE>:<remoteId>" — so their rows are scoped to the source
                    // ids this device actually holds. Keys still in the legacy stream-URL shape carry
                    // no source id and frequently embed the account's username and password, so they
                    // ride only when there is a passphrase.
                    val urlKeysAllowed = seal != null
                    // Per-item zoom / volume / track languages (DB v32). Same key shape as every
                    // per-item store — [tv.own.owntv.core.player.contentKey].
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
        /** Kept for the restore message's shape; sources are never skipped — there are none to skip. */
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
     * Reads only. Every lookup mirrors what [import] does, so the numbers are the same ones the
     * apply will produce, minus anything that lands after a sync of the catalogue (a favorite whose
     * channel this device has not downloaded yet counts as new, and heals into place later exactly
     * as a restore's does).
     */
    data class Preview(
        /** Kept for the confirm screen's shape; nothing adds sources any more, so it is always 0. */
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
                        if (!userData.wouldAdd(kind, e)) continue
                        counts[kind] = (counts[kind] ?: 0) + 1
                    }
                }
                root.optJSONArray("tombstones")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        if (e.optString("kind") !in kinds) continue
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
     * - Customizations and per-source settings ride under keys that carry a source id. The file's
     *   ids are accepted as they are: there is exactly one provider (the built-in RuTube row, which
     *   [tv.own.owntv.core.repository.BuiltInSource.ensure] recreates on demand), so no remapping
     *   table exists any more.
     * - Favorites/history/customizations merge; nothing is wiped.
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
            // Locale is deliberately deferred until every other restore operation and the marker
            // clear have completed. Publishing it earlier can recreate the Activity while the
            // database/DataStore merge is still in flight.
            var pendingLocaleTag: String? = null
            var localeFieldPresent = false
            var invalidLocale = false

            // Id remapping (file id → device id). There is no source merge to fill it any more — the
            // one source is recreated locally and content keys are accepted as they carry — so it
            // stays empty and every remap below passes its keys through verbatim.
            val sourceIdMap = HashMap<Long, Long>()

            // Interrupted-restore marker: set before the first write of any kind, cleared only
            // after the last one. A restore spans the database AND several DataStore files, so no
            // single transaction can cover it; the marker is what makes a half-applied restore
            // visible instead of silent (the shell prompts about it at the next launch).
            settings.markRestoreStarted("${file.name} · ${sections.joinToString(", ") { it.name }}")
            Log.i(
                TAG,
                "Restore start file=${file.name} sections=${sections.joinToString(",") { it.name }} " +
                    "encrypted=${crypto != null} key=${key != null}",
            )

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
            Log.i(TAG, "Restore done items=$count invalidLocale=$invalidLocale")
            ImportSummary(items = count, invalidLocale = invalidLocale)
        }
    }

    /** Confirms the derived key opens at least one encrypted secret in the file (GCM tag check). */
    private fun validatePassphrase(root: JSONObject, key: javax.crypto.SecretKey): Boolean {
        firstEncryptedSecret(root)?.let { sealed ->
            return runCatching { BackupCrypto.decrypt(key, sealed); true }.getOrDefault(false)
        }
        return true // crypto block but no actual encrypted field — nothing to validate against
    }

    /** Finds the first encrypted secret object in the file (the proxy/TMDB key; sources are gone). */
    private fun firstEncryptedSecret(root: JSONObject): JSONObject? {
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

    companion object {
        internal const val TAG = "BackupManager"

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
// functions with no dependency on the manager's DAOs. A merge-restore that gets one of them wrong
// silently attaches a restored backup's folder customizations to the WRONG source on the device.
// With sources no longer restored the map is always empty in this build, so every remap is a
// pass-through — the helpers stay because legacy files' keys flow through them and unit tests
// pin that behavior.

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

/** Remaps a provider-folder context key while preserving custom and built-in context keys. */
internal fun remapContentContextKey(contextKey: String, sourceIdMap: Map<Long, Long>): String {
    val sourceId = contextKey.substringBefore(':').toLongOrNull() ?: return contextKey
    val mapped = sourceIdMap[sourceId] ?: return contextKey
    return "$mapped:${contextKey.substringAfter(':')}"
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
