package tv.own.owntv.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainSectionTest {

    @Test
    fun `the browse order is home, live and the guide`() {
        val order = MainSection.browseOrder
        assertEquals(listOf(MainSection.HOME, MainSection.LIVE_TV, MainSection.EPG), order)
        order.forEach { assertTrue(it.isBrowse && it in MainSection.allBrowse) }
    }

    @Test
    fun `dynamic mode always shows home, and live and the guide only with channels`() {
        assertEquals(setOf(MainSection.HOME), MainSection.dynamicVisible(false))
        assertEquals(
            setOf(MainSection.HOME, MainSection.LIVE_TV, MainSection.EPG),
            MainSection.dynamicVisible(true),
        )
    }

    @Test
    fun `static mode hides a browse item only when the user hid it`() {
        // An existing user's hidden set never names an unknown section, so the update shows it.
        assertTrue(MainSection.LIVE_TV in MainSection.staticVisible(setOf("GONE")))
        assertFalse(MainSection.LIVE_TV in MainSection.staticVisible(setOf("LIVE_TV")))
    }
}
