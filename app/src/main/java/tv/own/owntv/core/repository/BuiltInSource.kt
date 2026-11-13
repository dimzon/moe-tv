package tv.own.owntv.core.repository

import tv.own.owntv.core.database.SingleSource
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sync.RutubeApi

/**
 * The single built-in RuTube source. The app's setup wizard never offers IPTV providers:
 * this helper creates (idempotently) the one [SourceType.RUTUBE] row, shared by everyone
 * (the app has no profiles).
 *
 * Mirrors TVTube's [app.tvtube.data.repo.SourceRepository.ensureBuiltIn]: the row's `url`
 * is the RuTube base and its `userAgent` is the mobile UA play options requires, so
 * `source.userAgent` on the row is always the right value to send.
 */
object BuiltInSource {
    const val NAME = "RuTube"

    fun template(): SourceEntity = SourceEntity(
        name = NAME,
        type = SourceType.RUTUBE,
        url = RutubeApi.BASE_URL,
        userAgent = RutubeApi.USER_AGENT,
        syncLive = true,
        syncMovies = false,
        syncSeries = false,
    )

    /**
     * Return the built-in RuTube source, creating it when needed.
     * Idempotent: call it on first run.
     */
    suspend fun ensure(sourceDao: SourceDao): SourceEntity {
        val existing = sourceDao.rutubeSource()
        if (existing != null) {
            // The channel row no longer carries a sourceId; a few callers still ask a channel
            // "whose are you?" — answer with the one source's row id.
            SingleSource.id = existing.id
            return existing
        }
        val id = sourceDao.insert(template())
        SingleSource.id = id
        return template().copy(id = id)
    }

    /**
     * The built-in source, or null while it has not been created yet (cold-start guard).
     * Refreshes [tv.own.owntv.core.database.SingleSource] when it resolves.
     */
    suspend fun current(sourceDao: SourceDao): SourceEntity? = sourceDao.rutubeSource()?.also {
        SingleSource.id = it.id
    }
}
