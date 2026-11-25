package tv.own.owntv.core.repository

import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao

/**
 * The one provider's guide: the built-in RuTube source's schedule, fetched from its API and written
 * straight into the shared EPG tables by [tv.own.owntv.core.sync.RuTubeEpgSyncer]. There is no XMLTV
 * downloader any more — RuTube has no `.xmltv` feed, and the standalone-EPG-source system that used
 * to carry one is gone. The grid reads from [EpgDao]; finished rows are pruned by the syncer's own
 * window, so the guide stays bounded.
 */
class EpgRepository(
    private val epgDao: EpgDao,
    private val channelDao: ChannelDao,
    /** RuTube's own API; null only in unit-test fixtures that never refresh. */
    private val rutube: tv.own.owntv.core.sync.RutubeApi? = null,
    /** Records the attempt so the startup policy can tell "never tried" from "tried, no schedule". */
    private val settings: tv.own.owntv.core.settings.SettingsRepository? = null,
) {


    /**
     * Refresh the guide: the built-in source's per-channel/day grid from the API, written straight
     * into the shared EPG tables by [tv.own.owntv.core.sync.RuTubeEpgSyncer]. Only the channels the
     * catalogue holds are asked for.
     */
    suspend fun refresh(onProgress: (channels: Int, programmes: Int) -> Unit = { _, _ -> }): Int {
        val api = rutube ?: return 0
        val keys = channelDao.allEpgChannelIds()
        if (keys.isEmpty()) return 0
        onProgress(keys.size, 0)
        val summary = tv.own.owntv.core.sync.RuTubeEpgSyncer(api, epgDao).sync(keys)
        if (summary.hasFailures) {
            // IOException keeps WorkManager's transient-failure classification: HTTP 5xx, timeouts and
            // incomplete passes must retry instead of being treated as a terminal programmer error.
            throw java.io.IOException(
                "EPG sync incomplete: failedChannels=${summary.failedChannels}, " +
                    "failedWrites=${summary.failedWrites}, failedSeed=${summary.failedSeed}",
            )
        }
        // A completed pass counts as an attempt even with zero programmes (all channels returned 404);
        // otherwise every cold start would hammer RuTube with the same 404s. Any failed channel/write
        // above leaves the timestamp untouched so the worker reports/retries the incomplete pass.
        settings?.markEpgSynced(System.currentTimeMillis())
        onProgress(summary.channelsWithGuide, summary.programmesWritten)
        return summary.programmesWritten
    }
}
