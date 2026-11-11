package tv.own.owntv.core.backup

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import tv.own.owntv.core.sync.local.DeviceIdentity
import org.json.JSONObject
import java.io.File
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SyncMatrix

/**
 * Backup & restore of the user's data as a JSON file: favorites, watch history, manual Move
 * positions and programme reminders — the four sync-matrix categories (phase ③, see [SyncMatrix]).
 * The user picks which [Section]s to include on export and which to apply on restore. Content
 * (channels) is NOT backed up — it's large and re-syncs from the provider after restore.
 *
 * Everything else the older backups carried has left the container: the customizations (the
 * custom-categories feature still lives locally — it just stopped travelling), the settings block
 * with its proxy secrets and the wallpaper (the background picture stays a per-device choice), the
 * per-item playback prefs/quirks, and the sources block before that. A **restore of an older file is
 * tolerant**: unknown root keys ("customizations", "settings", "wallpaper", "playbackPrefs", …) are
 * simply ignored, and the surviving sections apply exactly as they did.
 *
 * The app has no profiles any more (live-only redesign): the file no longer carries profile rows,
 * profile-source links, profile pictures, per-profile settings maps or per-profile PIN hashes. Old
 * files that still hold those blocks import as well as they can — the profile-shaped parts are
 * silently ignored, the surviving sections apply to the single user.
 *
 * Sources themselves are not backed up: the app has exactly one provider, the built-in RuTube source,
 * and [tv.own.owntv.core.repository.BuiltInSource.ensure] recreates that row on demand. Content keys
 * ("<sourceId>:…") inside user data pass through unremapped: there is one source and its row is
 * (re)created locally, so the file's ids are accepted as they are.
 */
