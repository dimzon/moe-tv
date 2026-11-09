package tv.own.myowntv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.myowntv.core.ui.stage.StageGlyph

class StageGlyphsTest {

    // Every core glyph but the phone-only three finds its MyOwnTVIcon by name; a renamed entry fails here, not on screen.
    @Test
    fun everyCoreGlyphButThePhoneOnesHasAnMyOwnTVIcon() = assertEquals(
        StageGlyph.entries.map { it.name } - setOf("PLUS", "MOON", "EYE"),
        StageGlyphPaths.keys.map { it.name },
    )
}
