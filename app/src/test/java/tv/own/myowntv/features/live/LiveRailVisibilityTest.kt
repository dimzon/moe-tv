package tv.own.myowntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.live.LiveBuiltIns
import tv.own.owntv.core.live.LiveKey

/**
 * The category rail's visibility rule: the built-in rows shown, filtered by [LiveBuiltIns]. With the
 * catch-up list gone from the rail, the only switch left on it is "All channels".
 */
class LiveRailVisibilityTest {

    private fun keys(showAll: Boolean = true, showCatchup: Boolean = true) = railKeys(LiveBuiltIns(showAll, showCatchup))

    /** The rail the view model assembles: the built-ins, each kept only while its switch is on. */
    private fun railKeys(builtIns: LiveBuiltIns) = LiveViewModel.defaultRail.filter { builtIns.shows(it.key) }.map { it.key }

    @Test
    fun `defaults are today's rail`() {
        assertEquals(listOf(LiveKey.Favorites, LiveKey.History, LiveKey.All), keys())
    }

    @Test
    fun `showAll false drops All only`() {
        assertEquals(listOf(LiveKey.Favorites, LiveKey.History), keys(showAll = false))
    }

    @Test
    fun `the catch-up switch no longer moves anything — the rail has no catch-up row`() {
        // The key itself survives in core (the guide still lists catch-up channels); the live rail
        // simply has nothing for the switch to hide.
        assertEquals(keys(), keys(showCatchup = false))
    }

    @Test
    fun `fallbackKey follows the rail as shown`() {
        val open = LiveRailItem(LiveKey.Folder(2), title = "B")
        assertEquals(LiveKey.All, LiveViewModel.fallbackKey(LiveViewModel.defaultRail + open))
        val noAll = LiveViewModel.defaultRail.filter { LiveBuiltIns(showAll = false).shows(it.key) }
        assertEquals(LiveKey.Folder(2), LiveViewModel.fallbackKey(noAll + open))
    }
}
