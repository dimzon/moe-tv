package tv.own.owntv.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType

/**
 * The set of sources the Browse screens should show right now: every stored source. There is no
 * "active playlist" filter any more — the app has exactly one provider (the built-in RuTube source,
 * which [BuiltInSource.ensure] recreates on demand), so the old "chosen default playlist" narrowing
 * is gone with it.
 *
 * This is purely a *display* view: content imports and stores all of its rows; this only decides
 * which source ids the grids/guide read from.
 *
 * [liveSourceIds] hides sources whose `syncLive` scope is Off — cache stays on disk, rows just don't
 * surface. The [MediaType] parameter of [sourceIdsFor] is kept for call-site compatibility and maps
 * every type to [liveSourceIds], since live channels are the only content.
 */
data class ActiveProfileSources(val sources: List<SourceEntity>) {
    val sourceIds: List<Long> get() = sources.map { it.id }
    val liveSourceIds: List<Long> get() = sources.filter { it.syncLive }.map { it.id }

    fun sourceIdsFor(type: MediaType): List<Long> = liveSourceIds
}

/**
 * Reactive [ActiveProfileSources] over every stored source. Emits again whenever a source is
 * added/removed, so Browse refreshes live.
 */
fun activeProfileSources(
    sourceDao: SourceDao,
): Flow<ActiveProfileSources> =
    sourceDao.observeAll().map(::ActiveProfileSources).distinctUntilChanged()

/**
 * One-shot version of [activeProfileSources] for imperative code paths: every source id.
 *
 * When [section] is set, only sources with that section's enabledScope flag On are returned.
 */
suspend fun activeSourceIds(
    sourceDao: SourceDao,
    section: MediaType? = null,
): List<Long> {
    val all = sourceDao.observeAll().first()
    val scoped = if (section == null) all else all.filter { it.syncLive }
    return scoped.map { it.id }
}

/** The sources alone, for screens that list what exists. */
fun allSources(sourceDao: SourceDao): Flow<List<SourceEntity>> =
    sourceDao.observeAll()
