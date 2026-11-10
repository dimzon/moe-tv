package tv.own.myowntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.SubtitleStyle
import tv.own.owntv.core.settings.SubtitleStyle.Position

/**
 * The subtitle-anchor mapping the live renderer reads — Media3's `SubtitleView`, driven through the
 * fractions [SubtitleStyle] derives from the picked anchor.
 *
 * Pinned before any work on subtitle geometry: the mapping is applied in the preview surface, and a
 * consolidation is only safe if "the anchor a position resolves to" is something a test states rather
 * than something each call site re-derives. The "Default" values matter as much as the anchors — every
 * option's default means *leave this renderer alone*, which is what keeps the master toggle from
 * changing anything on its own, including broadcaster-styled Live TV cues.
 */
class SubtitleStyleTest {

    @Test
    fun `every option has a default that means leave this alone`() {
        assertFalse(SubtitleStyle.hasScale(SubtitleStyle.SCALE_DEFAULT))
        assertFalse(SubtitleStyle.hasColor(SubtitleStyle.COLOR_DEFAULT))
        assertFalse(SubtitleStyle.hasOpacity(SubtitleStyle.OPACITY_DEFAULT))
        assertEquals(Position.DEFAULT, Position.fromKey(null))
        assertEquals(Position.DEFAULT, Position.fromKey("nonsense"))
    }

    @Test
    fun `a picked option is recognised as picked`() {
        assertTrue(SubtitleStyle.hasScale(1.5f))
        assertTrue(SubtitleStyle.hasColor("#FFEE00"))
        assertTrue(SubtitleStyle.hasOpacity(SubtitleStyle.OPACITY_MIN)) // 0 = "no box", a real choice
        assertTrue(SubtitleStyle.hasOpacity(SubtitleStyle.OPACITY_START))
    }

    @Test
    fun `the picker offers six anchors in reading order and every key round-trips`() {
        assertEquals(
            listOf(
                Position.TOP_LEFT, Position.TOP_CENTER, Position.TOP_RIGHT,
                Position.BOTTOM_LEFT, Position.BOTTOM_CENTER, Position.BOTTOM_RIGHT,
            ),
            Position.ANCHORS,
        )
        for (p in Position.entries) assertEquals(p, Position.fromKey(p.key))
    }

    @Test
    fun `top and bottom anchors are classified consistently`() {
        assertTrue(Position.TOP_LEFT.isTop && Position.TOP_CENTER.isTop && Position.TOP_RIGHT.isTop)
        assertFalse(Position.BOTTOM_LEFT.isTop || Position.BOTTOM_CENTER.isTop || Position.BOTTOM_RIGHT.isTop)
        // "Default" is not top, so it falls to the renderer's own placement, which is the bottom.
        assertFalse(Position.DEFAULT.isTop)
    }

    @Test
    fun `left and right are the outer columns only — centre is neither`() {
        assertTrue(Position.TOP_LEFT.isLeft && Position.BOTTOM_LEFT.isLeft)
        assertTrue(Position.TOP_RIGHT.isRight && Position.BOTTOM_RIGHT.isRight)
        assertFalse(Position.TOP_CENTER.isLeft || Position.TOP_CENTER.isRight)
        assertFalse(Position.BOTTOM_CENTER.isLeft || Position.BOTTOM_CENTER.isRight)
        assertFalse(Position.DEFAULT.isLeft || Position.DEFAULT.isRight)
    }

    @Test
    fun `the fraction mapping follows the anchor flags for every anchor`() {
        // The cue fractions handed to `SubtitleView` must say what the picker's flags say: a "top"
        // anchor lands near the top, a side column lands off centre, and the rest is centred.
        for (p in Position.entries) {
            val nearTop = SubtitleStyle.lineFraction(p) < 0.5f
            assertEquals("vertical agreement for $p", p.isTop, nearTop)

            val x = SubtitleStyle.positionFraction(p)
            when {
                p.isLeft -> assertTrue("left anchor for $p", x < 0.5f)
                p.isRight -> assertTrue("right anchor for $p", x > 0.5f)
                else -> assertEquals("centre anchor for $p", 0.5f, x, 0.0001f)
            }
        }
    }

    @Test
    fun `the anchors sit inside the frame, never flush against an edge`() {
        // A line placed at exactly 0 or 1 is clipped by overscan on a TV; the 5% inset is the safe area.
        for (p in Position.entries) {
            assertTrue("line inside for $p", SubtitleStyle.lineFraction(p) in 0.02f..0.98f)
            assertTrue("column inside for $p", SubtitleStyle.positionFraction(p) in 0.02f..0.98f)
        }
    }

    @Test
    fun `colors parse in both accepted forms and never crash on junk`() {
        assertEquals(0xFFFFEE00.toInt(), SubtitleStyle.colorArgb("#FFEE00"))
        assertEquals(0xFFFFEE00.toInt(), SubtitleStyle.colorArgb("FFEE00"))
        assertEquals(0x80FF0000.toInt(), SubtitleStyle.colorArgb("#80FF0000"))
        assertEquals(0xFFFFFFFF.toInt(), SubtitleStyle.colorArgb("")) // falls back to opaque white
        assertEquals(0xFFFFFFFF.toInt(), SubtitleStyle.colorArgb("#zzzzzz"))
        assertEquals(0xFFFFFFFF.toInt(), SubtitleStyle.colorArgb("#12345"))
    }

    @Test
    fun `the background box is black at the requested transparency`() {
        assertEquals(0x00000000, SubtitleStyle.backgroundArgb(0))
        assertEquals(0xFF000000.toInt(), SubtitleStyle.backgroundArgb(100))
        assertEquals(0x7F000000, SubtitleStyle.backgroundArgb(50))
        assertEquals(0xFF000000.toInt(), SubtitleStyle.backgroundArgb(500)) // clamped
        assertEquals(0x00000000, SubtitleStyle.backgroundArgb(-20)) // clamped
    }
}
