package tv.own.owntv.player

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-provider live-stream quirks learned at runtime by the live engine.
 *
 * Everything here is **in-memory for the session only** — so a provider that fixes its panel is back
 * to stock behaviour after the next app start. Keyed by `host:port` so a
 * lesson learned on one channel applies immediately to every other channel of the same panel
 * (these faults are panel-wide, not per-channel), except the format lessons, which are keyed to the
 * one channel that proved them.
 *
 * Tracked here:
 *
 *  1. **`.ts` that is really HLS.** Some Xtream panels advertise `/live/user/pass/ID.ts` but
 *     HTTP-redirect it to an `.m3u8` manifest. ExoPlayer picks its media source from the URL
 *     *before* that redirect and hands a text manifest to the progressive extractor
 *     (`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`). Once the engine has seen the redirect land on a
 *     manifest it remembers it here, and later loads of that channel (or panel) open as HLS directly.
 *     The DASH redirect is remembered the same way.
 *
 *  2. **A panel that allows only one session at a time.** It answers the second connection with the
 *     non-standard `458` (or a `407` with no proxy in play). The stream is fine; *we* — or a download,
 *     or the preview pane next to a full-screen channel — are the second client. Panel-wide, because
 *     the limit is on the account, not the channel.
 *
 *  3. **A panel that blocklists the default User-Agent.** Some hosts sit behind a WAF that refuses
 *     player identities by name. Traced on a Cloudflare-fronted panel that answers every request
 *     carrying `VLC/3.0.20 LibVLC/3.0.20` with a `403` challenge page ("Just a moment…") and serves the
 *     identical URL `200` to a neutral UA. Once the engine has been refused this way we remember it
 *     here, so every other channel on the panel opens under
 *     [tv.own.owntv.core.network.HttpClient.FALLBACK_USER_AGENT] first time instead of repeating the
 *     failure.
 *
 * Plus the per-channel format lessons the fallback ladder learns (a `.m3u8` sibling that never
 * plays for this channel, a stream whose pre-buffer threshold it can never satisfy) and the panel's
 * own refusal wording, kept verbatim for the error screen.
 */
object LiveStreamQuirks {

    /**
     * How many distinct live segments a provider must refuse before we stop trying on this load.
     *
     * Two, not one: a single 403 can be a genuine one-off (a segment rolled out of the window mid-flight),
     * and the load-error policy already absorbs that with one quick retry. Two *different* segments
     * refused in the same load is the signature of a URL-signing scheme Media3 structurally cannot
     * satisfy — it can only re-issue the identical URL it resolved from the playlist snapshot, so once
     * a token has aged out the channel can never recover; every further attempt just costs the user
     * another dead spinner. At that count the engine flags the load, the watchdog hands the tune to the
     * ladder, and a panel-wide lesson is recorded for the support log.
     */
    const val REFUSALS_BEFORE_HANDOFF = 2

    /** HTTP statuses a panel uses to refuse a segment outright. Never worth hammering the same URL for. */
    fun isEdgeRefusal(responseCode: Int): Boolean =
        responseCode == 403 || responseCode == 404 || responseCode == 410

    /**
     * The non-standard status a panel returns when the account's one allowed session is already in use.
     *
     * Traced on a panel whose edge answers `458` with an empty `text/html` body and no redirect, while a
     * permitted request is redirected through to the origin. It is not an HTTP standard code — Xtream
     * panels invent codes in this range for "max connections" — so it is matched exactly rather than by
     * class, and it means something very different from [isEdgeRefusal]: the stream is fine, *we* are the
     * second client.
     *
     * `407` joins it for the same reason. A panel fronted by a gateway answers the *reconnect* with
     * "Proxy Authentication Required" while the dying connection is still counted against the
     * account, even though no proxy is in play — traced on a television whose channel had played for
     * twenty seconds with zero dropped frames, then burned all eight reconnects on `407` in six
     * seconds and gave up. The stream was never the problem: the previous socket had not finished
     * closing. Waiting once is the answer here exactly as it is for `458`.
     */
    fun isSessionLimit(responseCode: Int): Boolean =
        responseCode == 458 || responseCode == PlayerErrors.HTTP_PROXY_AUTH

