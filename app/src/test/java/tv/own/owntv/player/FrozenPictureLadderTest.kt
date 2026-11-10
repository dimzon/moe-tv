package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.player.FrozenPictureLadder.Rung

class FrozenPictureLadderTest {

    @Test
    fun `a frozen picture escalates reconnect, fresh decoder, then the tune ladder`() {
        assertEquals(Rung.RECONNECT, FrozenPictureLadder.rungFor(1))
        assertEquals(Rung.FRESH_DECODER, FrozenPictureLadder.rungFor(2))
        assertEquals(Rung.HAND_TO_LADDER, FrozenPictureLadder.rungFor(3))
    }

    @Test
    fun `further freezes stay on the tune ladder`() {
        assertEquals(Rung.HAND_TO_LADDER, FrozenPictureLadder.rungFor(7))
    }
}
