package tv.own.owntv.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.player.LiveLadder.Rung

/**
 * The format fallback ladder for one live tune — the sequence the ExoPlayer engine walks when the
 * stream format it started on cannot play the channel. Both playback audits independently named this
 * the single highest-value test in the app: nothing else decides so much with so little visible
 * feedback, and its failure mode (a channel re-tuning the same format forever, or giving up before
 * the combination that works) is invisible in a log.
 *
 * The properties pinned here are the ones the ladder exists to guarantee:
 *  - each rung is spent at most once per tune, so the ladder always terminates;
 *  - the `.ts` rung is dropped when there is no `.m3u8`/`.ts` distinction to try, or when this
 *    session already learned this channel's `.m3u8` does not work for it;
 *  - a late failure from a superseded tune cannot advance the ladder of the current one.
 *
 * [LiveStreamQuirks] is process-global session state, so every test uses its own URLs.
 */
class LiveLadderTest {

    private fun ladderFor(url: String, hasHls: Boolean = true): LiveLadder =
        LiveLadder().also { runBlocking { it.arm(url) { hasHls } } }

    @Test
    fun `a tune tries the HLS format first and the plain TS endpoint only as a fallback`() {
        val ladder = ladderFor("http://a.test/live/1.ts")
        assertEquals(listOf(Rung.EXO_HLS, Rung.EXO_TS), ladder.plan)
    }

    @Test
    fun `the rung on screen is spent, so the first advance is a real change`() {
        val ladder = ladderFor("http://c.test/live/1.ts")
        assertTrue(ladder.isSpent(Rung.EXO_HLS))
        assertFalse(ladder.isSpent(Rung.EXO_TS))
        assertEquals(Rung.EXO_TS, ladder.advance())
    }

    @Test
    fun `every rung is spent at most once and the ladder then ends`() {
        // The whole safety property: a failure that re-tuned the same format could otherwise keep a
        // channel spinning forever.
        val ladder = ladderFor("http://d.test/live/1.ts")
        assertEquals(Rung.EXO_TS, ladder.advance())
        assertNull(ladder.advance())
        assertNull(ladder.advance()) // exhausted stays exhausted
    }

    @Test
    fun `with no HLS alternative the ladder is the plain attempt it always was`() {
        // No HLS preference, a portal cmd, or a URL already `.m3u8`: there is no second format
        // to try, and a rung the tune code would silently turn into the `.ts` one anyway would cost a
        // duplicate attempt before the error.
        val ladder = ladderFor("http://f.test/live/1.ts", hasHls = false)
        assertEquals(listOf(Rung.EXO_TS), ladder.plan)
        assertNull(ladder.advance())
    }

    @Test
    fun `a channel caught lacking its HLS variant arms with the plain rung only`() {
        // The lesson is keyed to this channel — no number of other channels' failures condemns a
        // panel-wide format, which is why the same host's neighbours still get both rungs.
        val url = "http://h.test/live/1.ts"
        LiveStreamQuirks.rememberNoHlsVariant(url)
        assertEquals(listOf(Rung.EXO_TS), ladderFor(url).plan)
        assertFalse(LiveStreamQuirks.lacksHlsVariant("http://h.test/live/2.ts"))
    }

    @Test
    fun `dropping to the TS rung records the format lesson`() {
        // So the NEXT tune of this channel skips the dead rung instead of paying for it again.
        val url = "http://j.test/live/1.ts"
        val ladder = ladderFor(url)
        assertFalse(LiveStreamQuirks.lacksHlsVariant(url))
        assertEquals(Rung.EXO_TS, ladder.advance())
        assertTrue(LiveStreamQuirks.lacksHlsVariant(url))
    }

    @Test
    fun `no lesson is recorded when there was no HLS rung to blame`() {
        // Nothing was swapped, so the `.ts` attempt proves nothing about a `.m3u8` that was never
        // tried — recording one here would poison the next tune of a channel whose HLS is fine.
        val url = "http://k.test/live/1.ts"
        val ladder = ladderFor(url, hasHls = false)
        assertNull(ladder.advance())
        assertFalse(LiveStreamQuirks.lacksHlsVariant(url))
    }

    @Test
    fun `a refused request teaches the ladder nothing about format`() {
        // The regression this exists for: a one-session panel (HTTP 458) refused ten channels opened
        // by scrolling the list. Three of them stepped onto their `.ts` rung during the lockout, each
        // wrote "this channel has no HLS", and the app stopped asking for HLS at all. A refusal is
        // not evidence about a stream's format — a different file extension cannot answer an
        // account-level refusal.
        val url = "http://o.test/live/1.ts"
        val ladder = ladderFor(url)
        ladder.advance(failureWasAboutFormat = false)
        assertFalse(LiveStreamQuirks.lacksHlsVariant(url))
        // The next tune therefore still gets both rungs — nothing was written off.
        runBlocking { ladder.arm(url) { true } }
        assertEquals(listOf(Rung.EXO_HLS, Rung.EXO_TS), ladder.plan)
    }

    @Test
    fun `a format failure still teaches`() {
        val url = "http://p.test/live/1.ts"
        val ladder = ladderFor(url)
        assertEquals(Rung.EXO_TS, ladder.advance(failureWasAboutFormat = true))
        assertTrue(LiveStreamQuirks.lacksHlsVariant(url))
    }

