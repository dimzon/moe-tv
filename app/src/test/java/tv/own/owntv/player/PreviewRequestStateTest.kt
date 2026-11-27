package tv.own.owntv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewRequestStateTest {

    @Test
    fun `a cancelled resolve is not a committed preview`() {
        val resolving = PreviewRequestState().begin("match")

        assertFalse(resolving.isCommitted("match"))
        assertTrue(resolving.isCurrent(resolving.generation, "match"))
    }

    @Test
    fun `only the current generation may commit`() {
        val first = PreviewRequestState().begin("tnt")
        val second = first.begin("match")

        assertFalse(second.isCurrent(first.generation, "tnt"))
        assertTrue(second.isCurrent(second.generation, "match"))
        assertTrue(second.commit().isCommitted("match"))
    }

    @Test
    fun `refocus after cancellation starts a new request instead of reusing the old marker`() {
        val cancelled = PreviewRequestState().begin("match")
        val refocused = cancelled.begin("match")

        assertFalse(refocused.isCurrent(cancelled.generation, "match"))
        assertTrue(refocused.generation > cancelled.generation)
        assertFalse(refocused.isCommitted("match"))
    }

    @Test
    fun `an old A completion cannot overwrite a newer B request`() {
        val firstA = PreviewRequestState().begin("a")
        val b = firstA.begin("b")
        val secondA = b.begin("a")

        assertFalse(secondA.isCurrent(firstA.generation, "a"))
        assertTrue(secondA.isCurrent(secondA.generation, "a"))
        assertFalse(secondA.isCommitted("a"))
    }

    @Test
    fun `invalidate clears both channel and committed state`() {
        val ready = PreviewRequestState().begin("tnt").commit()
        val stopped = ready.invalidate()

        assertFalse(stopped.isCommitted("tnt"))
        assertFalse(stopped.isCurrent(ready.generation, "tnt"))
        assertTrue(stopped.channelId == null)
    }
}
