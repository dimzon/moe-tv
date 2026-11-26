package tv.own.myowntv.features.shell

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.nav.MainSection

class LockedSettingsExitTest {

    private val visible = setOf(MainSection.HOME, MainSection.LIVE_TV)

    @Test
    fun `returns to the section Settings was opened from`() {
        assertEquals(MainSection.LIVE_TV, sectionAfterLockedSettings(MainSection.LIVE_TV, visible))
        assertEquals(MainSection.MORE, sectionAfterLockedSettings(MainSection.MORE, visible))
        assertEquals(MainSection.SEARCH, sectionAfterLockedSettings(MainSection.SEARCH, visible))
    }

    @Test
    fun `falls back to More when that section has left the rail`() {
        assertEquals(MainSection.MORE, sectionAfterLockedSettings(MainSection.EPG, visible))
    }

    @Test
    fun `never returns to Settings itself`() {
        assertEquals(MainSection.MORE, sectionAfterLockedSettings(MainSection.SETTINGS, visible))
    }
}