    @Test
    fun `a newer tune owns the ladder, so a late failure from the old one cannot advance it`() {
        val first = "http://l.test/live/1.ts"
        val second = "http://l.test/live/2.ts"
        val ladder = LiveLadder()
        runBlocking { ladder.arm(first) { true } }
        assertTrue(ladder.owns(first))

        runBlocking { ladder.arm(second) { true } }
        assertFalse(ladder.owns(first))
        assertTrue(ladder.owns(second))
    }

    @Test
    fun `re-arming forgets the rungs already climbed — a new tune is a new chance`() {
        // Including for a channel that ended the last tune on its final rung: the provider may simply
        // have been having a bad minute. Nothing is learned along the way here (the refusals do not
        // write a format lesson), so the second tune is identical to the first.
        val url = "http://m.test/live/1.ts"
        val ladder = ladderFor(url)
        assertEquals(Rung.EXO_TS, ladder.advance(failureWasAboutFormat = false))
        assertNull(ladder.advance(failureWasAboutFormat = false))

        runBlocking { ladder.arm(url) { true } }
        assertFalse(ladder.isSpent(Rung.EXO_TS))
        assertEquals(Rung.EXO_TS, ladder.advance(failureWasAboutFormat = false))
    }

    // ---- the whole-tune budget -------------------------------------------------------------------

    private fun budgetedLadder(url: String, budgetMs: Long = 30_000L, armedAtMs: Long = 1_000L) =
        LiveLadder().also { runBlocking { it.arm(url, budgetMs, armedAtMs) { true } } }

    @Test
    fun `a tune that has spent its budget climbs no further, while a rung is still left`() {
        // The audit item: every rung had its own timeout and nothing bounded their sum — a removed
        // channel spun through the ladder for the best part of two minutes of black screen.
        val ladder = budgetedLadder("http://q.test/live/1.ts")
        assertTrue(ladder.expired(31_000L))
        assertNull(ladder.advance(nowMs = 31_000L))
        // The remaining rung was still unspent — the budget, not exhaustion, is what stopped it.
        assertFalse(ladder.isSpent(Rung.EXO_TS))
    }

    @Test
    fun `a restarted deadline lets a spent tune climb to its next rung`() {
        // ExoPlayer played this channel, then found its content unplayable (not a connection
        // problem). The opening budget is long spent by then, which would refuse the next rung.
        val ladder = budgetedLadder("http://restart.test/live/1.ts")
        assertNull(ladder.advance(nowMs = 300_000L))
        ladder.restartDeadline(nowMs = 300_000L, budgetMs = 30_000L)
        assertEquals(Rung.EXO_TS, ladder.advance(nowMs = 300_000L))
        assertTrue(ladder.expired(331_000L))
    }

    @Test
    fun `a tune inside its budget is completely unaffected`() {
        val ladder = budgetedLadder("http://r.test/live/1.ts")
        assertFalse(ladder.expired(1_000L))
        assertEquals(Rung.EXO_TS, ladder.advance(nowMs = 5_000L))
        assertNull(ladder.advance(nowMs = 20_000L)) // exhausted on its own terms, not expired
        assertFalse(ladder.expired(25_000L))
    }

    @Test
    fun `the budget is bounded but the ladder still terminates without one`() {
        // NO_BUDGET is the old behaviour verbatim, and it is what every other test in this file uses:
        // the finiteness property has to come from the rungs themselves, never from the clock.
        val ladder = LiveLadder().also {
            runBlocking { it.arm("http://s.test/live/1.ts", LiveLadder.NO_BUDGET, 0L) { true } }
        }
        assertFalse(ladder.expired(Long.MAX_VALUE))
        assertTrue(ladder.advance(nowMs = Long.MAX_VALUE) != null)
        assertNull(ladder.advance(nowMs = Long.MAX_VALUE))
    }

    @Test
    fun `a wait the app asked for is given back, so a provider back-off cannot expire the tune`() {
        // HTTP 429 + Retry-After is the panel naming the second the channel frees up, and the engine
        // is counting it down behind the spinner. Charging that to the budget would abandon a good
        // channel over a delay the app itself agreed to.
        val ladder = budgetedLadder("http://y.test/live/1.ts")
        ladder.postponeDeadline(60_000L)
        assertFalse(ladder.expired(31_000L))
        assertEquals(Rung.EXO_TS, ladder.advance(nowMs = 31_000L))
        assertTrue(ladder.expired(91_001L)) // still bounded, just later
    }

    @Test
    fun `re-arming starts a fresh budget — a new tune is a new chance at the clock too`() {
        val url = "http://z.test/live/1.ts"
        val ladder = budgetedLadder(url)
        assertTrue(ladder.expired(40_000L))
        runBlocking { ladder.arm(url, 30_000L, 40_000L) { true } }
        assertFalse(ladder.expired(40_000L))
        assertEquals(Rung.EXO_TS, ladder.advance(nowMs = 50_000L))
    }

    @Test
    fun `every rung carries a format label for the playback log`() {
        val ladder = LiveLadder()
        assertEquals("ExoPlayer + HLS", ladder.label(Rung.EXO_HLS))
        assertEquals("ExoPlayer + TS", ladder.label(Rung.EXO_TS))
    }

    @Test
    fun `the rung table itself is the two stream formats`() {
        assertEquals(2, Rung.entries.size)
        assertTrue(Rung.EXO_HLS.isHls)
        assertFalse(Rung.EXO_TS.isHls)
    }
}
