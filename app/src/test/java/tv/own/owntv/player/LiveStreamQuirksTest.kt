package tv.own.owntv.player

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The provider-quirk decisions behind the live 403/black-screen fix. Everything here is pure — the
 * store is session-only in-memory state, so each test starts from a clean slate.
 */
class LiveStreamQuirksTest {

    @Before fun reset() = LiveStreamQuirks.clearForTest()
    @After fun tearDown() = LiveStreamQuirks.clearForTest()

    // --- ".ts that is really HLS" ------------------------------------------------------------------

    @Test
    fun `a learned redirect applies to the whole panel, not just the channel that taught us`() {
        val taught = "http://panel.example:80/live/user/pass/136875.ts"
        val sibling = "http://panel.example:80/live/user/pass/999.ts"
        assertFalse(LiveStreamQuirks.isKnownHls(sibling))
        LiveStreamQuirks.rememberHlsRedirect(taught)
        assertTrue(LiveStreamQuirks.isKnownHls(sibling))
    }

    /**
     * The guard behind "Prefer HLS off played nothing on ExoPlayer": a channel tuned as `.m3u8` because
     * the user asked for HLS must never be mistaken for a panel that redirects its `.ts` to a manifest.
     * Learning that lesson is host-wide, so one such channel used to force every plain `.ts` on the panel
     * into the HLS parser, which fails on the first bytes.
     */
    @Test
    fun `an explicitly requested m3u8 is not evidence that the panel redirects its ts`() {
        val hls = "http://panel.example:80/live/user/pass/136875.m3u8"
        assertTrue(LiveStreamQuirks.isExplicitHlsUrl(hls))
        assertTrue(LiveStreamQuirks.isExplicitHlsUrl("$hls?token=abc"))
        assertFalse(LiveStreamQuirks.isExplicitHlsUrl("http://panel.example:80/live/user/pass/136875.ts"))
    }

    @Test
    fun `a learned host does not make a ts URL count as explicitly HLS`() {
        val ts = "http://panel.example:80/live/user/pass/136875.ts"
        LiveStreamQuirks.rememberHlsRedirect(ts)
        assertTrue(LiveStreamQuirks.isKnownHls(ts))
        assertFalse(LiveStreamQuirks.isExplicitHlsUrl(ts))
    }

    /**
     * Bug 8: a panel where a few channels redirect `.ts` to a manifest and the rest serve plain TS. Once
     * the host lesson sends a plain channel into the HLS parser and it fails, the lesson is wrong for this
     * panel — it must stop applying, and a later redirecting channel must not teach it back.
     */
    @Test
    fun `a host lesson disproved by a plain ts channel is dropped and not relearned`() {
        val redirecting = "http://panel.example:80/live/user/pass/6082.ts"
        val plain = "http://panel.example:80/live/user/pass/5331.ts"
        LiveStreamQuirks.rememberHlsRedirect(redirecting)
        assertTrue(LiveStreamQuirks.isKnownHls(plain))

        LiveStreamQuirks.forgetHlsRedirect(plain)
        assertFalse(LiveStreamQuirks.isKnownHls(plain))

        LiveStreamQuirks.rememberHlsRedirect(redirecting)
        assertFalse(LiveStreamQuirks.isKnownHls(plain))
    }

    @Test
    fun `on a mixed panel each redirecting channel keeps its own lesson`() {
        val redirecting = "http://panel.example:80/live/user/pass/6082.ts"
        val plain = "http://panel.example:80/live/user/pass/5331.ts"
        val other = "http://panel.example:80/live/user/pass/7000.ts"
        LiveStreamQuirks.rememberHlsRedirect(redirecting)
        LiveStreamQuirks.forgetHlsRedirect(plain)
        // The channel that taught us still opens as HLS first time; the plain one and the untried one don't.
        assertTrue(LiveStreamQuirks.isKnownHls(redirecting))
        assertFalse(LiveStreamQuirks.isKnownHls(plain))
        assertFalse(LiveStreamQuirks.isKnownHls(other))
        // A redirecting channel discovered after the panel was proved mixed is remembered on its own.
        LiveStreamQuirks.rememberHlsRedirect(other)
        assertTrue(LiveStreamQuirks.isKnownHls(other))
        assertFalse(LiveStreamQuirks.isKnownHls(plain))
    }

    @Test
    fun `a channel proved plain loses its own lesson too`() {
        val url = "http://panel.example:80/live/user/pass/6082.ts"
        LiveStreamQuirks.rememberHlsRedirect(url)
        LiveStreamQuirks.forgetHlsRedirect(url)
        assertFalse(LiveStreamQuirks.isKnownHls(url))
    }

