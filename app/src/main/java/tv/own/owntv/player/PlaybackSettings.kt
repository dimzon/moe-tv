package tv.own.owntv.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.player.AudioOutputWatch
import tv.own.owntv.core.player.OutputAudioDelays
import tv.own.owntv.core.player.SurroundMode
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Every setting the live engines and the live tuner read, as one value.
 *
 * The engines used to subscribe to each setting separately — some thirty DataStore pipelines — and
 * each started on a hard-coded default until its own first read landed. A tune that began in that
 * window (Last-channel autoplay at a cold start) opened with defaults: hardware decoding on, no
 * pre-buffer. Now there is one process-wide snapshot, started from
 * [PlaybackStartup] in `Application.onCreate`; an engine built after it has a value takes that value
 * during construction, and the live tuner waits for it ([await]) before its first tune.
 *
 * Only the plumbing changed: each engine still reacts to each field exactly as it reacted to that
 * setting, through [field].
 */
data class PlaybackSettings(
    /** HLS with SDR and HDR variants plays the SDR ones — see [SdrVariants]. */
    val preferSdr: Boolean,
    val hwDecoding: Boolean,
    /** Software decoding allowed above 1080p — see [SettingsRepository.softwareDecodeAbove1080p]. */
    val softwareDecodeAbove1080p: Boolean,
    val surroundMode: SurroundMode,
    val measuredStreamStats: Boolean,
    val livePrerollSecs: Int,
    val liveBufferSeconds: Int?,
    val audioDelayMs: Int,
    /** Extra delay per output kind, on top of [audioDelayMs]. */
    val outputAudioDelays: OutputAudioDelays,
    val preferredAudioLang: String,
    val preferredSubLang: String,
    val defaultZoom: String,
    val defaultVolume: Int,
    val liveTuneTimeoutSecs: Int,
    /** Passthrough (ExoPlayer), night mode, volume levelling. See [AudioDynamics]. */
    val audioPassthrough: Boolean,
    val nightMode: Boolean,
    val volumeLevelling: Boolean,
    /** Quality limits (0 = none; see [VideoQuality.cap]) and tunneled playback. */
    val maxVideoHeight: Int,
    val mobileDataMaxVideoHeight: Int,
    val tunneledPlayback: Boolean,
    /** Keep CPU and Wi-Fi awake while ExoPlayer plays. */
    val backgroundPlayback: Boolean,
) {
    companion object {
        @Volatile private var shared: Pair<SettingsRepository, StateFlow<PlaybackSettings?>>? = null

        /** The process-wide snapshot for [settings]; null until the store has been read once. */
        fun of(settings: SettingsRepository): StateFlow<PlaybackSettings?> {
            shared?.takeIf { it.first === settings }?.let { return it.second }
            return synchronized(this) {
                shared?.takeIf { it.first === settings }?.second
                    ?: snapshotOf(settings).also { shared = settings to it }
            }
        }

        /** The snapshot, waiting for the store's first read if it has not landed yet. */
        suspend fun await(settings: SettingsRepository): PlaybackSettings = of(settings).filterNotNull().first()

        @Suppress("UNCHECKED_CAST")
        private fun snapshotOf(s: SettingsRepository): StateFlow<PlaybackSettings?> {
            val sources: List<Flow<Any?>> = listOf(
                s.preferSdr, s.hwDecoding, s.softwareDecodeAbove1080p, s.surroundMode,
                s.measuredStreamStats, s.livePrerollSecs, s.liveBufferSeconds,
                s.audioDelayMs, s.outputAudioDelays, s.preferredAudioLang, s.preferredSubLang,
                s.defaultZoom, s.defaultVolume, s.liveTuneTimeoutSecs,
                s.audioPassthrough, s.nightMode, s.volumeLevelling,
                s.maxVideoHeight, s.mobileDataMaxVideoHeight, s.tunneledPlayback, s.backgroundPlayback,
            )
            return combine(sources) { v ->
                PlaybackSettings(
                    preferSdr = v[0] as Boolean,
                    hwDecoding = v[1] as Boolean,
                    softwareDecodeAbove1080p = v[2] as Boolean,
                    surroundMode = v[3] as SurroundMode,
                    measuredStreamStats = v[4] as Boolean,
                    livePrerollSecs = v[5] as Int,
                    liveBufferSeconds = v[6] as Int?,
                    audioDelayMs = v[7] as Int,
                    outputAudioDelays = v[8] as OutputAudioDelays,
                    preferredAudioLang = v[9] as String,
                    preferredSubLang = v[10] as String,
                    defaultZoom = v[11] as String,
                    defaultVolume = v[12] as Int,
                    liveTuneTimeoutSecs = v[13] as Int,
                    audioPassthrough = v[14] as Boolean,
                    nightMode = v[15] as Boolean,
                    volumeLevelling = v[16] as Boolean,
                    maxVideoHeight = v[17] as Int,
                    mobileDataMaxVideoHeight = v[18] as Int,
                    tunneledPlayback = v[19] as Boolean,
                    backgroundPlayback = v[20] as Boolean,
                )
            }.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, null)
        }
    }
}

/**
 * One field of the snapshot as its own flow: emitted once the store has been read, then only when that
 * field changes — the same shape each engine's per-setting subscription had.
 */
internal fun <T> StateFlow<PlaybackSettings?>.field(pick: (PlaybackSettings) -> T): Flow<T> =
    filterNotNull().map(pick).distinctUntilChanged()

/** The extra audio delay for the output media plays to right now; follows both the setting and the device. */
internal fun StateFlow<PlaybackSettings?>.outputAudioDelayMs(context: android.content.Context): Flow<Int> =
    combine(field { it.outputAudioDelays }, AudioOutputWatch.current(context)) { delays, kind -> delays.offsetFor(kind) }
        .distinctUntilChanged()
