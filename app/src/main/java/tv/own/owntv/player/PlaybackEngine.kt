package tv.own.owntv.player

import kotlinx.coroutines.flow.StateFlow

/**
 * What the player HUD needs from "whichever engine is currently playing" — the ExoPlayer live engine
 * ([LivePreviewEngine], the preview pane promoted to full-screen or a Multiview tile surfaced directly).
 * The app is live-TV-only now, so the interface is exactly what that engine implements: no seek,
 * speed, position or prev/next members remain.
 */
interface PlaybackEngine {
    val isPlaying: StateFlow<Boolean>
    val buffering: StateFlow<Boolean>
    val error: StateFlow<PlaybackFailure?>
    /** The structured underlying failure (plain reason • media spec • raw engine text), shown under the
     *  friendly message so users can report the real cause without adb/logcat. Null when none. */
    val errorInfo: StateFlow<ErrorInfo?>
    /** Set while the engine is sitting out a wait the provider asked for (HTTP 429 + `Retry-After`) and
     *  will re-ask for the identical stream by itself. The HUD shows it as a spinner with a live countdown
     *  instead of an error screen. Null when nothing is pending. */
    val providerBackOff: StateFlow<ProviderBackOff?>
    /**
     * When the current stall began (`SystemClock.elapsedRealtime()`), for a stream that had been playing
     * and is now trying to get back — null while playing, before the first frame, and once the engine
     * has given up (then [error] says so). Once a stall has lasted a while the HUD says the stream is
     * reconnecting, so a long reconnect reads as "working on it" rather than a frozen app.
     */
    val stalledSinceMs: StateFlow<Long?>
    val videoRes: StateFlow<String?>
    /** Up-to-4 mini stream chips (aspect · resolution · fps · audio) for the player top bar. */
    val streamChips: StateFlow<List<String>>
    /** Re-check [streamChips] now. */
    fun refreshStreamChips()
    /** Bitrate is only ever displayed in the debug overlay — enable tracking only while it's open. */
    fun setBitrateTrackingEnabled(enabled: Boolean)
    val volume: StateFlow<Int>
    val zoomMode: StateFlow<ZoomMode>
    val audioCount: StateFlow<Int>
    val subCount: StateFlow<Int>
    val currentMeta: StateFlow<MediaMeta>

    /**
     * Whether the sound comes out of THIS device.
     *
     * True for every decoder that runs here, which is why it defaults to true. False only for an
     * engine that has handed the stream to something else — a Chromecast — where the phone's audio
     * focus and its headphone-unplug rule would be acting on sound it is not making. Pulling the
     * headphones out should not pause a channel playing in another room.
     */
    val playsLocally: Boolean get() = true

    /** True while the engine decodes audio only (video output stopped to save power) — Audio Mode. */
    val audioOnly: StateFlow<Boolean>

    /**
     * True when the ITEM itself carries no video track at all — a radio channel in a TV playlist.
     *
     * Distinct from [audioOnly], which is the app switching video off because the user asked. This one is
     * a property of the stream, and the UI needs it: sound over a black screen is indistinguishable from
     * a broken player, so the player says so on screen instead of leaving the user to guess.
     */
    val audioOnlyMedia: StateFlow<Boolean>

    /**
     * True once this live channel has dropped enough frames on ExoPlayer that a retry is worth a
     * hint; the player shows it once. Once per channel per app session, false again on every tune.
     */
    val smoothnessHint: StateFlow<Boolean>

    /** Stop the video decoder/output but keep audio playing at position (Audio Mode enter). No-op if
     *  already audio-only. Audio is uninterrupted — ExoPlayer releases its surface. */
    fun enterAudioOnly()
    /** Resume video output (Audio Mode exit → back to fullscreen/mini). No-op if not audio-only. */
    fun exitAudioOnly()

    fun togglePlayPause()
    fun setZoomMode(mode: ZoomMode)
    fun adjustVolume(delta: Int)

    /**
     * The two above, but as a DELIBERATE user choice: the engine applies the change and remembers it
     * for the channel currently playing (`playback_prefs`), so the same channel comes back at that
     * zoom and volume. Everything else that moves zoom or volume — audio-focus ducking, re-applying
     * zoom on a new video track — must keep calling the plain setters, or the player would teach
     * itself preferences the user never expressed.
     */
    fun setZoomModeByUser(mode: ZoomMode) = setZoomMode(mode)
    fun adjustVolumeByUser(delta: Int) = adjustVolume(delta)
    fun toggleMute()
    fun retry()
    fun selectAudio(id: Int)
    fun selectSubtitle(id: Int)
    fun disableSubtitles()
    fun audioTracks(): List<TrackOption>
    fun textTracks(): List<TrackOption>

    /** The picture heights this stream offers, highest first; empty when there is nothing to choose
     *  between (the player's Quality button shows only then). */
    val videoQualities: StateFlow<List<Int>>
    /** The Quality-menu pick for this stream, or null for Auto (Settings → Maximum video quality). */
    val videoQualityPick: StateFlow<Int?>
    fun selectVideoQuality(height: Int?)

    /** Live technical readout (label → value) for the stream-info overlay — codec, resolution, fps, HDR,
     *  bitrate, decoder, audio, buffer, source. A snapshot; the overlay re-reads it periodically.
     *
     *  Suspending so a future engine that gathers its numbers off-thread stays possible; the ExoPlayer
     *  implementations stay on the main thread, which is what Media3 requires, and simply never suspend. */
    suspend fun streamInfo(): List<StreamInfoRow>

    /** In-player A/V-sync nudge (ms); positive = sound later. 0 on an engine that cannot shift it. */
    val audioDelayMs: StateFlow<Int>
    /** True when this engine can shift audio against video — the ExoPlayer engines
     *  ([AudioDelayClock]); the HUD hides the nudge elsewhere (e.g. tunneled playback, cast). */
    fun audioDelayAvailable(): Boolean
    /** Whether the current channel has its own remembered A/V-sync offset. */
    val audioDelayRemembered: StateFlow<Boolean>
    /** Remember the current A/V-sync offset for this channel, or forget it again. */
    fun toggleRememberAudioDelay()
    fun adjustAudioDelay(deltaMs: Int)
}
