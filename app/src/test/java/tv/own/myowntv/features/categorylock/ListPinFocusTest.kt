package tv.own.myowntv.features.categorylock
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which PIN prompt closes move focus to the opened category's first item. */
class ListPinFocusTest {

    @Test
    fun theButtonsPromptOpeningTheCategoryWaitsForTheFirstItem() {
        val focus = ListPinFocus()
        focus.askedFromList()
        assertTrue(focus.takeClose(opened = true))
        assertTrue(focus.awaitingItems)
        assertFalse(focus.refocusPin)
    }

    @Test
    fun theButtonsPromptLeftShutReturnsToTheButton() {
        val focus = ListPinFocus()
        focus.askedFromList()
        assertTrue(focus.takeClose(opened = false))
        assertFalse(focus.awaitingItems)
        assertTrue(focus.refocusPin)
    }

    @Test
    fun theSheetsPromptCloseIsLeftToTheScreen() {
        val focus = ListPinFocus()
        assertFalse(focus.takeClose(opened = true))
        assertFalse(focus.awaitingItems)
    }

    @Test
    fun theSheetOpeningTheCategoryWaitsForTheFirstItemToo() {
        val focus = ListPinFocus()
        assertFalse(focus.takeClose(opened = true))
        focus.openedFromCategories()
        assertTrue(focus.awaitingItems)
        assertFalse(focus.refocusPin)
    }
}
