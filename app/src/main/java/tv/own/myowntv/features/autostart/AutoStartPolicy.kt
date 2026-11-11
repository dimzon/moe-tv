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
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
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

/** The moments at which OwnTV may open by itself. */
internal enum class AutoStartTrigger {
    /** The TV finished starting up (BOOT_COMPLETED). */
    BOOT,

    /** The screen came back on after sleep (ACTION_SCREEN_ON, heard by [WakeListenerService]). */
    WAKE,

    /** OwnTV was just updated (MY_PACKAGE_REPLACED): the wake listener has to be started again. */
    APP_UPDATED,
}

/**
 * When auto start acts, as plain decisions over plain inputs so they can be tested without a device.
 *
 * From Android 10 (API 29) an app in the background may not open an activity unless an exemption
 * applies; for an app that the user wants to open itself, the exemption is "Display over other apps"
 * (SYSTEM_ALERT_WINDOW). Without it a launch attempt is silently dropped by the system, so OwnTV does
 * not try, keeps no wake listener running for nothing, and the Settings row says what is missing.
 */
internal object AutoStartPolicy {

    /** The first Android version that blocks background activity starts. */
    const val BACKGROUND_START_LIMIT_SDK = 29

    fun canStartFromBackground(sdkInt: Int, overlayAllowed: Boolean): Boolean =
        sdkInt < BACKGROUND_START_LIMIT_SDK || overlayAllowed

    /** Whether [trigger] should open OwnTV. An option that is off never opens it; an update never does. */
    fun shouldLaunch(trigger: AutoStartTrigger, options: AutoStartOptions, canStartFromBackground: Boolean): Boolean =
        canStartFromBackground && when (trigger) {
            AutoStartTrigger.BOOT -> options.onBoot
            AutoStartTrigger.WAKE -> options.onWake
            AutoStartTrigger.APP_UPDATED -> false
        }

    /**
     * The wake listener runs only while it can do its job: on a television (a phone or tablet turns its
     * screen on all day, and opening OwnTV each time would be hostile), with the option on and a launch
     * allowed.
     */
    fun wakeListenerNeeded(options: AutoStartOptions, canStartFromBackground: Boolean, television: Boolean): Boolean =
        television && options.onWake && canStartFromBackground

    /** Ask for "Display over other apps" only at the moment an option is turned on and it is missing. */
    fun shouldAskForOverlay(before: AutoStartOptions, after: AutoStartOptions, canStartFromBackground: Boolean): Boolean =
        !canStartFromBackground && ((after.onBoot && !before.onBoot) || (after.onWake && !before.onWake))

    /** Whether a Settings row that is [enabled] has to say that it cannot work yet. */
    fun needsOverlayNotice(enabled: Boolean, canStartFromBackground: Boolean): Boolean =
        enabled && !canStartFromBackground
}
