package tv.own.owntv.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The one auto-refresh choice shared by the catalogue and the guide. The two data sets are one
 * source now, so asking the same question twice would only let them drift apart.
 *
 * Stored as the existing per-source [PlaylistRefresh] and [EpgRefresh] maps: an absent entry means
 * [EVERY_6_HOURS], which is the default for both.
 */
enum class DataRefreshPolicy {
    /** Refresh the catalogue and the guide every six hours. */
    EVERY_6_HOURS,

    /** Refresh once on a cold start. */
    STARTUP,

    /** Only fill missing data; no periodic refresh. */
    ONLY_FIRST,
    ;

    fun playlistRefresh(): PlaylistRefresh = when (this) {
        EVERY_6_HOURS -> PlaylistRefresh(PlaylistAutoRefresh.HOURS_6)
        STARTUP -> PlaylistRefresh(PlaylistAutoRefresh.STARTUP)
        ONLY_FIRST -> PlaylistRefresh.OFF
    }

    fun epgRefresh(): EpgRefresh = when (this) {
        EVERY_6_HOURS -> EpgRefresh(EpgAutoRefresh.HOURS_6)
        STARTUP -> EpgRefresh(EpgAutoRefresh.STARTUP)
        ONLY_FIRST -> EpgRefresh.OFF
    }

    companion object {
        val DEFAULT: DataRefreshPolicy = EVERY_6_HOURS
    }
}

/** Writes one policy choice into both existing per-source maps. */
suspend fun SettingsRepository.setDataRefreshPolicy(sourceId: Long, policy: DataRefreshPolicy) {
    setPlaylistAutoRefresh(sourceId, policy.playlistRefresh())
    setEpgAutoRefresh(sourceId, policy.epgRefresh())
}

/**
 * Reads the shared choice back from the two per-source maps that
 * [tv.own.owntv.core.sync.AutoRefresh] actually consults.
 * Absent entries mean the 6-hour default; the catalogue's entry wins when the two disagree.
 */
fun SettingsRepository.dataRefreshPolicy(sourceId: Long): Flow<DataRefreshPolicy> =
    combine(playlistAutoRefresh, epgAutoRefresh) { playlist, epg ->
        dataRefreshPolicyOf(
            playlistMode = playlist[sourceId]?.mode,
            epgMode = epg[sourceId]?.mode,
        )
    }

internal fun dataRefreshPolicyOf(
    playlistMode: PlaylistAutoRefresh?,
    epgMode: EpgAutoRefresh?,
): DataRefreshPolicy = when {
    playlistMode == PlaylistAutoRefresh.OFF -> DataRefreshPolicy.ONLY_FIRST
    playlistMode == PlaylistAutoRefresh.STARTUP -> DataRefreshPolicy.STARTUP
    playlistMode != null -> DataRefreshPolicy.EVERY_6_HOURS
    // Older builds could have written only the guide's entry; honour it until the next save.
    epgMode == EpgAutoRefresh.OFF -> DataRefreshPolicy.ONLY_FIRST
    epgMode == EpgAutoRefresh.STARTUP -> DataRefreshPolicy.STARTUP
    else -> DataRefreshPolicy.DEFAULT
}