class BackupManager(
    private val settings: SettingsRepository,
    private val userData: UserDataResolver,
) {
    /** What a backup can contain; the user multi-selects these for export and restore. */
    enum class Section {
        FAVORITES,
        HISTORY,
        MANUAL_REORDER,
        REMINDERS,
    }

    /**
     * Writes the chosen [sections] into [folder] as `owntv-backup.own`; returns the file path.
     *
     * The output is a [BackupContainer]: the backup JSON sealed with [backupPassword] when one is
     * given (nothing inside — history, the file list — is readable without it), a plain ZIP
     * otherwise. The file used to also carry the wallpaper's bytes and a field-encrypted secrets
     * block; both are gone — the container-level seal is the only encryption there is.
     *
     * Exporting bare `.json` is gone (the path in it was device-local and its contents were readable
     * to anyone) — restore still accepts old `.json` files, and always will.
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
            val root = JSONObject().apply {
                // Format v26 — the last version ever WRITTEN. The profile-era and per-item blocks of
                // older files ("profiles", "links", "customizations", "settings", "playbackPrefs",
                // "playbackQuirks", "sources", …) are no longer written and are silently ignored on
                // import — only the four sync sections travel. The version number is deliberately not
                // raised: there are zero installs to negotiate with during the redesign.
                put("version", 26)
                put("sections", JSONArray().apply { sections.forEach { put(it.name) } })
                // Which device wrote this file, so an inspection can say "from another device" —
                // the per-row history attribution rides in the history records themselves (`dev`).
                put("device", settings.deviceIdentity().toJson())
                // The four sections' rows, exported with stable keys (see UserDataResolver).
                val kinds = kindsFor(sections)
                if (kinds.isNotEmpty()) {
                    put("userData", userData.exportAll(kinds))
                    // The deletions that go with them. A file carrying only the surviving rows
                    // is indistinguishable from one written before the user removed anything, so a
                    // merge — which is what both restore and local sync are — silently reinstates
                    // every favorite or reminder they have ever deleted.
                    userData.exportTombstones(kinds.intersect(UserDataResolver.TOMBSTONE_KINDS))
                        .takeIf { it.length() > 0 }?.let { put("tombstones", it) }
                }
            }

            if (!folder.exists()) folder.mkdirs()
            val target = File(folder, BACKUP_FILENAME)
            val path = writeAtomically(
                target,
                BackupContainer.pack(BackupContainer.Payload(root.toString(2), wallpaper = null), backupPassword),
            )
            // Recorded here and nowhere else: after the atomic rename, so a throw anywhere above
            // leaves the previous date standing rather than claiming a backup that does not exist.
            if (recordAsBackup) settings.recordBackup(
                at = System.currentTimeMillis(),
                bytes = target.length(),
                encrypted = backupPassword?.takeIf { it.isNotBlank() } != null,
                // Where it went, so "is my backup on the USB stick or in app storage?" has an answer
                // without opening a file browser.
                path = target.absolutePath,
            )
            path
        }
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
     * Result of inspecting a backup file: which sections it holds, whether the file carried
     * field-encrypted secrets (older files do), and whether the whole file was sealed ([sealed]).
     *
     * [sealed] drives the restore UI's order. A field-encrypted backup can be inspected without the
     * password (only the secrets are opaque), so the user picks sections first and the password
     * afterwards — and may skip it. A sealed container reveals nothing at all until it is decrypted,
     * so the password comes FIRST and cannot be skipped.
     */
    data class Inspection(
        val sections: Set<Section>,
        /** Older files field-encrypt their secrets block; the block is ignored now, but the flag
         *  keeps the restore UI's password flow working for them. */
        val encrypted: Boolean,
        val sealed: Boolean = false,
        /**
         * The file was written on another device (or carries no device block). Meaningful only for
         * old files' settings; the user data sections apply regardless.
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
        /** Kept for the restore message's shape; the settings block (with the locale) no longer travels. */
        val invalidLocale: Boolean = false,
    )

    /** Thrown when a backup is encrypted and the supplied passphrase is wrong (or missing where required). */
    class WrongPasswordException : Exception()

    /**
     * What a backup file contains + whether it was sealed. [password] is required only for a sealed
     * container — see [isSealed].
     */
    suspend fun sectionsIn(file: File, password: String? = null): Result<Inspection> = withContext(Dispatchers.IO) {
        runCatching {
            val sealed = BackupContainer.probe(file) == BackupContainer.Kind.ENCRYPTED_CONTAINER
            val (root, _) = readBackup(file, password)
            val out = mutableSetOf<Section>()
            // The file's own section list first — tolerant by design: names this build no longer has
            // (CUSTOMIZE, SETTINGS) are simply ignored. Then the record kinds, both as the fallback
            // for pre-listing files and so a section shows when it actually carries rows.
            root.optJSONArray("sections")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching { Section.valueOf(arr.getString(i)) }.getOrNull()?.let { out += it }
                }
            }
            root.optJSONArray("userData")?.let { arr ->
                for (i in 0 until arr.length()) {
                    when (arr.getJSONObject(i).optString("kind")) {
                        SyncMatrix.KIND_FAVORITES -> out += Section.FAVORITES
                        SyncMatrix.KIND_HISTORY -> out += Section.HISTORY
                        SyncMatrix.KIND_ORDER -> out += Section.MANUAL_REORDER
                        SyncMatrix.KIND_REMINDERS -> out += Section.REMINDERS
                    }
                }
            }
            root.optJSONArray("tombstones")?.let { arr ->
                for (i in 0 until arr.length()) {
                    when (arr.getJSONObject(i).optString("kind")) {
                        SyncMatrix.KIND_FAVORITES -> out += Section.FAVORITES
                        SyncMatrix.KIND_ORDER -> out += Section.MANUAL_REORDER
                        SyncMatrix.KIND_REMINDERS -> out += Section.REMINDERS
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
     * volume: how many rows would appear that are not here now (or are newer than what is), and how
     * many would be removed.
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
        /** Programme reminders the file would add or move forward. */
        val newReminders: Int = 0,
        /** Kept for the confirm screen's shape; the settings block no longer travels — always 0. */
        val changedSettings: Int = 0,
        /** Kept for the confirm screen's shape; customizations no longer travel — always false. */
        val hasCustomizations: Boolean = false,
        /** Rows this device would LOSE, because the other device deleted them more recently. */
        val deletions: Int = 0,
        /** Kept for the confirm screen's shape; the settings block no longer travels — always false. */
        val hasDeviceSettings: Boolean = false,
    ) {
        val isEmpty: Boolean
            get() = newSources == 0 && newFavorites == 0 && newHistory == 0 &&
                newReorder == 0 && newReminders == 0 && changedSettings == 0 &&
                !hasCustomizations && deletions == 0
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

            Preview(
                newFavorites = counts[SyncMatrix.KIND_FAVORITES] ?: 0,
                newHistory = counts[SyncMatrix.KIND_HISTORY] ?: 0,
                newReorder = counts[SyncMatrix.KIND_ORDER] ?: 0,
                newReminders = counts[SyncMatrix.KIND_REMINDERS] ?: 0,
                deletions = deletions,
            )
        }
    }

    /**
     * Applies the chosen [sections] of the file (only those it actually contains) as a [MERGE] by
     * default — a restore never deletes anything that already exists on the device:
     *
     * - Favorites/history/orders/reminders merge per-row last-write-wins; nothing is wiped.
     * - Records whose content row is not here yet stay pending and attach themselves after the
     *   post-restore catalogue syncs ([UserDataResolver.resolvePending]).
     *
     * For a sealed container a wrong [backupPassword] fails fast with [WrongPasswordException] and
     * changes nothing. Older files' field-encrypted secrets block is ignored outright (the settings
     * no longer travel), so a field-encrypted file restores without validating that password.
     */
    suspend fun import(
        file: File,
        sections: Set<Section> = Section.entries.toSet(),
        backupPassword: String? = null,
        mode: ImportMode = ImportMode.RESTORE,
        /**
         * Vestigial since the settings block left the payload: there are no hardware settings in the
         * file to hold back any more. The parameter stays only so existing callers' shapes hold;
         * it does nothing.
         */
        deviceSettings: Boolean = false,
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val (root, _) = readBackup(file, backupPassword)
            // Schema version of the FILE (not of this build). Only used to drop pre-v26 whole-list
            // order snapshots (see [isOldListOrder]).
            val fileVersion = root.optInt("version", 0)

            // Interrupted-restore marker: set before the first write of any kind, cleared only
            // after the last one. A restore spans chunked transactions, so no single transaction
            // can cover it; the marker is what makes a half-applied restore visible instead of
            // silent (the shell prompts about it at the next launch).
            settings.markRestoreStarted("${file.name} · ${sections.joinToString(", ") { it.name }}")
            Log.i(TAG, "Restore start file=${file.name} sections=${sections.joinToString(",") { it.name }}")

            var count = 0

            // Favorites/history/orders/reminders: stashed as pending records — they attach
            // automatically as the post-restore syncs repopulate the content tables
            // (UserDataResolver.resolvePending).
            val kinds = kindsFor(sections)
            if (kinds.isNotEmpty()) {
                fun remapUserData(arr: JSONArray): JSONArray {
                    val filtered = JSONArray()
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        if (e.optString("kind") !in kinds) continue
                        filtered.put(e)
                    }
                    return filtered
                }
                // A restore drops this device's own deletion markers BEFORE anything is applied —
                // otherwise they refuse the very rows being restored
                // (UserDataResolver.Resolution.REFUSED) and the next local sync would delete them
                // again. A merge must never do this — see [ImportMode].
                if (mode == ImportMode.RESTORE) userData.clearDeletions()
                // Deletions FIRST: they remove any local row older than the deletion, and they
                // are recorded here, so the very next step cannot re-insert what this one removed.
                root.optJSONArray("tombstones")?.let { userData.applyTombstones(remapUserData(it)) }
                root.optJSONArray("userData")?.let { arr ->
                    val filtered = remapUserData(arr).withoutOldListOrders(fileVersion)
                    // Refused records were dropped for good, not queued — counting them is what let
                    // a restore report "N items restored" having reinstated none of them.
                    val refused = userData.importAll(filtered)
                    count += (filtered.length() - refused).coerceAtLeast(0)
                }
            }

            settings.clearRestoreMarker()
            Log.i(TAG, "Restore done items=$count")
            ImportSummary(items = count)
        }
    }

    private fun kindsFor(sections: Set<Section>): Set<String> = buildSet {
        if (Section.FAVORITES in sections) add(SyncMatrix.KIND_FAVORITES)
        if (Section.HISTORY in sections) add(SyncMatrix.KIND_HISTORY)
        if (Section.MANUAL_REORDER in sections) add(SyncMatrix.KIND_ORDER)
        if (Section.REMINDERS in sections) add(SyncMatrix.KIND_REMINDERS)
    }

    companion object {
        internal const val TAG = "BackupManager"

        /** What export writes since v4.2 — a [BackupContainer], not a bare JSON file. */
        const val BACKUP_FILENAME = "owntv-backup.own"

        /** The pre-4.2 name. Export never produces it any more; restore still accepts such files. */
        const val LEGACY_BACKUP_FILENAME = "owntv-backup.json"

        /** File-picker filter for restore: the new container plus every legacy `.json` in the wild. */
        val RESTORE_EXTENSIONS = setOf("own", "json")

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
// functions with no dependency on the manager's state. A merge-restore that gets one of them wrong
// silently attaches a restored backup's data to the WRONG source on the device. The customizations
// and per-item playback blocks no longer travel, so the customization/per-item remaps have no caller
// in this build — they stay because legacy files' keys flow through the same helpers' shape and unit
// tests pin their behavior.

/**
 * True for a manual item order from a file older than v26. Those files saved the whole list on every Move, so
 * each item would keep a fixed place and the sort chip could never sort the list again; they are dropped and
 * the list starts from the playlist's order. Favorites has no sort chip, so its order is kept.
 */
internal fun isOldListOrder(record: JSONObject, fileVersion: Int): Boolean =
    fileVersion < 26 && record.optString("kind") == SyncMatrix.KIND_ORDER &&
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
