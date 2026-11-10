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

import androidx.compose.runtime.MonotonicFrameClock
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where focus goes when BACK closes the category sheet. */
class SheetBackFocusTest {

    /** Hands out frames one at a time, so a test sees what happens before and after each. */
    private class StepClock : MonotonicFrameClock {
        private val waiting = mutableListOf<CancellableContinuation<Unit>>()

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            suspendCancellableCoroutine { waiting += it }
            return onFrame(0L)
        }

        fun frame() {
            val due = waiting.toList()
            waiting.clear()
            due.forEach { it.resume(Unit) }
        }
    }

    private val clock = StepClock()
    private val scope = CoroutineScope(Dispatchers.Unconfined + clock)
    private var currentTries = 0
    private var listWaysIn = 0
    private var listFocused = false

    @After
    fun tearDown() = scope.cancel()

    private fun back() = focusListAfterSheet(scope, { currentTries++; listFocused }, { listFocused }) { listWaysIn++ }

    @Test
    fun theCurrentItemIsTriedAtOnce() {
        back()
        assertEquals(1, currentTries)
        assertEquals(0, listWaysIn)
    }

    @Test
    fun aListHoldingFocusIsLeftAlone() {
        listFocused = true
        back()
        repeat(3) { clock.frame() }
        assertEquals(0, listWaysIn)
    }

    @Test
    fun aListALockEmptiedTakesItsOwnWayInOnceSettled() {
        back()
        clock.frame()
        assertEquals(0, listWaysIn)
        clock.frame()
        assertEquals(1, listWaysIn)
        clock.frame()
        assertEquals(1, listWaysIn)
    }

    @Test
    fun anItemThatVanishesAfterTakingFocusStillGetsTheWayIn() {
        listFocused = true
        back()
        listFocused = false
        repeat(2) { clock.frame() }
        assertEquals(1, listWaysIn)
    }
}
