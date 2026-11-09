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

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.live.LiveBuiltIns
import tv.own.owntv.core.live.LiveKey

/**
 * The selection move when All or Catch-up is hidden. No view model — the flows are the inputs.
 */
class HiddenSelectionReconcilerTest {

    private val folder = LiveRailItem(LiveKey.Folder(1), title = "News")

    private suspend fun await(what: String, condition: () -> Boolean) {
        try {
            withTimeout(2_000) {
                while (!condition()) delay(10)
            }
        } catch (_: TimeoutCancellationException) {
            throw AssertionError("timed out waiting for $what")
        }
    }

    private fun rail(showAll: Boolean, showCatchup: Boolean = true, hasCatchup: Boolean = false, withFolder: Boolean = true) =
        LiveViewModel.railWithCatchup(hasCatchup, LiveBuiltIns(showAll, showCatchup)).let { if (withFolder) it + folder else it }

    @Test
    fun `hiding All moves to the first folder and unhiding does not jump back`() = runBlocking {
        val rail = MutableStateFlow(rail(showAll = false))
        val builtIns = MutableStateFlow(LiveBuiltIns(showAll = false))
        val selected = MutableStateFlow<LiveKey>(LiveKey.All)
        val job = launch {
            reconcileHiddenSelection(rail, LiveViewModel.defaultRail, builtIns, selected) { false }
        }
        await("folder") { selected.value == LiveKey.Folder(1) }
        builtIns.value = LiveBuiltIns()
        rail.value = rail(showAll = true)
        delay(40)
        assertEquals(LiveKey.Folder(1), selected.value)
        job.cancel()
    }

    @Test
    fun `hiding All with no folder falls back to Favorites`() = runBlocking {
        val rail = MutableStateFlow(rail(showAll = false, withFolder = false))
        val selected = MutableStateFlow<LiveKey>(LiveKey.All)
        val job = launch {
            reconcileHiddenSelection(rail, LiveViewModel.defaultRail, MutableStateFlow(LiveBuiltIns(showAll = false)), selected) { false }
        }
        await("favorites") { selected.value == LiveKey.Favorites }
        job.cancel()
    }

    @Test
    fun `hiding Catch-up moves to All when All is shown`() = runBlocking {
        val rail = MutableStateFlow(rail(showAll = true, showCatchup = true, hasCatchup = true))
        val builtIns = MutableStateFlow(LiveBuiltIns())
        val selected = MutableStateFlow<LiveKey>(LiveKey.Catchup)
        val job = launch {
            reconcileHiddenSelection(rail, LiveViewModel.defaultRail, builtIns, selected) { false }
        }
        delay(40)
        assertEquals(LiveKey.Catchup, selected.value)
        builtIns.value = LiveBuiltIns(showCatchup = false)
        await("all") { selected.value == LiveKey.All }
        job.cancel()
    }

    @Test
    fun `the placeholder rail is ignored until a real rail arrives`() = runBlocking {
        val placeholder = LiveViewModel.defaultRail
        val rail = MutableStateFlow(placeholder)
        val selected = MutableStateFlow<LiveKey>(LiveKey.All)
        val job = launch {
            reconcileHiddenSelection(rail, placeholder, MutableStateFlow(LiveBuiltIns(showAll = false)), selected) { false }
        }
        delay(40)
        assertEquals(LiveKey.All, selected.value)
        rail.value = rail(showAll = false)
        await("folder") { selected.value == LiveKey.Folder(1) }
        job.cancel()
    }

    @Test
    fun `a lock leaves a hidden All selected`() = runBlocking {
        val selected = MutableStateFlow<LiveKey>(LiveKey.All)
        val job = launch {
            reconcileHiddenSelection(
                MutableStateFlow(rail(showAll = false)),
                LiveViewModel.defaultRail,
                MutableStateFlow(LiveBuiltIns(showAll = false)),
                selected,
            ) { true }
        }
        delay(40)
        assertEquals(LiveKey.All, selected.value)
        job.cancel()
    }

    @Test
    fun `a selected folder is never changed`() = runBlocking {
        val selected = MutableStateFlow<LiveKey>(LiveKey.Folder(1))
        val builtIns = MutableStateFlow(LiveBuiltIns(showAll = false, showCatchup = false))
        val job = launch {
            reconcileHiddenSelection(
                MutableStateFlow(rail(showAll = false)),
                LiveViewModel.defaultRail,
                builtIns,
                selected,
            ) { false }
        }
        delay(40)
        builtIns.value = LiveBuiltIns(showCatchup = false)
        delay(40)
        assertEquals(LiveKey.Folder(1), selected.value)
        job.cancel()
    }

    @Test
    fun `a switch waits for a rail without the stale folder then lands on the new first folder`() = runBlocking {
        val placeholder = LiveViewModel.defaultRail
        val rail = MutableStateFlow(LiveViewModel.railWithCatchup(false, LiveBuiltIns(showAll = false)) + LiveRailItem(LiveKey.Folder(1), title = "Old"))
        val deferred = async { fallbackAfterSwitch(rail, placeholder, LiveKey.Folder(1)) }
        delay(40)
        assertEquals(false, deferred.isCompleted)
        rail.value = LiveViewModel.railWithCatchup(false, LiveBuiltIns(showAll = false)) + LiveRailItem(LiveKey.Folder(7), title = "New")
        await("folder 7") { deferred.isCompleted }
        assertEquals(LiveKey.Folder(7), deferred.await())
    }

    @Test
    fun `a switch with All shown lands on All`() = runBlocking {
        val rail = MutableStateFlow(LiveViewModel.railWithCatchup(false) + LiveRailItem(LiveKey.Folder(7), title = "New"))
        assertEquals(LiveKey.All, fallbackAfterSwitch(rail, LiveViewModel.defaultRail, LiveKey.Folder(1)))
    }

    @Test
    fun `the placeholder is ignored and a timeout returns All`() = runBlocking {
        val placeholder = LiveViewModel.defaultRail
        val rail = MutableStateFlow(placeholder)
        assertEquals(LiveKey.All, fallbackAfterSwitch(rail, placeholder, LiveKey.Folder(1), timeoutMs = 100))
    }

    @Test
    fun `a switch with All hidden and no folders lands on Favorites`() = runBlocking {
        val rail = MutableStateFlow(rail(showAll = false, withFolder = false))
        assertEquals(LiveKey.Favorites, fallbackAfterSwitch(rail, LiveViewModel.defaultRail, LiveKey.Folder(1)))
    }
}
