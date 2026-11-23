package tv.own.myowntv.features.live

import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveNumberWidthTest {

    // The Stage design converts design pixels to dp at 1.8 px per dp.
    private fun inMpx(width: Dp): Float = width.value * 1.8f

    @Test
    fun `short and missing numbers keep the base column`() {
        assertEquals(50f, inMpx(liveNumberWidth(listOf(null, 1, 999))), 0.01f)
    }

    @Test
    fun `longest number widens the column for the whole list`() {
        assertEquals(52f, inMpx(liveNumberWidth(listOf(12, 1650, 9999))), 0.01f)
        assertEquals(65f, inMpx(liveNumberWidth(listOf(12345, 7))), 0.01f)
    }

    @Test
    fun `large system font widens the column too`() {
        assertEquals(78f, inMpx(liveNumberWidth(listOf(1650), fontScale = 1.5f)), 0.01f)
    }
}
