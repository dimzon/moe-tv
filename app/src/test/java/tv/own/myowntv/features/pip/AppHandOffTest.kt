package tv.own.myowntv.features.pip
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AppHandOffTest {

    private val events = mutableListOf<String>()
    private val handOff = AppHandOff(
        holdWindow = { held -> events += if (held) "hold" else "release" },
        stopPlayback = { events += "stop" },
        restorePlayback = { events += "restore" },
    )

    @Test
    fun launch_holdsTheWindowAndStopsPlaybackBeforeTheOtherScreenOpens() {
        handOff.launch { events += "start" }

        assertEquals(listOf("hold", "stop", "start"), events)
    }

    @Test
    fun launch_returnsWhatTheStartReturned() {
        assertEquals(true, handOff.launch { true })
    }

    @Test
    fun coveredActivity_leavesTheRestoreToItsOwnStart() {
        handOff.launch { }
        handOff.onActivityStopped()
        events.clear()

        handOff.onActivityResumed()

        assertEquals(listOf("release"), events)
    }

    @Test
    fun screenThatNeverStopsTheActivity_restoresPlaybackOnResume() {
        handOff.launch { }
        events.clear()

        handOff.onActivityResumed()
        handOff.onActivityResumed()

        assertEquals(listOf("release", "restore", "release"), events)
    }

    @Test
    fun failedLaunch_givesTheWindowAndPlaybackBackAndRethrows() {
        val failure = IllegalStateException("no activity")

        val thrown = assertThrows(IllegalStateException::class.java) { handOff.launch { throw failure } }

        assertSame(failure, thrown)
        assertEquals(listOf("hold", "stop", "release", "restore"), events)
        events.clear()
        handOff.onActivityResumed()
        assertEquals(listOf("release"), events)
    }

    @Test
    fun resumeWithoutAHandOff_onlyReleasesTheWindow() {
        handOff.onActivityStopped()
        handOff.onActivityResumed()

        assertEquals(listOf("release"), events)
    }
}
