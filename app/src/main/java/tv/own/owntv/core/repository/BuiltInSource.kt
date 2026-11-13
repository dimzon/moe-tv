package tv.own.owntv.core.repository

import tv.own.owntv.core.database.SingleSource
import tv.own.owntv.core.database.entity.FOLLOW_GLOBAL_PREROLL
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.LiveLatency
import tv.own.owntv.core.settings.SourcePreferences
import tv.own.owntv.core.sync.RutubeApi

/**
 * The one built-in RuTube source, as a **singleton DTO** — no `sources` row, no DAO (schema v5).
 * The identity is hardcoded: RuTube, its API base, and the mobile UA that play-options requires.
 *
 * The settings-backed fields (pre-buffer, latency, "give up after", clean-names, last sync) are not
 * hardcoded: [snapshot] copies them from the global Settings values, because with one source a
 * "per-playlist override" *is* the global setting. The repository observes those values and rebuilds
 * the DTO, so every existing `SourceEntity` reader keeps working unchanged.
 */
object BuiltInSource {
    const val ID: Long = SingleSource.id
    const val NAME = "RuTube"

    /** The hardcoded identity/defaults. Never mutated; [snapshot] copies settings into it. */
    val instance: SourceEntity = SourceEntity(
        id = ID,
        name = NAME,
        type = SourceType.RUTUBE,
        url = RutubeApi.BASE_URL,
        userAgent = RutubeApi.USER_AGENT,
        syncLive = true,
        syncMovies = false,
        syncSeries = false,
        livePrerollSecs = FOLLOW_GLOBAL_PREROLL,
        liveLatencyMode = null,
        createdAt = 0L,
        lastSyncAt = null,
    )

    /**
     * The identity plus the current Settings values. Sentinel semantics are preserved for the UI's
     * "X overrides" counters: a value equal to the global default is exposed as "follow global"
     * (`-1` / `null`), so a fresh install reports no overrides and a non-default global reports one.
     */
    fun snapshot(prefs: SourcePreferences): SourceEntity = instance.copy(
        livePrerollSecs = prefs.livePrerollSecs.takeIf { it > 0 } ?: FOLLOW_GLOBAL_PREROLL,
        liveLatencyMode = prefs.liveLatencyMode
            .takeIf { it != LiveLatency.DEFAULT.name },
        liveLatencyCustomSecs = prefs.liveLatencyCustomSecs,
        liveTuneTimeoutSecs = prefs.liveTuneTimeoutSecs
            .takeIf { it != tv.own.owntv.core.player.PlaybackDefaults.LIVE_TUNE_BUDGET_SECS },
        lastSyncAt = prefs.lastSyncAt,
        cleanChannelNames = prefs.cleanChannelNames,
        channelNameStrip = prefs.channelNameStrip,
    )

    /** The DTO with no Settings applied — first-run callers that only need identity. */
    val current: SourceEntity get() = instance
}
