package tv.own.myowntv.features.live

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
 * The selection move when All is hidden. No view model — the flows are the inputs.
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

    /** The built-in rows a rail shows under [builtIns] — the shape [LiveViewModel] assembles now. */
    private fun builtins(builtIns: LiveBuiltIns) =
        LiveViewModel.defaultRail.filter { builtIns.shows(it.key) }

    private fun rail(showAll: Boolean, withFolder: Boolean = true) =
        builtins(LiveBuiltIns(showAll = showAll)).let { if (withFolder) it + folder else it }

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
        val builtIns = MutableStateFlow(LiveBuiltIns(showAll = false))
        val job = launch {
            reconcileHiddenSelection(
                MutableStateFlow(rail(showAll = false)),
                LiveViewModel.defaultRail,
                builtIns,
                selected,
            ) { false }
        }
        delay(40)
        builtIns.value = LiveBuiltIns(showAll = true)
        delay(40)
        assertEquals(LiveKey.Folder(1), selected.value)
        job.cancel()
    }

    @Test
    fun `a switch waits for a rail without the stale folder then lands on the new first folder`() = runBlocking {
        val placeholder = LiveViewModel.defaultRail
        val rail = MutableStateFlow(builtins(LiveBuiltIns(showAll = false)) + LiveRailItem(LiveKey.Folder(1), title = "Old"))
        val deferred = async { fallbackAfterSwitch(rail, placeholder, LiveKey.Folder(1)) }
        delay(40)
        assertEquals(false, deferred.isCompleted)
        rail.value = builtins(LiveBuiltIns(showAll = false)) + LiveRailItem(LiveKey.Folder(7), title = "New")
        await("folder 7") { deferred.isCompleted }
        assertEquals(LiveKey.Folder(7), deferred.await())
    }

    @Test
    fun `a switch with All shown lands on All`() = runBlocking {
        val rail = MutableStateFlow(LiveViewModel.defaultRail + LiveRailItem(LiveKey.Folder(7), title = "New"))
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
