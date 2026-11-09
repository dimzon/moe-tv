package tv.own.myowntv.features.live
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*
import tv.own.owntv.player.ffmpeg.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import tv.own.owntv.core.live.LiveBuiltIns
import tv.own.owntv.core.live.LiveKey

class LiveRailVisibilityTest {

    private fun keys(hasCatchup: Boolean, showAll: Boolean = true, showCatchup: Boolean = true) =
        LiveViewModel.railWithCatchup(hasCatchup, LiveBuiltIns(showAll, showCatchup)).map { it.key }

    @Test
    fun `defaults without catch-up are today's rail`() {
        val rail = LiveViewModel.railWithCatchup(false)
        assertSame(LiveViewModel.defaultRail, rail)
        assertEquals(listOf(LiveKey.Favorites, LiveKey.History, LiveKey.All), rail.map { it.key })
    }

    @Test
    fun `defaults with catch-up insert Catchup before All`() {
        assertEquals(
            listOf(LiveKey.Favorites, LiveKey.History, LiveKey.Catchup, LiveKey.All),
            keys(true),
        )
    }

    @Test
    fun `showAll false drops All only`() {
        assertEquals(listOf(LiveKey.Favorites, LiveKey.History), keys(false, showAll = false))
        assertEquals(
            listOf(LiveKey.Favorites, LiveKey.History, LiveKey.Catchup),
            keys(true, showAll = false),
        )
    }

    @Test
    fun `showCatchup false drops Catchup only even when hasCatchup`() {
        assertEquals(
            listOf(LiveKey.Favorites, LiveKey.History, LiveKey.All),
            keys(true, showCatchup = false),
        )
    }

    @Test
    fun `fallbackKey follows the rail as shown`() {
        val open = LiveRailItem(LiveKey.Folder(2), title = "B")
        assertEquals(LiveKey.All, LiveViewModel.fallbackKey(LiveViewModel.railWithCatchup(false) + open))
        assertEquals(LiveKey.Folder(2), LiveViewModel.fallbackKey(LiveViewModel.railWithCatchup(true, LiveBuiltIns(showAll = false)) + open))
    }
}
