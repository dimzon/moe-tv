package tv.own.owntv.player

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.playStreamUrl
import tv.own.owntv.core.player.contentKey
import tv.own.owntv.core.sync.RutubeCaption
import tv.own.owntv.core.settings.LiveBuffer
import tv.own.owntv.core.settings.LiveLatency
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SourceOverrides
import tv.own.owntv.core.stalker.ReconnectUrlProvider
import tv.own.owntv.core.stalker.StreamUrlResolver

/** Everything a live tune hands the engine besides the URL. */
data class LiveRequest(
    val meta: MediaMeta,
    val userAgent: String?,
    val prerollSecs: Int?,
    val liveBuffer: LiveBuffer.Override?,
    val httpHeaders: String?,
    val drmConfig: String?,
    val manifestType: String?,
    val directSource: String?,
    /** Sidecar subtitles the feed advertises beside this stream (RuTube `captions[]`); empty for
     *  sources/feeds that carry none. */
    val captions: List<RutubeCaption> = emptyList(),
)

/** Start [url] on the live ExoPlayer engine with everything [request] carries — a tune, or a Multiview tile. */
internal fun LivePreviewEngine.play(url: String, muted: Boolean, request: LiveRequest) = play(
    url,
    muted = muted,
    meta = request.meta,
    userAgent = request.userAgent,
    prerollSecsOverride = request.prerollSecs,
    liveBufferOverride = request.liveBuffer,
    httpHeaders = request.httpHeaders,
    drmConfig = request.drmConfig,
    manifestType = request.manifestType,
    directSource = request.directSource,
    captions = request.captions,
)

/**
 * One live channel from "tune" to "playing" or "gave up".
 *
 * This is the orchestration the television's `LiveViewModel` and the phone's `LiveTuner` each carried
 * in their own ~600 lines, and which had drifted apart: the fallback [LiveLadder] and its "Give up
 * after" alarm, the HLS/TS rung choice, HLS-redirect learning and the Stalker reconnect link. The app
 * keeps only presentation — which channel is on screen, the HUD, history.
 *
 * The app plays live on one engine, [LivePreviewEngine]; the ladder's rungs are the two stream formats
 * (HLS first when the channel offers it, then the plain `.ts` endpoint), and after them there is
 * nothing left to try: the failure goes on screen.
 *
 * **Every tune is one job, and a newer one cancels it** ([launch]). Two quick picks can no longer
 * finish in the wrong order.
 *
 * Everything is main-thread: [scope] must be a Main-dispatched scope, as the engine requires.
 */
