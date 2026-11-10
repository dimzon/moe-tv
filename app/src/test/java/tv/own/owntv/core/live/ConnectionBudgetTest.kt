package tv.own.owntv.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

/**
 * The connection rules as arithmetic. Every case here is one a user can hit with a real account: a
 * one-connection panel, a panel that never said, and a playlist whose stated limit is lower than what
 * it really allows.
 */
class ConnectionBudgetTest {

    private fun source(max: Int) = SourceEntity(
        id = 1,
        name = "Playlist",
        type = SourceType.XTREAM,
        url = "http://example.test",
        maxConnections = max,
    )

    private fun grant(
        max: Int,
        watching: Int = 0,
        reserve: Boolean = true,
    ) = connectionBudget(source(max), OpenStreams(watching), StreamPurpose.WATCHING, reserve)

    private fun refusal(grant: StreamGrant) = (grant as StreamGrant.Refused).reason

    // --- "Ignore connection limit" on the playlist -----------------------------------------------

    @Test
    fun `a playlist set to ignore its limit allows every stream to try`() {
        val ignoring = source(1).copy(ignoreConnectionLimit = true)
        assertEquals(StreamGrant.Allowed, connectionBudget(ignoring, OpenStreams(watching = 3), StreamPurpose.WATCHING))
    }

    // --- maxConnections = 0, the playlist never said ---------------------------------------------

    @Test
    fun `an unknown limit allows everything`() {
        assertEquals(StreamGrant.Allowed, grant(0, watching = 4))
    }

    @Test
    fun `no playlist at all allows everything`() {
        assertEquals(
            StreamGrant.Allowed,
            connectionBudget(null, OpenStreams(watching = 3), StreamPurpose.WATCHING),
        )
    }

    // --- maxConnections = 1 -----------------------------------------------------------------------

    @Test
    fun `one connection means the first tile tunes and the second explains`() {
        assertEquals(StreamGrant.Allowed, grant(1))
        assertEquals(StreamRefusal.SINGLE_CONNECTION, refusal(grant(1, watching = 1)))
    }

    @Test
    fun `the refusal carries the limit and what is open so the sentence can say so`() {
        val refused = grant(1, watching = 1) as StreamGrant.Refused
        assertEquals(1, refused.maxConnections)
        assertEquals(1, refused.open.watching)
        assertEquals(1, refused.open.total)
    }

    // --- maxConnections = 2, 3, 4 -----------------------------------------------------------------

    @Test
    fun `two connections fill two tiles and refuse the third`() {
        assertEquals(StreamGrant.Allowed, grant(2, watching = 1))
        assertEquals(StreamRefusal.ALL_IN_USE, refusal(grant(2, watching = 2)))
    }

    @Test
    fun `three connections fill three tiles and refuse the fourth`() {
        assertEquals(StreamGrant.Allowed, grant(3, watching = 2))
        assertEquals(StreamRefusal.ALL_IN_USE, refusal(grant(3, watching = 3)))
    }

    @Test
    fun `four connections fill all four tiles`() {
        assertEquals(StreamGrant.Allowed, grant(4, watching = 3))
        assertEquals(StreamRefusal.ALL_IN_USE, refusal(grant(4, watching = 4)))
    }

    @Test
    fun `more connections than tiles is never a problem`() {
        assertEquals(StreamGrant.Allowed, grant(8, watching = 4))
    }

    // --- the reserve rule ---------------------------------------------------------------------

    @Test
    fun `the reserve flag no longer changes the answer, because nothing else takes a stream`() {
        // It was kept for call-shape compatibility when recordings were removed: watching is the only
        // purpose left, and the reserve existed for watching.
        assertEquals(StreamGrant.Allowed, grant(3, watching = 2, reserve = false))
        assertEquals(StreamRefusal.ALL_IN_USE, refusal(grant(3, watching = 3, reserve = false)))
        assertEquals(refusal(grant(3, watching = 3)), refusal(grant(3, watching = 3, reserve = false)))
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

    @Test
    fun `a second playlist has its own budget`() {
        val registry = OpenStreamRegistry()
        registry.claim(sourceId = 1, purpose = StreamPurpose.WATCHING)
        // One connection each, one tile each: the second playlist's tile is fine.
        assertEquals(StreamRefusal.SINGLE_CONNECTION, refusal(
            connectionBudget(source(1), registry.openOn(1), StreamPurpose.WATCHING),
        ))
        assertEquals(
            StreamGrant.Allowed,
            connectionBudget(source(1), registry.openOn(2), StreamPurpose.WATCHING),
        )
    }
}
