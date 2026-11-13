package tv.own.owntv.core.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.TombstoneDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.CustomCategoryMemberEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.ReminderEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.database.transaction
import tv.own.owntv.core.settings.SyncMatrix

/**
 * Records that could not be attached yet, kept on disk so they outlive the process and heal on a later
 * sync. Deliberately NOT in the database: the whole point is that the content tables are being
 * replaced when these are written.
 *
 * `internal` rather than private only so the instrumentation test can empty it between cases. A
 * Preferences DataStore is one instance per file per process, so a test cannot open its own — and
 * without a way to clear it, one test's unresolved records healed against the next test's fresh
 * database and every assertion about "how many favorites are there" was counting someone else's.
 */
internal val Context.pendingStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_pending_userdata")
internal val PENDING_KEY = stringPreferencesKey("entries")

/**
 * Backs up and restores the user data that lives on volatile content ids: favorites, watch
 * history, manual positions and programme reminders (the app has a single user — profiles are
 * gone). Content rows are clear-then-insert on every sync, so ids can't be exported directly —
 * instead each record is exported with a stable identity (sourceId + provider remoteId, falling
 * back to the name) and re-resolved against the content tables AFTER the post-restore sync
 * repopulates them. Unresolvable records stay pending and are retried after every sync, so they
 * heal as content appears.
 *
 * The payload carries exactly the four sync-matrix categories (phase ③): `fav`, `his`, `order`,
 * `prog` — see [SyncMatrix.KINDS]. Custom-category membership (`member`) is no longer part of it,
 * but the **intra-device** re-sync snapshot ([exportForSource] → [relinkAfterSync]) still carries
 * and heals it, because the custom-categories feature keeps living locally.
 *
 * Every apply is per-row last-write-wins: a record only changes a row when the record's timestamp
 * is **newer** than the row's ([FavoriteEntity.addedAt] / [WatchHistoryEntity.watchedAt] /
 * [ContentOrderEntity.updatedAt] / [ReminderEntity.updatedAt]), so fresh local edits are never
 * overwritten by older copies from the household.
 */
