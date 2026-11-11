package tv.own.owntv.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/**
 * The set of sources the Browse screens should show right now: every stored source, narrowed to the
 * user's chosen "active playlist" ([SettingsRepository.defaultSourceId]) when one is set.
 *
 * A default of `-1` (or a default that no longer exists) means **All playlists** — the full merged
 * view. This is purely a *display* filter: every playlist still imports and stores all of its content
 * and EPG; this only decides which source ids the grids/guide read from.
 *
 * The app is live-only: [liveSourceIds] hides sources whose `syncLive` scope is Off — cache stays on
 * disk, rows just don't surface. The [MediaType] parameter of [sourceIdsFor] is kept for call-site
 * compatibility and maps every type to [liveSourceIds], since live channels are the only content.
 */
data class ActiveProfileSources(val sources: List<SourceEntity>) {
    val sourceIds: List<Long> get() = sources.map { it.id }
    val liveSourceIds: List<Long> get() = sources.filter { it.syncLive }.map { it.id }

    fun sourceIdsFor(type: MediaType): List<Long> = liveSourceIds
}

/**
 * Reactive [ActiveProfileSources] with the active-playlist filter. Emits again whenever a source is
 * added/removed or the chosen default changes, so Browse refreshes live.
 */
fun activeProfileSources(
    settings: SettingsRepository,
    sourceDao: SourceDao,
): Flow<ActiveProfileSources> =
    combine(sourceDao.observeAll(), settings.defaultSourceId) { srcs, defaultId ->
        val filtered = if (defaultId > 0 && srcs.any { it.id == defaultId }) {
            srcs.filter { it.id == defaultId }
        } else {
            srcs
        }
        filtered.forEach(::seedSessionLimit)
        ActiveProfileSources(filtered)
    }.distinctUntilChanged()

/**
 * Tell the playback layer about a provider that allows only one stream at a time, using the value the
 * Xtream panel reported at sync (`user_info.max_connections`).
 *
 * Without this the app can only *learn* the limit by failing a tune and reading the 458 back, which means
 * one misbehaving zap after every app start. The learned cache is session-only by design (it is a guess);
 * the synced number is a fact, so it is safe to state up front.
 *
 * Hung off the shared source flow because that is the one place every screen — Home included — already
 * passes through, and it costs a set-add per source. It never *clears* the flag: a panel caught refusing
 * a second session stays flagged for the session even if it later reports a bigger number.
 */
private fun seedSessionLimit(s: SourceEntity) {
    if (s.maxConnections == 1 && s.url.isNotBlank()) {
        tv.own.owntv.core.player.LiveSessionLimit.singleSession(s.url)
    }
}

/**
 * One-shot version of [activeProfileSources] for imperative code paths: every source id, narrowed to
 * the active-playlist filter when one is set (else all).
 *
 * When [section] is set, only sources with that section's enabledScope flag On are returned.
 */
suspend fun activeSourceIds(
    settings: SettingsRepository,
    sourceDao: SourceDao,
    section: MediaType? = null,
): List<Long> {
    val all = sourceDao.observeAll().first()
    val defaultId = settings.defaultSourceId.first()
    val filtered = if (defaultId > 0 && all.any { it.id == defaultId }) {
        all.filter { it.id == defaultId }
    } else {
        all
    }
    val scoped = if (section == null) filtered else filtered.filter { it.syncLive }
    return scoped.map { it.id }
}

/** The sources alone (no active-playlist narrowing), for screens that list what exists. */
fun allSources(sourceDao: SourceDao): Flow<List<SourceEntity>> =
    sourceDao.observeAll().map { srcs -> srcs.also { it.forEach(::seedSessionLimit) } }
