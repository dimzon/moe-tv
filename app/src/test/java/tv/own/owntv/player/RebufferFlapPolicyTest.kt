package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RebufferFlapPolicyTest {

    @Test
    fun `the reported OTC burst keeps advancing and is not a terminal flap`() {
        val decision = RebufferFlapPolicy.evaluate(
            nowMs = 633L,
            positionMs = 597L,
            windowStartMs = 1L,
            transitionCount = RebufferFlapPolicy.LIMIT,
            windowStartPosMs = 0L,
        )

        assertEquals(506L, decision.requiredProgressMs)
        assertFalse(decision.starved)
    }

    @Test
    fun `a real micro-freeze with almost no position progress is terminal`() {
        val decision = RebufferFlapPolicy.evaluate(
            nowMs = 632L,
            positionMs = 25L,
            windowStartMs = 1L,
            transitionCount = RebufferFlapPolicy.LIMIT,
            windowStartPosMs = 100L,
        )

        assertTrue(decision.starved)
    }

    @Test
    fun `the eighty percent boundary passes and one millisecond below fails`() {
        val passing = RebufferFlapPolicy.evaluate(1_000L, 800L, 1L, RebufferFlapPolicy.LIMIT, 0L)
        val failing = RebufferFlapPolicy.evaluate(1_000L, 799L, 1L, RebufferFlapPolicy.LIMIT, 0L)

        assertFalse(passing.starved)
        assertTrue(failing.starved)
    }

    @Test
    fun `fewer than twelve transitions only accumulate`() {
        val decision = RebufferFlapPolicy.evaluate(500L, 0L, 1L, RebufferFlapPolicy.LIMIT - 1, 100L)

        assertFalse(decision.windowExpired)
        assertFalse(decision.starved)
    }

    @Test
    fun `an expired window starts over instead of judging stale progress`() {
        val decision = RebufferFlapPolicy.evaluate(
            nowMs = RebufferFlapPolicy.WINDOW_MS + 2L,
            positionMs = 0L,
            windowStartMs = 1L,
            transitionCount = RebufferFlapPolicy.LIMIT,
            windowStartPosMs = 100L,
        )

        assertTrue(decision.windowExpired)
        assertFalse(decision.starved)
    }

    @Test
    fun `required progress rounds up for fractional milliseconds`() {
        assertEquals(1L, RebufferFlapPolicy.requiredProgressMs(1L))
        assertEquals(5L, RebufferFlapPolicy.requiredProgressMs(6L))
        assertEquals(506L, RebufferFlapPolicy.requiredProgressMs(632L))
    }
}