    @Test
    fun `per-channel lessons are bounded and forget the oldest`() {
        LiveStreamQuirks.forgetHlsRedirect("http://panel.example:80/live/u/p/0.ts") // mixed: channel tier only
        val first = "http://panel.example:80/live/u/p/1.ts"
        LiveStreamQuirks.rememberHlsRedirect(first)
        for (i in 2..513) LiveStreamQuirks.rememberHlsRedirect("http://panel.example:80/live/u/p/$i.ts")
        assertFalse(LiveStreamQuirks.isKnownHls(first))
        assertTrue(LiveStreamQuirks.isKnownHls("http://panel.example:80/live/u/p/513.ts"))
    }

    @Test
    fun `only a picture on the plain TS route proves the panel mixed`() {
        assertTrue(LiveStreamQuirks.firstFrameProvesMixed(lessonIgnored = true, progressiveRoute = true))
        // A normal tune that shows a picture proves nothing about the lesson.
        assertFalse(LiveStreamQuirks.firstFrameProvesMixed(lessonIgnored = false, progressiveRoute = true))
        // A load that ended up on HLS anyway (declared, forced) is not the TS retry.
        assertFalse(LiveStreamQuirks.firstFrameProvesMixed(lessonIgnored = true, progressiveRoute = false))
        assertFalse(LiveStreamQuirks.firstFrameProvesMixed(lessonIgnored = false, progressiveRoute = false))
    }

    @Test
    fun `only a response that would not parse disproves the host lesson`() {
        // The manifest parser choked on what came back: this channel really is plain TS.
        assertTrue(LiveStreamQuirks.disprovesHlsLesson(isFormatFailure = true, httpStatus = null))
        // Not a format failure at all (a timeout, a decoder error): says nothing either way.
        assertFalse(LiveStreamQuirks.disprovesHlsLesson(isFormatFailure = false, httpStatus = null))
    }

    @Test
    fun `an offline channel's 404 leaves a redirecting panel's lesson alone`() {
        // The traced case: a dead channel on a redirecting panel 404s on its manifest. Counting that as
        // "plain TS" put the panel on the mixed list, and every redirecting channel after it paid a
        // failed plain open before its learned HLS rung.
        assertFalse(LiveStreamQuirks.disprovesHlsLesson(isFormatFailure = true, httpStatus = 404))
        assertFalse(LiveStreamQuirks.disprovesHlsLesson(isFormatFailure = true, httpStatus = 415))
    }

    @Test
    fun `the DASH redirect lesson is panel-wide too`() {
        val taught = "http://proxy.example/live/mpd/173"
        assertFalse(LiveStreamQuirks.isKnownDashHost("http://proxy.example/live/mpd/174"))
        LiveStreamQuirks.rememberDashRedirect(taught)
        assertTrue(LiveStreamQuirks.isKnownDashHost("http://proxy.example/live/mpd/174"))
        assertFalse(LiveStreamQuirks.isKnownDashHost("http://other.example/live/mpd/174"))
    }

    @Test
    fun `a different provider is unaffected by what one panel taught us`() {
        LiveStreamQuirks.rememberHlsRedirect("http://panel.example:80/live/u/p/1.ts")
        assertFalse(LiveStreamQuirks.isKnownHls("http://other.example:80/live/u/p/1.ts"))
    }

    @Test
    fun `an m3u8 URL is explicitly HLS without anyone having to learn it`() {
        assertTrue(LiveStreamQuirks.isExplicitHlsUrl("http://panel.example/live/u/p/1.m3u8?token=abc"))
        assertFalse(LiveStreamQuirks.isExplicitHlsUrl("http://panel.example/live/u/p/1.ts"))
    }

    @Test
    fun `the host key ignores credentials, path and query but keeps the port`() {
        assertEquals("panel.example:2086", LiveStreamQuirks.hostKey("http://panel.example:2086/live/a/b/1.ts?x=1"))
        assertEquals("panel.example:2086", LiveStreamQuirks.hostKey("http://u:p@PANEL.example:2086/other.m3u8"))
        // A different port is a different origin — the traced panel's CDN and origin differ exactly here.
        assertTrue(
            LiveStreamQuirks.hostKey("http://panel.example:80/a.ts") !=
                LiveStreamQuirks.hostKey("http://panel.example:2086/a.ts"),
        )
    }

