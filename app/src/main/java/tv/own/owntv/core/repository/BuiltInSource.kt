package tv.own.owntv.core.repository

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
        sourceDao.rutubeSource()?.let { return it }
        val id = sourceDao.insert(template())
        return template().copy(id = id)
    }

    /** The built-in source, or null while it has not been created yet (cold-start guard). */
    suspend fun current(sourceDao: SourceDao): SourceEntity? = sourceDao.rutubeSource()
}