    /**
     * Statuses a WAF uses to refuse a request on *who is asking* rather than what was asked for — worth
     * one retry under a different identity before the stream is written off.
     *
     * `403` is the Cloudflare managed-challenge answer traced on the panel in the quirk-3 note above;
     * `503` is the same product's "checking your browser" variant. Only ever consulted **before the
     * first frame** (see the caller): once a channel has played, a mid-stream `403` is a stale segment
     * token, which is [isEdgeRefusal]'s business and a new UA cannot help it.
     */
    fun isIdentityRefusal(responseCode: Int): Boolean = responseCode == 403 || responseCode == 503

    /** `host:port` of [url], lowercased; the whole URL when it can't be parsed (still a stable key). */
    fun hostKey(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringAfterLast('@')
        return authority.ifBlank { url }.lowercase()
    }

    // --- learned state ---------------------------------------------------------------------------

    /**
     * The most URL-keyed lessons any one of the sets below will hold.
     *
     * Host-keyed sets are naturally bounded — a user has a handful of panels — but a URL-keyed one grows
     * with every channel ever tuned, and nothing here ever evicted. A long zapping session on a big
     * playlist is the case that matters. 512 is far beyond any real session: reaching it means learning
     * a format lesson about five hundred DIFFERENT streams without restarting the app, at which point
     * the oldest are of no further interest anyway.
     */
    private const val MAX_URL_LESSONS = 512

