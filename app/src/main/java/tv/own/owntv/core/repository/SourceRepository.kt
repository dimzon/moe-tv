package tv.own.owntv.core.repository

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncManager
import tv.own.owntv.core.sync.SyncResult

/**
 * Adds sources (global — the app has no profiles) and runs imports. The setup wizard and the
 * playlist screen drive this; the actual parsing/inserting lives in [SyncManager].
 */
class SourceRepository(
    private val settings: SettingsRepository,
    private val syncManager: SyncManager,
    private val userData: tv.own.owntv.core.backup.UserDataResolver,
    private val channelDao: tv.own.owntv.core.database.dao.ChannelDao,
    private val categoryDao: tv.own.owntv.core.database.dao.CategoryDao,
) {
    /**
     * The one built-in source as a DTO snapshot: hardcoded identity plus the current Settings
     * values. A Flow because those settings are user-editable; it always emits exactly one element.
     */
    fun observeSources(): Flow<List<SourceEntity>> = settings.sourcePreferences
        .map { listOf(BuiltInSource.snapshot(it)) }
        .distinctUntilChanged()

    /** One-shot [observeSources] for imperative paths (workers, the importer, AutoRefresh). */
    suspend fun currentSource(): SourceEntity =
        BuiltInSource.snapshot(settings.sourcePreferences.first())

    /** The wizard's "attach the built-in source": now just the identity DTO. */
    suspend fun ensureRutube(): SourceEntity = currentSource()

    suspend fun rutubeSource(): SourceEntity = currentSource()

    /**
     * Wipe the imported catalog (channels + their categories) but keep user data. Used when a
     * backgrounded first import fails: keeping the partial content would duplicate rows on the next
     * sync (a never-synced source takes the fresh-import path, which assumes empty tables).
     * Content before categories — same order the syncers clear in.
     */
    suspend fun clearSourceContent() {
        channelDao.deleteByRemoteIds(channelDao.remoteIdsForSource())
        categoryDao.clear()
    }

    /**
     * [onProgress] is deliberately **last**: Kotlin binds a trailing lambda to the final parameter,
     * so while `forcePrune` sat there `sync(source) { … }` aimed the progress callback at a Boolean
     * and failed with *"'Boolean' was expected"*, which points at nothing useful. Every existing
     * caller already passed it by name, so moving it broke none of them and the natural spelling now
     * works too. If a parameter is ever added after this one, put it before [onProgress].
     */
    suspend fun sync(
        source: SourceEntity,
        contentTypes: SyncContentTypes = SyncContentTypes(),
        /** User-requested clean resync: allows this run to remove titles the provider no longer lists. */
        forcePrune: Boolean = false,
        onProgress: (ImportStage) -> Unit,
    ): SyncResult {
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "sync wrapper start sourceId=${source.id} type=${source.type} contentTypes=$contentTypes")
        // Snapshot favorites/history with stable keys BEFORE the sync clears content (their ids
        // change on every refresh, so they'd otherwise orphan — count badge set, list empty).
        val snapshotStartedAt = SystemClock.elapsedRealtime()
        val snapshot = runCatching { userData.exportSnapshot() }
            .onSuccess { Log.i(TAG, "userData export sourceId=${source.id} rows=${it.length()} ${it.typeCounts()} ms=${SystemClock.elapsedRealtime() - snapshotStartedAt}") }
            .onFailure { Log.w(TAG, "userData export failed sourceId=${source.id} ms=${SystemClock.elapsedRealtime() - snapshotStartedAt}", it) }
            .getOrNull()
        val coreStartedAt = SystemClock.elapsedRealtime()
        val (result, _) = syncManager.sync(source, onProgress, contentTypes, forcePrune)
        Log.i(TAG, "core sync sourceId=${source.id} result=${result.name()} ms=${SystemClock.elapsedRealtime() - coreStartedAt}")
        // Always re-attach the snapshot to the new ids — a failed/cancelled sync can still have
        // rewritten some rows (the clear-then-insert pass renumbers row ids), and
        // without a relink those favorites/history entries turn invisible until a later
        // successful sync. Purging genuinely-gone rows is only allowed after a complete enabled
        // success: a partial pass never touched the rest, and a failure proves nothing. Off
        // sections retain cache + user data, so their favorites stay resolvable.
        val effective = contentTypes.effectiveFor(source)
        val purge = result is SyncResult.Success && result.catalogueComplete &&
            effective.isCompleteFor(SyncContentTypes.enabledFor(source))
        val relinkStartedAt = SystemClock.elapsedRealtime()
        // Serialized: parallel source syncs (startup refresh runs every source at once) must not
        // relink/purge against each other's mid-flight state.
        relinkMutex.withLock {
            runCatching { userData.afterSync(purge) }
                .onSuccess { Log.i(TAG, "userData relink sourceId=${source.id} rows=${snapshot?.length() ?: 0} purge=$purge ms=${SystemClock.elapsedRealtime() - relinkStartedAt}") }
                .onFailure { Log.w(TAG, "userData relink failed sourceId=${source.id} ms=${SystemClock.elapsedRealtime() - relinkStartedAt}", it) }
        }
        Log.i(TAG, "sync wrapper end sourceId=${source.id} result=${result.name()} totalMs=${SystemClock.elapsedRealtime() - startedAt}")
        return result
    }

    fun getLastSyncStats(sourceId: Long): tv.own.owntv.core.sync.SyncRunStats? =
        syncManager.getLastSyncStats(sourceId)

    /** Per-mediaType row counts of a snapshot, e.g. "types={LIVE=8, MOVIE=6}" — diagnostics. */
    private fun org.json.JSONArray.typeCounts(): String {
        val counts = LinkedHashMap<String, Int>()
        for (i in 0 until length()) {
            val t = optJSONObject(i)?.optString("t") ?: continue
            counts[t] = (counts[t] ?: 0) + 1
        }
        return "types=$counts"
    }

    private companion object {
        const val TAG = "SourceRepository"
        /** Class-wide: SourceRepository is a Koin singleton, but keep the lock global regardless. */
        val relinkMutex = Mutex()
    }
}

private fun SyncResult.name(): String = when (this) {
    is SyncResult.Success -> "Success"
    is SyncResult.Failed -> "Failed"
    SyncResult.Cancelled -> "Cancelled"
}
