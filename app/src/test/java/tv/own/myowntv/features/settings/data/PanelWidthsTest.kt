package tv.own.myowntv.features.settings.data
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
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.PanelShares
import tv.own.owntv.core.settings.PanelWidthLimits
import tv.own.owntv.core.settings.balanceToTotal

class PanelWidthsTest {

    @Test
    fun `zero is valid only for the third panel when total is 100`() {
        assertTrue(PanelShares(category = 40, list = 60, preview = 0).isValid)
        assertFalse(PanelShares(category = 0, list = 80, preview = 20).isValid)
        assertFalse(PanelShares(category = 20, list = 70, preview = 0).isValid)
        assertFalse(PanelShares(category = 15, list = 80, preview = 5).isValid)
    }

    @Test
    fun `balancing preserves a hidden third panel`() {
        val balanced = balanceToTotal(PanelShares(category = 35, list = 55, preview = 0))

        assertEquals(0, balanced.preview)
        assertEquals(100, balanced.total)
        assertTrue(balanced.category >= PanelWidthLimits.MIN)
        assertTrue(balanced.list >= PanelWidthLimits.MIN)
    }

    @Test
    fun `balancing never lets category or list become zero`() {
        val balanced = balanceToTotal(PanelShares(category = 0, list = 90, preview = 10))

        assertEquals(100, balanced.total)
        assertTrue(balanced.category >= PanelWidthLimits.MIN)
        assertTrue(balanced.list >= PanelWidthLimits.MIN)
    }

    @Test
    fun `hidden preview reserves divider and its two surrounding gaps`() {
        val widths = computePanelWidths(
            shares = PanelShares(category = 40, list = 60, preview = 0),
            total = 1_000.dp,
        )

        assertEquals(0f, widths.preview.value, 0f)
        assertEquals(975f, widths.category.value + widths.list.value, 0.01f)
    }

    @Test
    fun `visible preview reserves divider and all three column gaps`() {
        val widths = computePanelWidths(
            shares = PanelShares(category = 20, list = 50, preview = 30),
            total = 1_000.dp,
        )

        assertEquals(963f, widths.category.value + widths.list.value + widths.preview.value, 0.01f)
        assertTrue(widths.preview.value > 0f)
    }
}