class LiveTuneController(
    private val scope: CoroutineScope,
    private val engine: LivePreviewEngine,
    private val host: Host,
) {

    /** What the controller needs from outside the engine. [CoreHost] is the one the app uses. */
    interface Host {
        suspend fun sourceOf(sourceId: Long): SourceEntity?
        /** A Stalker playlist stores a portal command, minted into a URL per play. */
        fun needsResolve(source: SourceEntity?): Boolean
        suspend fun resolve(source: SourceEntity, cmd: String): String?
        /** Sidecar subtitles the feed advertises beside a resolved stream (RuTube `captions[]`); empty
         *  for every other source — and for a source that is not loaded (yet) — so the caller can pass
         *  it straight through unchecked. */
        suspend fun captions(source: SourceEntity?, cmd: String): List<RutubeCaption> = emptyList()
        /** Settings → "Give up on a channel after", in seconds; 0 is Never. */
        suspend fun globalBudgetSecs(): Int
        fun meta(channel: ChannelEntity): MediaMeta
        /** Mirror a ladder decision into the user-visible playback error log. */
        fun recordLadderEvent(reason: PlayerFailureReason, detail: String)
        fun nowMs(): Long = android.os.SystemClock.elapsedRealtime()
        /** A download is using the only stream [sourceId] allows, so the preview pane must not take it. */
        fun downloadHoldsOnlyStream(sourceId: Long): Boolean = false
    }

    private val _liveOnExo = MutableStateFlow(false)

    /**
     * Whether the controller is holding the engine for a full-screen stream — true from [start] (or
     * [expectPromotion] on a promoted preview) until [detach] or [stop]. False while the engine is the
     * preview pane's, or idle. The pane must not re-tune or re-mute the stream while this is true.
     */
    val liveOnExo: StateFlow<Boolean> = _liveOnExo.asStateFlow()

    private val _previewBlocked = MutableStateFlow(false)

    /** True while the preview pane is held back because the provider allows one stream at a time and
     *  something outside the app — a download — already holds it. */
    val previewBlockedSingleSession: StateFlow<Boolean> = _previewBlocked.asStateFlow()

    private val recall = ChannelRecall()

    /** The channel watched before the one on screen — the "previous channel" key's target. The app
     *  still vets it (profile, playlists, adult filter) before tuning it. */
    val previousChannel: StateFlow<ChannelEntity?> = recall.previous

    /** The channel this controller is tuning or playing full-screen; null when it is not driving one. */
    private var current: ChannelEntity? = null

    private val ladder = LiveLadder()
    private var armedBudgetMs: Long = LiveLadder.NO_BUDGET

    /**
     * The channel whose ExoPlayer rung is the explicit `.ts` one. Keyed by channel, not a bare flag: the
     * preview tunes through [exoUrlFor] too, so a flag left set by one channel's TS rung would send every
     * other channel's preview to `.ts` until the next tune.
     */
    private var forceTsFor: String? = null

    /** The Stalker command whose minted URL the engine holds, so a re-focus or a promote can tell "same
     *  channel" without minting again — the engine's own URL is the minted link, not the command. */
    private var exoStalkerCmd: String? = null

    private var tuneJob: Job? = null
    private var previewJob: Job? = null
    private var exoResolveJob: Job? = null
    private var exoWatchJob: Job? = null
    private var deadlineJob: Job? = null

    /** Whether the engine is sitting on its error state for the stream it holds. */
    private val engineFailed: Boolean get() = engine.state.value == LivePreviewEngine.State.ERROR

    // --- Entry points -----------------------------------------------------------------------------

    /**
     * Run [block] as the one tune in flight, cancelling whichever was running. Every
     * playback-changing entry point of an app goes through here — a tune, a retune at the live edge —
     * so the last thing asked for is the thing that happens.
     */
    fun launch(block: suspend LiveTuneController.() -> Unit): Job {
        tuneJob?.cancel()
        return scope.launch { block() }.also { tuneJob = it }
    }

    /**
     * Route [channel], arm its ladder and start the engine. Called from inside [launch].
     *
     * [resolved] is a URL already minted for a Stalker channel by this tune (the phone mints one before
     * offering the channel to a cast receiver): minting ends the portal's previous session, so the first
     * rung reuses it and only later rungs mint again.
     */
    suspend fun start(channel: ChannelEntity, source: SourceEntity?, resolved: String? = null) {
        previewJob?.cancel()
        cancelLadderJobs()
        current = channel
        recall.onWatched(channel)
        _previewBlocked.value = false
        engineLog("tune '${channel.name}' -> ExoPlayer")
        arm(channel, source)
        if (previewedChannelId == channel.id && engineFailed) {
            // The preview already found the engine can't open this channel. Promoting it put the
            // preview's error on screen for ~1 s while the ladder moved on to the next rung: go there
            // directly.
            engineLog("'${channel.name}' — its ExoPlayer preview had already failed; trying the next rung")
            advance(channel, source, "the ExoPlayer preview had already failed", aboutFormat = false)
        } else {
            startOnExo(channel, source, resolved)
        }
    }

    /** The channel the preview pane last asked the engine for (see [expectPromotion]). */
    private var previewedChannelId: String? = null

    /**
     * The user is opening [channel] full screen. When the engine is already previewing it, say so at
     * once, before the full-screen player is first drawn: it then shows the engine's surface from the
     * start instead of building another one and swapping it out when [start] gets there. Measured on a
     * 4K channel: ~4 s of black on opening before this existed. [start] sets this again in any case.
     */
    fun expectPromotion(channel: ChannelEntity) {
        if (previewedChannelId == channel.id && engine.currentUrl != null && !engineFailed) _liveOnExo.value = true
    }

    /**
     * The preview pane: play [channel] on the engine, muted or not, with no ladder.
     *
     * Never while promoted to full-screen — a late preview would re-mute the full-screen stream. On a
     * one-session panel a download holding the stream would lock the user's own playback out, so the
     * pane stays silent and [previewBlockedSingleSession] says why.
     */
    fun preview(channel: ChannelEntity, muted: Boolean) {
        if (_liveOnExo.value) {
            if (current != null) return
            // An [expectPromotion] no tune followed (an external player, a refused channel): the pane is
            // still the pane, and must not stay frozen on the last channel.
            _liveOnExo.value = false
        }
        previewedChannelId = channel.id
        previewJob?.cancel()
        previewJob = scope.launch {
            val source = host.sourceOf(channel.sourceId)
            if (host.needsResolve(source)) {
                if (exoStalkerCmd == channel.streamUrl && !engineFailed) {
                    engine.setMuted(muted)
                    return@launch
                }
                val url = host.resolve(source!!, channel.streamUrl) ?: return@launch
                if (_liveOnExo.value) return@launch // promoted to full-screen while resolving
                if (blockedBySingleSession(channel)) return@launch
                exoStalkerCmd = channel.streamUrl
                setReconnect(channel, source)
                engine.play(url, muted, request(channel, source, host.captions(source, channel.streamUrl)))
                return@launch
            }
            val url = exoUrlFor(channel, source)
            if (blockedBySingleSession(channel)) return@launch
            if (engine.currentUrl == url && !engineFailed) {
                engine.setMuted(muted) // already previewing it (a re-focus) — just re-apply the mute
                return@launch
            }
            exoStalkerCmd = null
            setReconnect(null, null)
            engine.play(url, muted, request(channel, source, host.captions(source, channel.streamUrl)))
        }
    }

    /**
     * Tune [channel] into a Multiview tile's own [engine] — same URL, headers, overrides and Stalker
     * minting as a full-screen tune, so the grid can never drift from it. No ladder: a tile that fails
     * shows its failure.
     */
    suspend fun playTile(engine: LivePreviewEngine, channel: ChannelEntity, muted: Boolean) {
        val source = host.sourceOf(channel.sourceId)
        val url = if (host.needsResolve(source)) {
            host.resolve(source!!, channel.streamUrl) ?: return
        } else {
            exoUrlFor(channel, source)
        }
        engine.play(url, muted, request(channel, source, host.captions(source, channel.streamUrl)))
    }

    /**
     * Stop driving the channel but leave the stream playing — leaving full-screen for the Live screen,
     * where the pane re-takes the engine as a preview. Watchers and the alarm stand down: a preview has
     * no ladder.
     */
    fun detach() {
        tuneJob?.cancel()
        cancelLadderJobs()
        current = null
        _liveOnExo.value = false
    }

    /** Stop the engine (the preview, or a stream [detach] left playing). */
    fun stopExo() {
        previewJob?.cancel()
        exoResolveJob?.cancel()
        exoWatchJob?.cancel()
        exoStalkerCmd = null
        setReconnect(null, null)
        engine.stop()
    }

    /** Cancel the tune in flight, from outside [launch]. */
    fun cancelTune() {
        tuneJob?.cancel()
    }

    /** Stop everything: the engine, every watcher, the give-up alarm and the reconnect link. */
    fun stop() {
        cancelTune()
        previewJob?.cancel()
        cancelLadderJobs()
        current = null
        _previewBlocked.value = false
        _liveOnExo.value = false
        exoStalkerCmd = null
        setReconnect(null, null)
        engine.stop()
    }

    // --- Engine -----------------------------------------------------------------------------------

    /**
     * Open [channel] on ExoPlayer and watch it. If the engine already holds this channel — the preview
     * the user just pressed OK on — it is promoted (unmuted) instead of rebuilt.
     */
    private suspend fun startOnExo(channel: ChannelEntity, source: SourceEntity?, resolved: String? = null) {
        _liveOnExo.value = true
        if (host.needsResolve(source)) {
            startOnExoStalker(channel, source!!, resolved)
            return
        }
        val url = exoUrlFor(channel, source)
        if (engine.currentUrl == url) {
            engine.setMuted(false) // promote — instant if already playing, otherwise keeps loading
        } else {
            // Leaving a UHD channel: release its 4K decoder before the rebuild (no-op for SD/HD).
            engine.releaseDecoderForUhd()
            exoStalkerCmd = null
            setReconnect(null, null)
            engine.play(url, muted = false, request(channel, source, host.captions(source, channel.streamUrl)))
        }
        watchExo(channel, source)
    }

    private suspend fun startOnExoStalker(channel: ChannelEntity, source: SourceEntity, resolved: String?) {
        if (exoStalkerCmd == channel.streamUrl && resolved == null) {
            engine.setMuted(false) // promote the preview that already holds this command
            setReconnect(channel, source)
            watchExo(channel, source)
            return
        }
        exoResolveJob?.cancel()
        engine.releaseDecoderForUhd()
        // Best-effort, alongside minting: captions are read for the same channel, and a fetch that
        // fails must not keep the stream it rides with from starting.
        val captions = host.captions(source, channel.streamUrl)
        if (resolved != null) {
            // Already minted by this tune: start now, so the caller's next line finds the stream there.
            playExoStalker(channel, source, resolved, captions)
            return
        }
        // Detached: minting is a network call, and a ladder step that got here from a watcher must not
        // die with that watcher when [watchExo] replaces it.
        exoResolveJob = scope.launch {
            val url = host.resolve(source, channel.streamUrl) ?: return@launch
            if (!isCurrent(channel)) return@launch // zapped away while minting
            playExoStalker(channel, source, url, captions)
        }
    }

    private fun playExoStalker(channel: ChannelEntity, source: SourceEntity, url: String, captions: List<RutubeCaption>) {
        exoStalkerCmd = channel.streamUrl
        setReconnect(channel, source)
        engine.play(url, muted = false, request(channel, source, captions))
        watchExo(channel, source)
    }

    // --- Watcher ----------------------------------------------------------------------------------

    private fun watchExo(channel: ChannelEntity, source: SourceEntity?) {
        exoWatchJob?.cancel()
        exoWatchJob = scope.launch {
            LiveExoWatchdog(
                engine,
                // A watchdog outlives the tune that armed it; firing after that would stop a stream
                // nobody complained about.
                stillOurs = { isStillExo(channel) },
                // Into the ladder rather than straight to the failure: the ladder decides what "next" means.
                handOver = { reason -> advance(channel, source, reason) },
                onOpened = ::standDownAlarm,
                // A provider back-off is a wait OwnTV agreed to; it is not charged to the budget.
                postponeDeadline = { ladder.postponeDeadline(it) },
                log = ::engineLog,
            ).watch(channel.name)
        }
    }

    // --- The ladder -------------------------------------------------------------------------------

    private suspend fun arm(channel: ChannelEntity, source: SourceEntity?) {
        forceTsFor = null
        val secs = SourceOverrides.liveTuneTimeoutSecsOf(source) ?: host.globalBudgetSecs()
        armedBudgetMs = if (secs <= 0) LiveLadder.NO_BUDGET else secs * 1000L
        ladder.arm(channel.streamUrl, budgetMs = armedBudgetMs, nowMs = host.nowMs()) {
            hasHlsAlternative(channel, source)
        }
        startAlarm(channel)
    }

    /**
     * The alarm behind "Give up after", so the budget bounds the black screen rather than only the
     * decision to climb another rung: a rung entered at 24 s with a 35 s timeout of its own would
     * otherwise run to 59 s. The deadline is re-read on each pass, so a postponement moves the alarm.
     * A channel that opens stands the alarm down — later stalls belong to the watchdog.
     */
    private fun startAlarm(channel: ChannelEntity) {
        deadlineJob?.cancel()
        deadlineJob = scope.launch {
            while (ladder.owns(channel.streamUrl)) {
                val left = (ladder.deadlineAt() ?: return@launch) - host.nowMs()
                if (left <= 0) break
                delay(left)
            }
            // Both gates matter: the ladder can still own a channel the user walked away from, and this
            // alarm stops the engine.
            if (!ladder.owns(channel.streamUrl)) return@launch
            if (!isStillExo(channel)) return@launch
            val detail = "no picture within ${armedBudgetMs / 1000}s of tuning"
            engineLog("'${channel.name}' — giving up: $detail")
            host.recordLadderEvent(PlayerFailureReason.LIVE_NO_FALLBACK, "'${channel.name}': $detail")
            exoWatchJob?.cancel()
            exoResolveJob?.cancel()
            abandon(channel, detail)
        }
    }

    private fun standDownAlarm() {
        deadlineJob?.cancel()
        deadlineJob = null
    }

    /**
     * Move to the next untried rung after a failure, or give up when there is nothing left. The only
     * place the ladder is climbed, which is what makes "each rung at most once" hold — and that
     * finiteness is what stops a channel re-tuning itself forever.
     */
    private suspend fun advance(
        channel: ChannelEntity,
        source: SourceEntity?,
        reason: String,
        /** Whether the failure says anything about the stream's format; by default, unless it was a refusal. */
        aboutFormat: Boolean = !isRequestRefusal(reason),
    ) {
        if (!ladder.owns(channel.streamUrl)) return // a newer tune owns the ladder now
        val nowMs = host.nowMs()
        // ExoPlayer played this channel, then found its content unplayable (not a connection problem).
        // The opening budget is long spent by then, which would refuse the next rung: give it a fresh one.
        if (_liveOnExo.value && engine.contentBroken) ladder.restartDeadline(nowMs, armedBudgetMs)
        val outOfTime = ladder.expired(nowMs)
        // A panel refusing the *request* (a busy 458, a 403, a rate limit) says nothing about the format,
        // so nothing may be learned from it.
        val next = ladder.advance(failureWasAboutFormat = aboutFormat, nowMs = nowMs) ?: run {
            val detail = if (outOfTime) "$reason — gave up after ${armedBudgetMs / 1000}s" else reason
            // Nowhere to go, but ExoPlayer is still reconnecting a channel that played. Abandoning here
            // sets its gaveUp flag, which stops those reconnects: measured on an upstream outage of ~50s
            // with the link still up (so no "network restored" to resume on), the next connect failure
            // left a black screen until the user pressed Retry. The engine reports "Lost connection"
            // itself once its own ladder is spent.
            if (_liveOnExo.value && engine.stillReconnecting) {
                engineLog("'${channel.name}' — no fallback left ($detail); leaving ExoPlayer's own reconnects running")
                return
            }
            engineLog("'${channel.name}' — no fallback left ($detail)")
            host.recordLadderEvent(PlayerFailureReason.LIVE_NO_FALLBACK, "'${channel.name}': $detail")
            abandon(channel, detail)
            return
        }
        val label = ladder.label(next)
        engineLog("'${channel.name}' falling back to $label ($reason)")
        host.recordLadderEvent(PlayerFailureReason.LIVE_FALLBACK, "'${channel.name}': $label — $reason")
        forceTsFor = if (next.isHls) null else channel.streamUrl
        startOnExo(channel, source)
    }

    /** Put the failure on screen: a stream that delivers no segment produces no frame AND no error, so
     *  the honest answer is written by whoever decided to stop trying. */
    private fun abandon(channel: ChannelEntity, detail: String) {
        engine.abandon("'${channel.name}': $detail")
    }

    private fun cancelLadderJobs() {
        exoResolveJob?.cancel()
        exoWatchJob?.cancel()
        deadlineJob?.cancel()
    }

    // --- Small helpers ----------------------------------------------------------------------------

    private fun isCurrent(channel: ChannelEntity): Boolean = current?.id == channel.id

    private fun isStillExo(channel: ChannelEntity): Boolean = _liveOnExo.value && isCurrent(channel)

    /** Whether the preview pane may ask for [channel]'s stream: only something outside the app (a
     *  download) competes with it for a one-session panel — the full-screen stream is guarded in
     *  [preview] itself, and a preview replaces the previous preview instead of competing. */
    private fun blockedBySingleSession(channel: ChannelEntity): Boolean {
        val blocked = host.downloadHoldsOnlyStream(channel.sourceId)
        _previewBlocked.value = blocked
        return blocked
    }

    /**
     * Which of the channel's two addresses ExoPlayer should ask for: the "Prefer HLS" `.m3u8`, except on
     * the ladder's `.ts` rung or a channel already caught this session having no working `.m3u8`.
     */
    private fun exoUrlFor(channel: ChannelEntity, source: SourceEntity?): String =
        if (forceTsFor == channel.streamUrl || LiveStreamQuirks.lacksHlsVariant(channel.streamUrl)) channel.streamUrl
        else channel.playStreamUrl(source)

    /** Whether "Prefer HLS" rewrites this channel's URL at all; if not, HLS and TS rungs are one
     *  attempt and the ladder drops the HLS ones. A Stalker command is not a URL. */
    private fun hasHlsAlternative(channel: ChannelEntity, source: SourceEntity?): Boolean =
        !host.needsResolve(source) && channel.playStreamUrl(source) != channel.streamUrl

    private fun isRequestRefusal(reason: String): Boolean =
        PlayerErrors.httpStatusIn(reason)?.let { LiveStreamQuirks.isRequestRefusal(it) } == true

    /**
     * Install the Stalker reconnect link on the engine while a Stalker channel plays, so a mid-session
     * stream death mints a fresh `create_link` instead of looping on the expired one; cleared otherwise.
     */
    private fun setReconnect(channel: ChannelEntity?, source: SourceEntity?) {
        if (channel == null || source == null || !host.needsResolve(source)) {
            engine.reconnectUrlProvider = null
            return
        }
        engine.reconnectUrlProvider =
            ReconnectUrlProvider {
                runCatching { host.resolve(source, channel.streamUrl) }
                    .onFailure { android.util.Log.w(ENGINE_TAG, "stalker reconnect re-resolve failed '${channel.name}'", it) }
                    .getOrNull()
            }
    }

    private fun request(
        channel: ChannelEntity,
        source: SourceEntity?,
        captions: List<RutubeCaption> = emptyList(),
    ) = LiveRequest(
        meta = host.meta(channel),
        userAgent = source?.userAgent,
        prerollSecs = source?.livePrerollSecs?.takeIf { it >= 0 },
        liveBuffer = source?.liveLatencyMode?.let { mode ->
            LiveBuffer.Override(LiveBuffer.effectiveSeconds(LiveLatency.fromName(mode), source.liveLatencyCustomSecs))
        },
        httpHeaders = SourceOverrides.headersWithReferer(channel.httpHeaders, source),
        drmConfig = channel.drmConfig,
        manifestType = channel.manifestType,
        directSource = channel.directSource,
        captions = captions,
    )

    /** Routing decisions go to logcat unconditionally (`adb logcat -s LiveEngine`) and to the
     *  diagnostics ring. Channel names only — never a stream URL. */
    private fun engineLog(message: String) {
        android.util.Log.i(ENGINE_TAG, message)
        LiveDiagnosticsLog.event("engine: $message")
    }

    /**
     * The [Host] the app uses: the settings store, the playlist row and Stalker minting through
     * [StreamUrlResolver]. [label] is the line under the channel name (the television's "#123");
     * [sourceLookup] lets the television read its warm playlist map.
     */
    class CoreHost(
        private val context: Context,
        private val settings: SettingsRepository,
        private val resolver: StreamUrlResolver,
        private val label: (ChannelEntity) -> String? = { null },
        private val sourceLookup: (suspend (Long) -> SourceEntity?)? = null,
        /** Core's `DownloadWatchGuard.holdsOnlyStream`; see [Host.downloadHoldsOnlyStream]. */
        private val downloadHolds: (Long) -> Boolean = { false },
    ) : Host {
        override suspend fun sourceOf(sourceId: Long): SourceEntity? =
            sourceLookup?.invoke(sourceId) ?: tv.own.owntv.core.repository.BuiltInSource.current

        override fun needsResolve(source: SourceEntity?): Boolean = resolver.needsResolve(source)

        override fun downloadHoldsOnlyStream(sourceId: Long): Boolean = downloadHolds(sourceId)

        override suspend fun resolve(source: SourceEntity, cmd: String): String? =
            runCatching { resolver.resolve(source, cmd) }
                .onFailure { android.util.Log.w(ENGINE_TAG, "stalker resolve failed", it) }
                .getOrNull()

        override suspend fun captions(source: SourceEntity?, cmd: String): List<RutubeCaption> =
            runCatching { resolver.resolveCaptions(source, cmd) }
                .onFailure { android.util.Log.w(ENGINE_TAG, "captions resolve failed", it) }
                .getOrDefault(emptyList())

        // Waits for the settings snapshot, so a tune started at a cold start — Last-channel autoplay —
        // is budgeted by the stored settings, never by defaults.
        override suspend fun globalBudgetSecs(): Int = PlaybackSettings.await(settings).liveTuneTimeoutSecs

        override fun meta(channel: ChannelEntity): MediaMeta = MediaMeta(
            title = channel.name,
            subtitle = label(channel),
            logoUrl = channel.logoUrl,
            contentKey = contentKey(channel.remoteId),
        )

        override fun recordLadderEvent(reason: PlayerFailureReason, detail: String) =
            PlaybackErrorLog.event(
                context = context,
                engine = "ExoPlayer",
                live = true,
                reason = reason,
                detail = detail,
            )
    }

    companion object {
        /** Tag for the engine decisions, so a support log can be filtered to just them. */
        const val ENGINE_TAG = "LiveEngine"
    }
}