    /**
     * A URL-keyed lesson set that forgets its oldest entries rather than growing without limit.
     *
     * Insertion-ordered, so eviction drops the stream longest ago written off. A lesson recorded during
     * the tune currently on screen is therefore the newest entry in the set and cannot be evicted unless
     * five hundred further streams are written off before the channel is consulted again — which cannot
     * happen inside one tune.
     */
    private fun urlLessonSet(): MutableSet<String> = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(
            object : LinkedHashMap<String, Boolean>(64, 0.75f) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) =
                    size > MAX_URL_LESSONS
            },
        ),
    )

    private val hlsRedirectHosts = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val hlsRedirectStreams = urlLessonSet()
    private val mixedHlsHosts = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val dashRedirectHosts = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val singleSessionHosts = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val noHlsVariantStreams = urlLessonSet()
    private val prerollDefeatedStreams = urlLessonSet()
    private val uaBlockingHosts = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val providerMessages = ConcurrentHashMap<String, Pair<Int, String>>()

    /**
     * Record that [url] — and, unless its panel is known to be mixed, every channel on its host — serves
     * HLS even though the advertised URL says `.ts`.
     *
     * Two tiers. The channel itself is always remembered, so on a mixed panel the channels that do
     * redirect still open as HLS first time instead of each paying a failed TS open again. The host is
     * remembered only while nothing has proved the panel mixed.
     */
    fun rememberHlsRedirect(url: String) {
        hlsRedirectStreams += url
        val key = hostKey(url)
        if (key !in mixedHlsHosts) hlsRedirectHosts += key
    }

    /** Whether [url] should open as HLS on what was learned: its own redirect, or its panel's. */
    fun isKnownHls(url: String): Boolean = url in hlsRedirectStreams || hostKey(url) in hlsRedirectHosts

    /**
     * [url] was proved to be plain TS — it played as TS after the learned HLS route failed — so its
     * panel serves plain TS on some channels and redirects others to a manifest, and the host-wide lesson
     * is wrong there: drop it and never learn it again this session. Without this one redirecting
     * channel sent every plain one through a failed HLS open and a failed `.m3u8` retry before the TS
     * rung played it. The redirecting channels keep their own lesson.
     */
    fun forgetHlsRedirect(url: String) {
        val key = hostKey(url)
        mixedHlsHosts += key
        hlsRedirectHosts -= key
        hlsRedirectStreams -= url
    }

    /**
     * Whether a first frame proves the panel mixed: only when this load deliberately ignored the learned
     * HLS route and played on the progressive (plain TS) route. A retry that never shows a picture — a
     * dead channel, an HTML error page — proves nothing about the panel and must not mark it.
     */
    fun firstFrameProvesMixed(lessonIgnored: Boolean, progressiveRoute: Boolean): Boolean =
        lessonIgnored && progressiveRoute

    /**
     * Whether a failed HLS open (one chosen because of a learned lesson) suggests *this* channel is plain
     * TS, so it is worth reopening as TS. Only a response that arrived and would not parse says that. An
     * HTTP status says nothing about the format: an offline channel on a redirecting panel 404s on its
     * manifest whatever it is asked for, and taking that as "plain TS" put the panel on the mixed list for
     * the rest of the session — after which every redirected `.ts` on it failed its learned HLS open on
     * every channel. Even then the panel is marked mixed only once the TS reopen shows a picture
     * ([firstFrameProvesMixed]).
     */
    fun disprovesHlsLesson(isFormatFailure: Boolean, httpStatus: Int?): Boolean =
        isFormatFailure && httpStatus == null

    /**
     * Record that [url]'s host serves DASH manifests from URLs that do not say `.mpd` (v43).
     *
     * The DASH twin of [rememberHlsRedirect], and it matters for the same reason: the traced proxy
     * publishes every channel as `…/live/mpd/{id}` and only redirects to `…/render.mpd` once asked, so
     * without a panel-wide lesson EVERY channel on it pays its own failed progressive attempt and
     * retry before playing.
     */
    fun rememberDashRedirect(url: String) { dashRedirectHosts += hostKey(url) }

    fun isKnownDashHost(url: String): Boolean = hostKey(url) in dashRedirectHosts

    /**
     * True when [url] itself asks for HLS — it ends in `.m3u8` — ignoring anything learned about its
     * panel.
     *
     * This is the test behind whether an HLS *discovery* is worth remembering: a URL we deliberately
     * requested as `.m3u8` proves nothing about what the panel does with the `.ts` endpoint, and
     * recording it as a redirect poisons every other channel there.
     */
    fun isExplicitHlsUrl(url: String): Boolean =
        url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    /**
     * The `.ts` ⇄ `.m3u8` sibling of [url], or null when there is no extension to swap.
     *
     * The extension is tested on the PATH only, and any query/fragment is carried over untouched. An
     * M3U live URL like `…/stream-output.m3u8?mode=hls` ends in a *parameter* rather than an extension,
     * so testing the whole string skips the swap entirely; and the provider's own `mode` is usually
     * required, so dropping it yields a 400 instead of the sibling.
     */
    fun alternateFormatUrl(url: String): String? {
        val cut = url.indexOfFirst { it == '?' || it == '#' }.takeIf { it >= 0 } ?: url.length
        val path = url.substring(0, cut)
        val suffix = url.substring(cut)
        val from = when {
            path.endsWith(".m3u8", ignoreCase = true) -> ".m3u8"
            path.endsWith(".ts", ignoreCase = true) -> ".ts"
            else -> return null
        }
        val to = if (from == ".m3u8") ".ts" else ".m3u8"
        return path.dropLast(from.length) + to + suffix
    }

    /**
     * Statuses where the provider is refusing the *request* rather than describing a broken stream.
     *
     * Guards the format swap above, which can only ever fix a *container* problem. Traced on a panel
     * that answers `429 "Channel limit has been reached"`: the ladder read that as a format failure,
     * went off to an invented `.ts` URL that 404s, retried the invented URL six times over ~45 s and
     * ended on an error screen blaming the channel — while the original URL worked the moment the
     * account's other stream closed. A different file extension cannot answer any of these.
     */
    fun isRequestRefusal(responseCode: Int): Boolean =
        responseCode == 401 || responseCode == 402 || responseCode == 403 || responseCode == 429 ||
            responseCode == 503 || responseCode == 509 || isSessionLimit(responseCode)

    /**
     * Record that this panel allows only one session at a time (it answered [isSessionLimit]).
     *
     * On such a panel nothing may hold two connections at once: whichever one holds the session keeps
     * playing and the other is locked out until the holder's socket is really gone. That is why the
     * preview pane stands down while full screen holds the stream, why a minted Stalker link is
     * retired carefully, and why a refused `458` waits and re-asks once instead of failing the channel.
     * Panel-wide, because the limit is on the *account*, not the channel.
     */
    fun rememberSessionLimit(url: String) { singleSessionHosts += hostKey(url) }

    fun isSingleSession(url: String): Boolean = hostKey(url) in singleSessionHosts

    /**
     * Record that this channel has no `.m3u8` sibling the live engine can play, so "Prefer HLS" must be
     * ignored for it.
     *
     * The playlist setting rewrites every Xtream `.ts` to `.m3u8` blindly, but a panel does not
     * necessarily remux *every* channel: the odd one answers its `.m3u8` with a 404, an empty body, or a
     * playlist whose segments never arrive — and the channel that played perfectly in TS mode turns into
     * a black screen or a spinner that never resolves. The fix is to fall back to the `.ts` the panel
     * does serve.
     *
     * Traced on a 4K channel whose `.m3u8` is served perfectly well, while the engine's audio renderer
     * never produces a sample from it (`audio=false` with the buffer full).
     *
     * Keyed by the **channel's own `.ts` URL**, and never widened to the panel: on the very same provider
     * all the other channels keep their HLS variant, which is the reason the user turned the setting on.
     * Session-only, so a panel that finishes remuxing is back to HLS after the next app start.
     *
     * **A panel-wide verdict used to exist here and was removed deliberately.** Three channels failing
     * their `.m3u8` wrote off the whole provider for the session, and the failures that reached this
     * function were not necessarily about format at all — an account-busy lockout (HTTP 458) walked three
     * channels onto their `.ts` rung in under two minutes and cost every remaining channel its HLS start
     * for the rest of the session. One channel's evidence now only ever condemns that channel.
     */
    fun rememberNoHlsVariant(tsUrl: String) {
        noHlsVariantStreams += tsUrl
    }

    fun lacksHlsVariant(tsUrl: String): Boolean = tsUrl in noHlsVariantStreams

    /**
     * Record that this stream can never satisfy the "Pre-buffer" threshold, so it must open without one.
     *
     * ExoPlayer's `DefaultLoadControl` starts (and *resumes*) playback once **either** the requested
     * amount of media or the byte cap is reached. On a very high-bitrate feed — a 4K live channel is the
     * traced case — the byte cap is hit long before 10 s of media exists, so the time threshold is
     * unreachable: playback starts on the byte rule, drains a few frames, drops back under the cap, and
     * re-buffers immediately. The result is a `READY`/`BUFFERING` oscillation several times a second that
     * looks exactly like a frozen picture with a stuck spinner — and it defeats every stall watchdog,
     * because each `READY` cancels them before they can fire.
     *
     * Keyed by the **whole URL**: it is a property of one feed's bitrate, not of the panel, and the same
     * provider's HD channels satisfy the pre-roll fine. Session-only.
     */
    fun rememberPrerollDefeated(url: String) { prerollDefeatedStreams += url }

    fun defeatsPreroll(url: String): Boolean = url in prerollDefeatedStreams

    /**
     * Record that this panel refuses [tv.own.owntv.core.network.HttpClient.DEFAULT_USER_AGENT], so every
     * later request to it — any channel — starts on the fallback identity.
     *
     * Panel-wide, because a WAF rule is configured for the site, not per stream. Session-only like the
     * rest: a provider that drops the rule is back to the default identity after the next app start,
     * which matters because the default is the one most panels actually want.
     *
     * Only ever recorded when the user has NOT configured a User-Agent for the source. An explicit
     * setting is the user's decision and is never second-guessed or overwritten.
     */
    fun rememberBlocksDefaultUserAgent(url: String) { uaBlockingHosts += hostKey(url) }

    fun blocksDefaultUserAgent(url: String): Boolean = hostKey(url) in uaBlockingHosts

    /**
     * Keep the panel's own explanation of a refusal, so an error screen can quote it verbatim.
     *
     * "Channel limit has been reached. Stop one of your active streams before opening a new channel."
     * says in one line what a status code can only hint at, and it is the difference between the user
     * closing the TV in the other room and the user assuming the channel is dead. Captured from the
     * response body the engine reads; kept panel-wide so the same status on any later channel there can
     * quote it too.
     *
     * Stored with the status it explained: a stale "channel limit" line quoted under a later 404 would
     * be worse than no message at all.
     */
    fun rememberProviderMessage(url: String, responseCode: Int, message: String) {
        providerMessages[hostKey(url)] = responseCode to message
    }

    /** The panel's own words for [responseCode], if it gave any this session; null otherwise. */
    fun providerMessage(url: String, responseCode: Int): String? =
        providerMessages[hostKey(url)]?.takeIf { it.first == responseCode }?.second

    /** Test hook — clears everything. */
    internal fun clearForTest() {
        hlsRedirectHosts.clear(); hlsRedirectStreams.clear(); mixedHlsHosts.clear(); singleSessionHosts.clear()
        noHlsVariantStreams.clear(); prerollDefeatedStreams.clear()
        dashRedirectHosts.clear()
        uaBlockingHosts.clear(); providerMessages.clear()
    }
}
