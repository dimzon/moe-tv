package tv.own.myowntv.features.epg
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideRowFocusArgsTest {

    @Test
    fun `focused row gets the cursor and cell mode`() {
        val args = guideRowFocusArgs(
            index = 3,
            focusedRow = 3,
            cursorTime = 1_700_000_000_000L,
            inCellMode = true,
            gridHasFocus = true,
        )
        assertEquals(1_700_000_000_000L, args.cursorTime)
        assertTrue(args.cellMode)
        assertTrue(args.selectedRow)
    }

    @Test
    fun `unfocused row gets no cursor and no cell mode`() {
        val args = guideRowFocusArgs(
            index = 2,
            focusedRow = 3,
            cursorTime = 1_700_000_000_000L,
            inCellMode = true,
            gridHasFocus = true,
        )
        assertEquals(-1L, args.cursorTime)
        assertEquals(NO_CURSOR, args.cursorTime)
        assertFalse(args.cellMode)
        assertFalse(args.selectedRow)
    }

    @Test
    fun `selectedRow needs the focused index and grid focus`() {
        assertFalse(
            guideRowFocusArgs(1, focusedRow = 1, cursorTime = 5L, inCellMode = true, gridHasFocus = false).selectedRow,
        )
        assertFalse(
            guideRowFocusArgs(0, focusedRow = 1, cursorTime = 5L, inCellMode = true, gridHasFocus = true).selectedRow,
        )
        assertTrue(
            guideRowFocusArgs(1, focusedRow = 1, cursorTime = 5L, inCellMode = false, gridHasFocus = true).selectedRow,
        )
    }

    @Test
    fun `cell mode stays off when not in cell mode`() {
        val args = guideRowFocusArgs(
            index = 4,
            focusedRow = 4,
            cursorTime = 9L,
            inCellMode = false,
            gridHasFocus = true,
        )
        assertFalse(args.cellMode)
        assertEquals(9L, args.cursorTime)
        assertTrue(args.selectedRow)
    }
}
