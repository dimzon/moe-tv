package tv.own.myowntv.features.autostart
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

class AutoStartPolicyTest {

    private val off = AutoStartOptions()
    private val boot = AutoStartOptions(onBoot = true)
    private val wake = AutoStartOptions(onWake = true)
    private val both = AutoStartOptions(onBoot = true, onWake = true)

    // --- defaults ---

    @Test
    fun bothOptionsAreOffByDefault() {
        assertEquals(AutoStartOptions(onBoot = false, onWake = false), AutoStartOptions())
    }

    // --- canStartFromBackground ---

    @Test
    fun beforeAndroid10_noPermissionIsNeeded() {
        assertTrue(AutoStartPolicy.canStartFromBackground(sdkInt = 28, overlayAllowed = false))
    }

    @Test
    fun fromAndroid10_theOverlayPermissionDecides() {
        assertFalse(AutoStartPolicy.canStartFromBackground(sdkInt = 29, overlayAllowed = false))
        assertTrue(AutoStartPolicy.canStartFromBackground(sdkInt = 29, overlayAllowed = true))
        assertFalse(AutoStartPolicy.canStartFromBackground(sdkInt = 36, overlayAllowed = false))
    }

    // --- shouldLaunch ---

    @Test
    fun optionsOff_neverLaunch() {
        AutoStartTrigger.entries.forEach { trigger ->
            assertFalse(trigger.name, AutoStartPolicy.shouldLaunch(trigger, off, canStartFromBackground = true))
        }
    }

    @Test
    fun boot_launchesOnlyWhenBootIsOn() {
        assertTrue(AutoStartPolicy.shouldLaunch(AutoStartTrigger.BOOT, boot, canStartFromBackground = true))
        assertFalse(AutoStartPolicy.shouldLaunch(AutoStartTrigger.BOOT, wake, canStartFromBackground = true))
    }

    @Test
    fun wake_launchesOnlyWhenWakeIsOn() {
        assertTrue(AutoStartPolicy.shouldLaunch(AutoStartTrigger.WAKE, wake, canStartFromBackground = true))
        assertFalse(AutoStartPolicy.shouldLaunch(AutoStartTrigger.WAKE, boot, canStartFromBackground = true))
    }

    @Test
    fun anUpdate_neverLaunches() {
        assertFalse(AutoStartPolicy.shouldLaunch(AutoStartTrigger.APP_UPDATED, both, canStartFromBackground = true))
    }

    @Test
    fun withoutPermission_nothingLaunches() {
        AutoStartTrigger.entries.forEach { trigger ->
            assertFalse(trigger.name, AutoStartPolicy.shouldLaunch(trigger, both, canStartFromBackground = false))
        }
    }

    // --- wakeListenerNeeded ---

    @Test
    fun wakeListener_runsOnlyForWakeWithPermission() {
        assertTrue(AutoStartPolicy.wakeListenerNeeded(wake, canStartFromBackground = true, television = true))
        assertTrue(AutoStartPolicy.wakeListenerNeeded(both, canStartFromBackground = true, television = true))
        assertFalse(AutoStartPolicy.wakeListenerNeeded(boot, canStartFromBackground = true, television = true))
        assertFalse(AutoStartPolicy.wakeListenerNeeded(off, canStartFromBackground = true, television = true))
        assertFalse(AutoStartPolicy.wakeListenerNeeded(wake, canStartFromBackground = false, television = true))
    }

    @Test
    fun wakeListener_neverRunsOffATelevision() {
        assertFalse(AutoStartPolicy.wakeListenerNeeded(wake, canStartFromBackground = true, television = false))
        assertFalse(AutoStartPolicy.wakeListenerNeeded(both, canStartFromBackground = true, television = false))
    }

    // --- shouldAskForOverlay ---

    @Test
    fun turningAnOptionOn_withoutPermission_asks() {
        assertTrue(AutoStartPolicy.shouldAskForOverlay(off, boot, canStartFromBackground = false))
        assertTrue(AutoStartPolicy.shouldAskForOverlay(boot, both, canStartFromBackground = false))
    }

    @Test
    fun turningAnOptionOff_neverAsks() {
        assertFalse(AutoStartPolicy.shouldAskForOverlay(both, boot, canStartFromBackground = false))
        assertFalse(AutoStartPolicy.shouldAskForOverlay(wake, off, canStartFromBackground = false))
    }

    @Test
    fun withPermission_neverAsks() {
        assertFalse(AutoStartPolicy.shouldAskForOverlay(off, both, canStartFromBackground = true))
    }

    // --- needsOverlayNotice ---

    @Test
    fun notice_onlyForAnEnabledSwitchThatCannotWork() {
        assertTrue(AutoStartPolicy.needsOverlayNotice(enabled = true, canStartFromBackground = false))
        assertFalse(AutoStartPolicy.needsOverlayNotice(enabled = false, canStartFromBackground = false))
        assertFalse(AutoStartPolicy.needsOverlayNotice(enabled = true, canStartFromBackground = true))
    }
}