    @Test
    fun `the format sibling keeps the signed query and refuses URLs without an extension`() {
        assertEquals(
            "http://panel.example/live/u/p/1.m3u8?token=abc&expires=1",
            LiveStreamQuirks.alternateFormatUrl("http://panel.example/live/u/p/1.ts?token=abc&expires=1"),
        )
        assertEquals(
            "http://panel.example/live/u/p/1.ts",
            LiveStreamQuirks.alternateFormatUrl("http://panel.example/live/u/p/1.m3u8"),
        )
        assertNull(LiveStreamQuirks.alternateFormatUrl("http://panel.example/live/u/p/1"))
        // The extension is tested on the PATH only, so a `.ts` sitting in a query token never drives
        // the swap: a URL like `…/stream-output?mode=hls.ts` has no extension to exchange.
        assertNull(LiveStreamQuirks.alternateFormatUrl("http://panel.example/live/u/p/stream-output?mode=hls.ts"))
    }

    // --- refusals -----------------------------------------------------------------------------------

    @Test
    fun `only a refusal status counts as the provider refusing us — a server fault must not`() {
        assertTrue(LiveStreamQuirks.isEdgeRefusal(403))
        assertTrue(LiveStreamQuirks.isEdgeRefusal(404))
        assertTrue(LiveStreamQuirks.isEdgeRefusal(410))
        assertFalse(LiveStreamQuirks.isEdgeRefusal(500))
        assertFalse(LiveStreamQuirks.isEdgeRefusal(502))
        assertFalse(LiveStreamQuirks.isEdgeRefusal(200))
    }

    @Test
    fun `one refusal is not enough to give up on the load`() {
        // A single 403 can be a segment that rolled out of the window mid-flight; the load-error policy
        // absorbs that with one quick retry. Only a second, *different* refused segment is the signature.
        assertTrue(LiveStreamQuirks.REFUSALS_BEFORE_HANDOFF > 1)
    }

    @Test
    fun `a WAF refusal is worth a different identity, a server fault is not`() {
        assertTrue(LiveStreamQuirks.isIdentityRefusal(403))
        assertTrue(LiveStreamQuirks.isIdentityRefusal(503))
        assertFalse(LiveStreamQuirks.isIdentityRefusal(404))
        assertFalse(LiveStreamQuirks.isIdentityRefusal(500))
    }

    @Test
    fun `a request refusal says nothing about the stream format`() {
        // The panel refusing the *request* — a busy 458, a rate limit, a 401 — cannot be answered by a
        // different file extension; only a container problem can.
        for (status in listOf(401, 402, 403, 429, 503, 509, 458)) {
            assertTrue("$status", LiveStreamQuirks.isRequestRefusal(status))
        }
        assertFalse(LiveStreamQuirks.isRequestRefusal(500))
        assertFalse(LiveStreamQuirks.isRequestRefusal(404))
    }

    @Test
    fun `a panel blocklisting the default User-Agent is remembered panel-wide`() {
        val taught = "http://cf.example:2086/live/u/p/1.m3u8"
        assertFalse(LiveStreamQuirks.blocksDefaultUserAgent("http://cf.example:2086/live/u/p/2.ts"))
        LiveStreamQuirks.rememberBlocksDefaultUserAgent(taught)
        assertTrue(LiveStreamQuirks.blocksDefaultUserAgent("http://cf.example:2086/live/u/p/2.ts"))
        assertFalse(LiveStreamQuirks.blocksDefaultUserAgent("http://good.example:2086/live/u/p/2.ts"))
    }

    @Test
    fun `the panel's own refusal wording is kept per status`() {
        val url = "http://limit.example:80/live/u/p/1.ts"
        LiveStreamQuirks.rememberProviderMessage(url, 429, "Channel limit has been reached")
        assertEquals("Channel limit has been reached", LiveStreamQuirks.providerMessage(url, 429))
        // Quoting the "channel limit" line under a later 404 would be worse than no message at all.
        assertNull(LiveStreamQuirks.providerMessage(url, 404))
        assertNull(LiveStreamQuirks.providerMessage("http://other.example/live/u/p/1.ts", 429))
    }

    // --- the HLS lesson never leaves the channel it was learned on ---------------------------------

    @Test
    fun `one channel without an HLS variant says nothing about the panel`() {
        // "Prefer HLS" is a per-playlist guess, and a single channel can genuinely be TS-only on a panel
        // that otherwise serves HLS fine. Condemning the whole panel on one miss would cost every other
        // channel its faster HLS start.
        val taught = "http://panel.example:80/live/u/p/1.ts"
        val sibling = "http://panel.example:80/live/u/p/2.ts"
        LiveStreamQuirks.rememberNoHlsVariant(taught)
        assertTrue(LiveStreamQuirks.lacksHlsVariant(taught))
        assertFalse(LiveStreamQuirks.lacksHlsVariant(sibling))
    }

