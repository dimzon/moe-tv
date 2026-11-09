package tv.own.myowntv.features.autostart
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

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * The Android side of auto start: opening OwnTV, running the wake listener, asking for the permission.
 * Every call fails silently: a TV that refuses any of this simply does not auto start.
 */
internal object AutoStart {

    /** Must match `android:process` of [AutoStartReceiver] and [WakeListenerService] in the manifest. */
    private const val PROCESS_SUFFIX = ":autostart"

    /**
     * True inside the `:autostart` process. The app's `Application.onCreate` returns straight away there,
     * so the wake listener costs a bare process instead of the whole app with its database and players.
     */
    fun isAutoStartProcess(context: Context): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            runCatching { java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault("")
        }
        return name == context.packageName + PROCESS_SUFFIX
    }

    fun canStartFromBackground(context: Context): Boolean =
        AutoStartPolicy.canStartFromBackground(Build.VERSION.SDK_INT, Settings.canDrawOverlays(context))

    /** Auto start on wake-up is offered on a television only (see [AutoStartPolicy.wakeListenerNeeded]). */
    fun isTelevision(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** A boot or an update: open OwnTV if that option is on, and start or stop the wake listener. */
    fun onBroadcast(context: Context, trigger: AutoStartTrigger) {
        val options = AutoStartStore(context).read()
        val canStart = canStartFromBackground(context)
        if (AutoStartPolicy.shouldLaunch(trigger, options, canStart)) launch(context)
        syncWakeListener(context, options, canStart)
    }

    /**
     * The screen came on. The wake listener runs only while that option is on, and it does not read the
     * stored options: the `:autostart` process may outlive a change made in the app's own process.
     */
    fun onScreenOn(context: Context) {
        if (canStartFromBackground(context)) launch(context)
    }

    /** The app process started: bring the wake listener back after a force stop. */
    fun onAppStarted(context: Context) {
        syncWakeListener(context, AutoStartStore(context).read(), canStartFromBackground(context))
    }

    /** A Settings switch changed: store it, follow it, and ask for the permission it needs. */
    fun update(context: Context, before: AutoStartOptions, after: AutoStartOptions) {
        AutoStartStore(context).write(after)
        val canStart = canStartFromBackground(context)
        syncWakeListener(context, after, canStart)
        if (AutoStartPolicy.shouldAskForOverlay(before, after, canStart)) openOverlayPermissionSettings(context)
    }

    fun syncWakeListener(context: Context, options: AutoStartOptions, canStartFromBackground: Boolean) {
        val service = Intent(context, WakeListenerService::class.java)
        if (AutoStartPolicy.wakeListenerNeeded(options, canStartFromBackground, isTelevision(context))) {
            // Android 12+ refuses a foreground service started from the background in some states; the
            // next boot, update or app start tries again.
            runCatching { ContextCompat.startForegroundService(context, service) }
        } else {
            runCatching { context.stopService(service) }
        }
    }

    /**
     * Opens OwnTV the way the launcher does, so the existing App startup choice (Home, last channel, a
     * specific channel) decides what shows on a cold start, and a running OwnTV comes back where it was,
     * behind the profile PIN again ([AutoStartLaunchMark]). Through the launcher activity that is enabled
     * now: with another icon colour chosen, `MainActivity` itself is disabled and opens nothing.
     */
    private fun launch(context: Context) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(if (isTelevision(context)) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(tv.own.owntv.core.brand.AppIconSwitcher.launchComponent(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        AutoStartLaunchMark.set(context)
        runCatching { context.startActivity(intent) }.onFailure { AutoStartLaunchMark.clear(context) }
    }

    /**
     * The "Display over other apps" screen for OwnTV. Some TV builds have no such screen, or only the
     * list without the per-app entry, so this falls back to the list and then to OwnTV's App info.
     */
    private fun openOverlayPermissionSettings(context: Context) {
        val pkg = "package:${context.packageName}".toUri()
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
        )
        for (intent in candidates) {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }
}