class UserDataResolver(
    private val context: Context,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val contentOrderDao: ContentOrderDao,
    private val customCategoryDao: CustomCategoryDao,
    private val tombstoneDao: TombstoneDao,
    private val db: tv.own.owntv.core.database.OwnTVDatabase,
) {

    private fun reminderDao() = db.reminderDao()

    // --- deletions (v36) -------------------------------------------------------------------------
    //
    // Local sync merges and never clobbers, so an absent row is indistinguishable from one the other
    // device has not heard about yet — and the merge puts it back. These functions are the other
    // half: a deletion the user meant is remembered as a fact with a timestamp, travels in the sync
    // payload exactly like a favorite does, and beats any older copy of the row on the far side.

    /**
     * The stable content key of a record, with its fields in a fixed order so the same item always
     * produces the same string. That string is the tombstone's identity, and it is compared as text
     * against a unique index — two spellings of the same item would be two different deletions.
     *
     * [record] is a record as [describe] builds it (or as a sync payload carries it, already remapped
     * to this device's source ids). Everything identifying is copied; the bookkeeping fields
     * (`kind`/`at`/`oid`/`pos`/`dur`/`dev`) are not, because they say when and by whom, not what.
     * Old files and payloads may also carry a per-profile `p`; profiles are gone and it is ignored.
     *
     * Reminders identify by (channelId, startMs) — see [ReminderEntity] — and `rid` carries the
     * channelId there; every producer of this string spells those two fields the same way.
     */
    fun canonicalIdentity(record: JSONObject): String = JSONObject().apply {
        record.optStringOrNull("rid")?.let { put("rid", it) }
        record.optStringOrNull("name")?.let { put("name", it) }
        // Membership is per custom category: removing a channel from one list is not removing it from
        // another, so the category has to be part of what was deleted.
        record.optStringOrNull("ctx")?.let { put("ctx", it) }
        if (record.has("startMs")) put("startMs", record.optLong("startMs"))
    }.toString()

    /** The stable content key of a channel, or null when its content row is already gone. */
    suspend fun identityOf(itemId: String): JSONObject? = describe(itemId)

    /**
     * Remembers that one row was deleted, so the deletion survives a sync.
     *
     * Call it BEFORE the delete, inside the same transaction: [describe] reads the content row the
     * record points at, and after the delete there may be nothing left to describe. A row whose
     * content is already missing records nothing at all — that is an orphan being tidied up, not a
     * choice the user made, and propagating it would delete the item on a device where it is fine.
     */
    suspend fun recordDeletion(
        kind: String,
        itemId: String,
        contextKey: String? = null,
        at: Long = System.currentTimeMillis(),
    ) {
        val record = describe(itemId) ?: return
        contextKey?.let { record.put("ctx", it) }
        tombstoneDao.record(kind, canonicalIdentity(record), at)
    }

    /**
     * Records that one reminder was dismissed, so the deletion survives a sync. A reminder's sync
     * identity is (channelId, startMs) — see [ReminderEntity]. Call before the row is deleted.
     */
    suspend fun recordReminderDeletion(
        channelId: String,
        startMs: Long,
        at: Long = System.currentTimeMillis(),
    ) {
        tombstoneDao.record(
            SyncMatrix.KIND_REMINDERS,
            canonicalIdentity(JSONObject().put("rid", channelId).put("startMs", startMs)),
            at,
        )
    }

    /** Trims the tombstone table to [MAX_TOMBSTONES]. Called once after a bulk deletion, not per row. */
    suspend fun pruneTombstones() {
        if (tombstoneDao.count() > MAX_TOMBSTONES) tombstoneDao.prune(MAX_TOMBSTONES)
    }

    /**
     * Drops this device's deletion markers — see [TombstoneDao.deleteAll].
     * A restore calls this before applying the file; a merge never does.
     */
    suspend fun clearDeletions() {
        tombstoneDao.deleteAll()
    }

    /** The deletions to put in a sync payload, as records of the same shape [exportAll] produces. */
    suspend fun exportTombstones(kinds: Set<String>): JSONArray {
        val out = JSONArray()
        tombstoneDao.getAllOnce().forEach { row ->
            if (row.kind !in kinds) return@forEach
            val record = runCatching { JSONObject(row.identity) }.getOrNull() ?: return@forEach
            out.put(record.put("kind", row.kind).put("at", row.deletedAt))
        }
        return out
    }

    /**
     * Applies deletions that arrived from another device: each one removes the matching local row
     * **only when that row is older than the deletion**, so a favorite re-added after the other
     * device deleted it survives. The tombstone is then recorded here too — both so a third device
     * hears about it, and so the same deletion cannot be undone by an older copy of the row arriving
     * later in the very same payload.
     *
     * Only [TOMBSTONE_KINDS] apply; a payload's `his`/`member` markers (older builds wrote them) are
     * ignored — history and custom-category deletions stopped travelling with the sync matrix.
     */
    suspend fun applyTombstones(entries: JSONArray?): Int {
        if (entries == null || entries.length() == 0) return 0
        var applied = 0
        var i = 0
        while (i < entries.length()) {
            val end = minOf(i + RESOLVE_CHUNK, entries.length())
            db.transaction {
                for (j in i until end) {
                    val e = entries.getJSONObject(j)
                    if (runCatching { applyTombstone(e) }.getOrDefault(false)) applied++
                }
            }
            i = end
        }
        pruneTombstones()
        return applied
    }

    private suspend fun applyTombstone(e: JSONObject): Boolean {
        val kind = e.optString("kind")
        if (kind !in TOMBSTONE_KINDS) return false
        val at = e.optLong("at", 0)
        // Recorded first: the row may not even exist here (nothing to delete), but a third device
        // still has to learn that it was deleted, and an older copy of it may arrive later.
        tombstoneDao.record(kind, canonicalIdentity(e), at)
        return when (kind) {
            SyncMatrix.KIND_FAVORITES -> {
                val itemId = locate(e) ?: return false
                favoriteDao.removeIfOlderThan(itemId, at) > 0
            }
            SyncMatrix.KIND_ORDER -> {
                val itemId = locate(e) ?: return false
                val ctx = e.optStringOrNull("ctx") ?: return false
                contentOrderDao.removeIfOlderThan(ctx, itemId, at) > 0
            }
            SyncMatrix.KIND_REMINDERS -> {
                val channelId = e.optStringOrNull("rid") ?: return false
                reminderDao().removeIfOlderThan(channelId, e.optLong("startMs", 0), at) > 0
            }
            else -> false
        }
    }

    /**
     * Exports the chosen kinds as stable-key records for the backup file / sync payload. The
     * default is exactly the four categories the payload carries ([SyncMatrix.KINDS]); the
     * intra-device re-sync snapshot goes through [exportSnapshot], which adds `member`.
     */
    suspend fun exportAll(kinds: Set<String> = SyncMatrix.KINDS): JSONArray {
        val out = JSONArray()
        if (SyncMatrix.KIND_FAVORITES in kinds) favoriteDao.getAllOnce().forEach { f ->
            describe(f.itemId)?.let { out.put(it.put("kind", SyncMatrix.KIND_FAVORITES).put("at", f.addedAt)) }
        }
        if (SyncMatrix.KIND_HISTORY in kinds) historyDao.getAllOnce().forEach { h ->
            describe(h.itemId)?.let {
                out.put(
                    it.put("kind", SyncMatrix.KIND_HISTORY).put("at", h.watchedAt)
                        // Which device watched it — the "recently in this household" rail's attribution.
                        .putOpt("dev", h.deviceId.takeIf { d -> d.isNotBlank() }),
                )
            }
        }
        if (SyncMatrix.KIND_ORDER in kinds) contentOrderDao.getAllOnce().forEach { o ->
            describe(o.itemId)?.let {
                // `at` is the row's LWW stamp ([ContentOrderEntity.updatedAt]) — the merge on the far
                // side only takes a position that is newer than its own.
                out.put(it.put("kind", SyncMatrix.KIND_ORDER).put("ctx", o.contextKey).put("pos", o.position).put("at", o.updatedAt))
            }
        }
        if (SyncMatrix.KIND_REMINDERS in kinds) exportReminders(out)
        return out
    }

    /** Reminders as `prog` records. Identity is (channelId, startMs); `rid` carries the channelId. */
    private suspend fun exportReminders(out: JSONArray) {
        reminderDao().all().forEach { r ->
            out.put(
                JSONObject()
                    .put("kind", SyncMatrix.KIND_REMINDERS)
                    .put("rid", r.channelId)
                    .put("startMs", r.startMs)
                    .put("stopMs", r.stopMs)
                    .put("title", r.title)
                    .put("lead", r.leadMinutes)
                    .put("at", r.updatedAt),
            )
        }
    }

    /**
     * The intra-device re-sync snapshot: the payload kinds plus custom-category membership, as
     * stable-key records. With hex keys this is consumed only by [afterSync]'s purge decision —
     * the rows themselves no longer need re-keying.
     */
    suspend fun exportSnapshot(kinds: Set<String> = SNAPSHOT_KINDS): JSONArray {
        val out = exportAll(kinds)
        if (MEMBER_KIND in kinds) customCategoryDao.getAllOnce().forEach { m ->
            describe(m.itemId)?.let {
                out.put(
                    it.put("kind", MEMBER_KIND).put("ctx", m.contextKey).put("pos", m.position),
                )
            }
        }
        return out
    }

    /**
     * The post-sync pass, replacing the old capture-before/relink-after dance: user-data keys ARE
     * the content keys now, so a re-sync cannot orphan them — the only thing that can is the
     * provider dropping a channel, and [purge] controls whether such rows are dropped. [purge] must
     * be false when the sync didn't fully succeed (a partial import can leave rows missing that a
     * later sync brings back; purging then would delete favorites for content that isn't gone).
     */
    suspend fun afterSync(purge: Boolean) {
        if (purge) {
            favoriteDao.purgeOrphans()
            historyDao.purgeOrphans()
            contentOrderDao.purgeOrphans()
            customCategoryDao.purgeOrphans()
        }
        resolvePending() // also retries any in-flight backup restore
    }

    /** Appends records to the pending set (de-duplicated by content), so they heal on a later resolve. */
    private suspend fun addPending(extra: JSONArray) {
        context.pendingStore.edit { prefs ->
            val existing = prefs[PENDING_KEY]?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
            val seen = HashSet<String>()
            for (i in 0 until existing.length()) seen.add(existing.getJSONObject(i).toString())
            for (i in 0 until extra.length()) {
                val s = extra.getJSONObject(i).toString()
                if (seen.add(s)) existing.put(extra.getJSONObject(i))
            }
            prefs[PENDING_KEY] = existing.toString()
        }
    }

    /** Merge-restore (backup): appends the backup's records to the pending set (deduplicated) and
     *  tries resolving — never drops records already pending.
     *
     *  Returns how many records a newer local deletion refused. Those are dropped for good rather
     *  than left pending, so the caller must not count them as restored — see [BackupManager]. */
    suspend fun importAll(entries: JSONArray?): Int {
        if (entries != null && entries.length() > 0) addPending(entries)
        return resolvePending()
    }

    /**
     * Tries to attach pending records to current content rows. Called after every successful source
     * sync; resolved records are inserted (idempotently — the user data tables have unique
     * (type, item) indices) and removed from the pending set.
     */
    suspend fun resolvePending(): Int {
        val raw = context.pendingStore.data.first()[PENDING_KEY] ?: return 0
        val entries = runCatching { JSONArray(raw) }.getOrNull() ?: return 0
        if (entries.length() == 0) return 0

        val (remaining, refused) = resolveAllChunked(entries)
        context.pendingStore.edit { prefs ->
            if (remaining.length() == 0) prefs.remove(PENDING_KEY) else prefs[PENDING_KEY] = remaining.toString()
        }
        return refused
    }

    /**
     * Resolves and inserts records in chunked transactions: each record used to be 2–4 lookups
     * plus a single-row insert in its OWN write transaction (one fsync each) — a restore of
     * thousands of favorites/history rows became thousands of fsyncs. Per-record failures are
     * still caught individually (a bad record never aborts its chunk); chunking keeps any single
     * transaction short so sync/UI writers aren't starved. Returns the records that didn't resolve,
     * and how many a newer local deletion refused.
     */
    private data class ResolveResult(val unresolved: JSONArray, val refused: Int)

    private suspend fun resolveAllChunked(entries: JSONArray): ResolveResult {
        val unresolved = JSONArray()
        var refused = 0
        // Asked once, not once per record: on a device that has never synced — and after every
        // ordinary playlist refresh, which relinks thousands of rows through here — the table is
        // empty, and an extra indexed lookup per record is a cost paid for nothing.
        val tombstonesPresent = tombstoneDao.count() > 0
        var i = 0
        while (i < entries.length()) {
            val end = minOf(i + RESOLVE_CHUNK, entries.length())
            db.transaction {
                for (j in i until end) {
                    val e = entries.getJSONObject(j)
                    when (runCatching { resolveAndInsert(e, tombstonesPresent) }.getOrDefault(Resolution.PENDING)) {
                        Resolution.PENDING -> unresolved.put(e)
                        Resolution.REFUSED -> refused++
                        Resolution.HANDLED -> Unit
                    }
                }
            }
            i = end
        }
        return ResolveResult(unresolved, refused)
    }

    // --- export side: content row → stable identity ---

    private suspend fun describe(itemId: String): JSONObject? = channelDao.getById(itemId)?.let {
        JSONObject().put("rid", it.remoteId).put("name", it.name)
    }

    // --- restore side: stable identity → current content row ---

    /**
     * Would this incoming record add something this device does not have? The dry run's question,
     * answered without writing anything.
     *
     * "Add" is per-row LWW, exactly like the apply: a record whose content has not been downloaded
     * here yet counts as new (it will be held pending and attach itself when the catalogue arrives),
     * and so does one that is **newer** than the row already here — applying it would actually
     * change something. A record a local deletion already outranks does not, because applying it
     * would do nothing.
     */
    suspend fun wouldAdd(kind: String, e: JSONObject): Boolean {
        val at = e.optLong("at", 0)
        if (tombstoneDao.deletedAt(kind, canonicalIdentity(e))?.let { it >= at } == true) return false
        return when (kind) {
            SyncMatrix.KIND_FAVORITES -> {
                val itemId = locate(e) ?: return true
                favoriteDao.addedAt(itemId)?.let { it >= at } != true
            }
            SyncMatrix.KIND_HISTORY -> {
                val itemId = locate(e) ?: return true
                historyDao.watchedAt(itemId)?.let { it >= at } != true
            }
            SyncMatrix.KIND_ORDER -> {
                val ctx = e.optStringOrNull("ctx") ?: return false
                val itemId = locate(e) ?: return true
                contentOrderDao.updatedAt(ctx, itemId)?.let { it >= at } != true
            }
            SyncMatrix.KIND_REMINDERS -> {
                val channelId = e.optStringOrNull("rid") ?: return false
                val existing = reminderDao().getByChannelAndStart(channelId, e.optLong("startMs", 0)) ?: return true
                existing.updatedAt < at
            }
            else -> false
        }
    }

    /** Would this incoming deletion actually remove a row that is here now? */
    suspend fun wouldRemove(e: JSONObject): Boolean {
        val kind = e.optString("kind")
        if (kind !in TOMBSTONE_KINDS) return false
        val at = e.optLong("at", 0)
        return when (kind) {
            SyncMatrix.KIND_FAVORITES -> {
                val itemId = locate(e) ?: return false
                favoriteDao.addedAt(itemId).let { it != null && it <= at }
            }
            SyncMatrix.KIND_ORDER -> {
                val itemId = locate(e) ?: return false
                val ctx = e.optStringOrNull("ctx") ?: return false
                contentOrderDao.updatedAt(ctx, itemId).let { it != null && it <= at }
            }
            SyncMatrix.KIND_REMINDERS -> {
                val channelId = e.optStringOrNull("rid") ?: return false
                reminderDao().getByChannelAndStart(channelId, e.optLong("startMs", 0))?.let { it.updatedAt <= at } == true
            }
            else -> false
        }
    }

    /**
     * The content key a record points at, or null while the channel is not (yet) here. The record's
     * `rid` IS the key — the hex never changes — and the name is only a fallback for very old files
     * that predate stable ids.
     */
    private suspend fun locate(e: JSONObject): String? {
        val rid = e.optStringOrNull("rid")
        return rid?.takeIf { channelDao.getById(it) != null }
            ?: channelDao.findByName(e.optStringOrNull("name") ?: e.getString("name"))?.remoteId
    }

    /** What [resolveAndInsert] did with one record. */
    private enum class Resolution {
        /** Applied, or deliberately dropped. Either way it leaves the pending set. */
        HANDLED,

        /** A newer local deletion refused it. It leaves the pending set too, but it was NOT restored,
         *  so it must not be counted as one — that miscount is what let a restore report success
         *  while reinstating nothing. */
        REFUSED,

        /** Its content row isn't on this device yet; stays pending for the next sync to resolve. */
        PENDING,
    }

    private suspend fun resolveAndInsert(e: JSONObject, tombstonesPresent: Boolean): Resolution {
        val kind = runCatching { e.getString("kind") }.getOrNull() ?: return Resolution.HANDLED
        // Reminders key on the channel id, not on a content row: they resolve without an itemId.
        if (kind == SyncMatrix.KIND_REMINDERS) return resolveAndInsertReminder(e, tombstonesPresent)
        val itemId: String = locate(e) ?: return Resolution.PENDING
        return applyRecord(e, itemId, tombstonesPresent)
    }

    /** One record with its content row resolved: tombstone gate, then the per-kind LWW apply. */
    private suspend fun applyRecord(e: JSONObject, itemId: String, tombstonesPresent: Boolean): Resolution {
        // The record's own profile (`p`) is no longer read: the app has one user, and old files or
        // payloads that still carry the field are applied as that one user's data.
        val at = e.optLong("at", System.currentTimeMillis())
        // The other half of the merge rule: a record older than a deletion of the same row loses to
        // it. Without this, a merge sync hands back every favorite the user has ever removed, because
        // the far device's copy of the row is perfectly valid — it just predates the removal.
        // Dropped rather than retried for ever — but reported as REFUSED, because the caller must be
        // able to tell "put back" from "deliberately not put back". A restore clears this device's
        // markers first (see BackupManager.ImportMode.RESTORE), so there it refuses nothing.
        if (tombstonesPresent && tombstoneDao.deletedAt(e.optString("kind"), canonicalIdentity(e))?.let { it >= at } == true) {
            return Resolution.REFUSED
        }
        return runCatching {
            when (e.getString("kind")) {
                SyncMatrix.KIND_FAVORITES -> {
                    favoriteDao.add(FavoriteEntity(itemId = itemId, addedAt = at))
                    // Per-row LWW: the incoming copy takes the row only when it is actually newer
                    // than the one here; an older arriving copy changes nothing.
                    favoriteDao.bumpIfNewer(itemId, at)
                }
                // Newest wins, both of them. An incoming record is a fact with a time on it exactly
                // like a deletion is, and the later fact is the true one — so these insert when the
                // row is absent and then move it forward only if the incoming copy is actually newer.
                // A plain REPLACE would let the other device's older state win purely by arriving
                // second, which is the difference between a merge and a coin toss.
                SyncMatrix.KIND_HISTORY -> {
                    // One row per (item, device): the payload's `dev` is part of the key, and an
                    // absent one means "this device wrote it before the id could be read".
                    val deviceId = e.optStringOrNull("dev").orEmpty()
                    historyDao.insertIfAbsent(WatchHistoryEntity(itemId = itemId, watchedAt = at, deviceId = deviceId))
                    historyDao.bumpIfNewer(itemId, deviceId, at)
                }
                SyncMatrix.KIND_ORDER -> {
                    val ctx = e.optStringOrNull("ctx") ?: return@runCatching Resolution.HANDLED
                    val pos = e.getInt("pos")
                    // A record that predates `updatedAt` (old containers' order rows carried no
                    // timestamp) inserts when the row is absent but never takes one this device
                    // already has — its stamp is 0, so [bumpIfNewer] can never fire for it.
                    val rowAt = if (e.has("at")) at else 0L
                    contentOrderDao.insertIfAbsent(
                        ContentOrderEntity(contextKey = ctx, itemId = itemId, position = pos, updatedAt = rowAt),
                    )
                    contentOrderDao.bumpIfNewer(ctx, itemId, pos, rowAt)
                }
                // Custom-category membership is not part of the sync payload any more; this branch
                // serves only the intra-device re-sync snapshot ([exportForSource] → [relinkAfterSync]).
                MEMBER_KIND -> customCategoryDao.insertAll(
                    listOf(CustomCategoryMemberEntity(contextKey = e.getString("ctx"), itemId = itemId, position = e.getInt("pos"))),
                )
            }
            Resolution.HANDLED
        }.getOrDefault(Resolution.PENDING)
    }

    /**
     * Applies one `prog` record, per-row LWW on [ReminderEntity.updatedAt]: absent → insert, local
     * older → replace, local same-or-newer → keep. The identity is (channelId, startMs); a record
     * whose channel this device does not have is skipped for good.
     */
    private suspend fun resolveAndInsertReminder(e: JSONObject, tombstonesPresent: Boolean): Resolution {
        val at = e.optLong("at", System.currentTimeMillis())
        // The same deletion-loses rule as every other kind: an older record behind a newer local
        // dismissal must not resurrect the reminder (see [applyRecord]).
        if (tombstonesPresent &&
            tombstoneDao.deletedAt(e.optString("kind"), canonicalIdentity(e))?.let { it >= at } == true
        ) {
            return Resolution.REFUSED
        }
        val channelId = e.optStringOrNull("rid") ?: return Resolution.HANDLED
        val startMs = e.optLong("startMs", 0)
        val dao = db.reminderDao()
        val existing = dao.getByChannelAndStart(channelId, startMs)
        if (existing != null && existing.updatedAt >= at) return Resolution.HANDLED
        val channel = channelDao.getById(channelId) ?: return Resolution.HANDLED
        return runCatching {
            dao.upsert(
                ReminderEntity(
                    channelId = channelId,
                    title = e.optStringOrNull("title") ?: channel.name,
                    startMs = startMs,
                    stopMs = if (e.has("stopMs")) e.optLong("stopMs") else startMs,
                    // ReminderSchedule.DEFAULT_LEAD_MINUTES spelled locally so core/backup does not
                    // import core/reminder for one constant.
                    leadMinutes = e.optInt("lead", DEFAULT_REMINDER_LEAD_MINUTES),
                    createdAt = existing?.createdAt ?: at,
                    updatedAt = at,
                ),
            )
            Resolution.HANDLED
        }.getOrDefault(Resolution.PENDING)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    companion object {
        /** Records per write transaction in [resolveAllChunked]. */
        private const val RESOLVE_CHUNK = 500

        /** Fallback lead for a `prog` record that carries none — ReminderSchedule's "5 min before". */
        private const val DEFAULT_REMINDER_LEAD_MINUTES = 5

        /**
         * Deletions that travel in a sync payload: fav/order/prog. History deletions do not travel
         * (its rows merge by `watchedAt` and the deletion kind was retired with the sync matrix),
         * and custom-category membership left the payload entirely. Reorder positions are not
         * among them: a position is overwritten by the newer one, never "missing", so it needs no
         * marker — the `order` kind exists for the folder-clears of future writers.
         */
        val TOMBSTONE_KINDS = setOf(SyncMatrix.KIND_FAVORITES, SyncMatrix.KIND_ORDER, SyncMatrix.KIND_REMINDERS)

        /** The legacy kind string for custom-category membership rows (intra-device snapshot only). */
        const val MEMBER_KIND = "member"

        /** The intra-device re-sync snapshot's kinds: the payload's four plus [MEMBER_KIND]. */
        val SNAPSHOT_KINDS = SyncMatrix.KINDS + MEMBER_KIND

        /** Newest deletions kept. "Clear watch history" writes one per row, and a deletion is only
         *  useful until every device has seen it. */
        private const val MAX_TOMBSTONES = 20_000
    }
}
