package tv.own.owntv.core.repository

import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sync.RutubeApi

/**
 * The single built-in RuTube source. The app's setup wizard never offers IPTV providers:
 * this helper creates (idempotently) the one [SourceType.RUTUBE] row every profile shares.
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
     * Return the built-in RuTube source, creating and linking it to [profileId] when needed.
     * Idempotent: call it on first run and on every profile creation.
     */
    suspend fun ensure(sourceDao: SourceDao, profileId: Long): SourceEntity {
        val existing = sourceDao.rutubeSource()
        if (existing != null) {
            sourceDao.link(ProfileSourceCrossRef(profileId = profileId, sourceId = existing.id))
            return existing
        }
        val id = sourceDao.insert(template())
        sourceDao.link(ProfileSourceCrossRef(profileId = profileId, sourceId = id))
        return template().copy(id = id)
    }

    /**
     * The source for the active profile, or null when no profile has linked it yet
     * (cold-start guard: the app creates it once a profile exists).
     */
    suspend fun forProfile(sourceDao: SourceDao, profileId: Long): SourceEntity? {
        // Direct lookup first (one query), then the link table.
        sourceDao.rutubeSource()?.let { return it }
        return null
    }
}
