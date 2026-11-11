package tv.own.owntv.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

/**
 * The connection rules as arithmetic. The one provider — RuTube — publishes no connection limit, so
 * the budget never refuses: these tests pin that the answer is unconditional, and that the registry
 * both sides count against still tracks the open streams per playlist.
 */
class ConnectionBudgetTest {

    private fun source() = SourceEntity(
        id = 1,
        name = "RuTube",
        type = SourceType.RUTUBE,
        url = "http://example.test",
    )

    // --- the budget never refuses -----------------------------------------------

    @Test
    fun `a stream is always allowed`() {
        assertEquals(StreamGrant.Allowed, connectionBudget(source(), OpenStreams(watching = 3), StreamPurpose.WATCHING))
    }

    @Test
    fun `a busy playlist still allows one more stream`() {
        assertEquals(StreamGrant.Allowed, connectionBudget(source(), OpenStreams(watching = 4), StreamPurpose.WATCHING))
    }

    @Test
    fun `no playlist at all allows everything`() {
        assertEquals(
            StreamGrant.Allowed,
            connectionBudget(null, OpenStreams(watching = 3), StreamPurpose.WATCHING),
        )
    }

    // --- the reserve rule ---------------------------------------------------------------------

    @Test
    fun `the reserve flag no longer changes the answer, because nothing else takes a stream`() {
        // It was kept for call-shape compatibility when recordings were removed: watching is the only
        // purpose left, and the reserve existed for watching.
        assertEquals(StreamGrant.Allowed, connectionBudget(source(), OpenStreams(watching = 3), StreamPurpose.WATCHING, reserveOneForWatching = false))
    }

    // --- the registry both sides count against ------------------------------------------------------

    @Test
    fun `the registry counts per playlist and forgets a released claim`() {
        val registry = OpenStreamRegistry()
        val tile = registry.claim(sourceId = 1, purpose = StreamPurpose.WATCHING)
        registry.claim(sourceId = 1, purpose = StreamPurpose.WATCHING)
        registry.claim(sourceId = 2, purpose = StreamPurpose.WATCHING)

        assertEquals(OpenStreams(watching = 2), registry.openOn(1))
        assertEquals(OpenStreams(watching = 1), registry.openOn(2))
        assertEquals(OpenStreams(), registry.openOn(3))

        registry.release(tile)
        assertEquals(OpenStreams(watching = 1), registry.openOn(1))
        assertTrue(registry.claims.value.size == 2)
    }
}