    @Test
    fun `no number of failing channels ever writes off the whole panel`() {
        // The panel-wide verdict was removed deliberately. It condemned a provider for the session on
        // three channels' evidence, and the failures that reached it were not necessarily about format:
        // an account-busy lockout (HTTP 458) walked three channels onto their `.ts` rung in under two
        // minutes and cost every remaining channel its HLS start until the app was restarted.
        val host = "http://nohls.example:8080/live/u/p/"
        repeat(10) { LiveStreamQuirks.rememberNoHlsVariant("$host$it.ts") }
        assertFalse(LiveStreamQuirks.lacksHlsVariant("${host}999.ts"))
        // Each taught channel still keeps its own lesson.
        assertTrue(LiveStreamQuirks.lacksHlsVariant("${host}3.ts"))
    }

    @Test
    fun `a URL-keyed lesson set forgets its oldest entries instead of growing forever`() {
        // These sets are keyed by stream URL, so they grew with every channel ever tuned and never
        // evicted anything. Host-keyed sets are naturally bounded and are deliberately left alone.
        val recent = "http://bound.example:80/live/u/p/99999.ts"
        LiveStreamQuirks.rememberNoHlsVariant(recent)
        assertTrue(LiveStreamQuirks.lacksHlsVariant(recent))

        // Well past the cap, all on the same host so nothing else about the panel is being tested.
        repeat(2_000) { LiveStreamQuirks.rememberNoHlsVariant("http://bound.example:80/live/u/p/$it.ts") }

        // The oldest entries are gone…
        assertFalse(LiveStreamQuirks.lacksHlsVariant(recent))
        assertFalse(LiveStreamQuirks.lacksHlsVariant("http://bound.example:80/live/u/p/0.ts"))
        // …and the newest are still answered correctly, which is the only property that matters: a
        // lesson recorded during the tune on screen is the newest entry there is.
        assertTrue(LiveStreamQuirks.lacksHlsVariant("http://bound.example:80/live/u/p/1999.ts"))
        assertTrue(LiveStreamQuirks.lacksHlsVariant("http://bound.example:80/live/u/p/1900.ts"))
    }

    // --- "the account only gets one session" -------------------------------------------------------

    @Test
    fun `the session-limit status is matched exactly, not as a class of refusals`() {
        // 458 means "the stream is fine, you are the second client" — the opposite conclusion to a 403,
        // which is why it must not be lumped in with the edge refusals that end a load.
        assertTrue(LiveStreamQuirks.isSessionLimit(458))
        assertFalse(LiveStreamQuirks.isSessionLimit(403))
        assertFalse(LiveStreamQuirks.isSessionLimit(459))
        assertFalse(LiveStreamQuirks.isSessionLimit(200))
        assertFalse(LiveStreamQuirks.isEdgeRefusal(458))
    }

    @Test
    fun `a one-session panel is remembered for the whole account, not one channel`() {
        val taught = "http://cf.example:80/live/u/p/440438.m3u8"
        val sibling = "http://cf.example:80/live/u/p/1.ts"
        assertFalse(LiveStreamQuirks.isSingleSession(sibling))
        LiveStreamQuirks.rememberSessionLimit(taught)
        assertTrue(LiveStreamQuirks.isSingleSession(sibling))
        assertFalse(LiveStreamQuirks.isSingleSession("http://other.example:80/live/u/p/1.ts"))
        // Independent of the other quirks.
        assertFalse(LiveStreamQuirks.isKnownHls(sibling))
    }

    // --- "this stream can't meet the pre-roll" -----------------------------------------------------

    @Test
    fun `a pre-roll the stream can never satisfy is learned per stream, not per panel`() {
        // It is a property of one feed's bitrate: the same provider's HD channels satisfy it fine.
        val broken = "http://panel.example:80/live/u/p/4k.ts"
        val neighbour = "http://panel.example:80/live/u/p/sd.ts"
        LiveStreamQuirks.rememberPrerollDefeated(broken)
        assertTrue(LiveStreamQuirks.defeatsPreroll(broken))
        assertFalse(LiveStreamQuirks.defeatsPreroll(neighbour))
    }

    @Test
    fun `the wait around a session release is long enough to matter and short enough to feel instant`() {
        // The traced panel needed ~2 s after the previous stream's socket closed before it let us back in.
        assertTrue(LivePreviewEngine.SESSION_RELEASE_MS in 1_000L..5_000L)
    }
}
