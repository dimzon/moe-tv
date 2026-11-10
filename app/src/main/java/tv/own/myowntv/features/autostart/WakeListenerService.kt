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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import tv.own.myowntv.R

/**
 * Settings › App › Auto start on wake-up. Android has no manifest broadcast for "the screen came on",
 * so this listens for ACTION_SCREEN_ON itself, and only while that option is on.
 *
 * A foreground service, because a registered receiver in a cached process stops hearing the broadcast
 * (Android 14 defers it, and the process can be killed at any time); typed `specialUse`, because none
 * of the other types describes waiting for a wake-up, and `specialUse` may start from BOOT_COMPLETED.
 * It runs in the bare `:autostart` process (see [AutoStart.isAutoStartProcess]), holds no wake lock and
 * does no work between wake-ups, so its cost is a few megabytes of memory and a silent, minimum-importance
 * notification. Without the notification permission the service still runs; only the notification
 * is hidden.
 *
 * If Android ends the process, it restarts the service by itself (sticky), and Android 12+ may keep
 * that restart out of the foreground. The service then ends and auto start on wake-up rests until the next
 * boot, app start or change in Settings starts it again; the switch stays on, as it is the user's choice.
 */
class WakeListenerService : Service() {

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) AutoStart.onScreenOn(context)
        }
    }

    private var listening = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground comes first, with nothing before it that can fail: a service started with
        // startForegroundService that stops before calling it crashes the process.
        val foreground = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), serviceType())
        }.isSuccess
        if (foreground) {
            listen()
            return START_STICKY
        }
        // Refused: only a restart Android made by itself (no intent) can be, and that one carries no
        // obligation to go foreground, so it may simply end.
        if (intent == null) stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun listen() {
        if (listening) return
        // A protected system broadcast: only the system can send it, which a not-exported receiver still hears.
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        listening = true
    }

    override fun onDestroy() {
        if (listening) runCatching { unregisterReceiver(screenOn) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun serviceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun notification(): android.app.Notification {
        // The Application is wrapped in the chosen app language; this service's own context is not.
        val localized = applicationContext
        val title = localized.getString(R.string.auto_start_notification_channel)
        // A refused channel must not keep the service from going foreground; the channel's defaults apply.
        runCatching {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.owntv_notification)
            .setContentTitle(title)
            .setContentText(localized.getString(R.string.auto_start_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "owntv_auto_start"
        const val NOTIFICATION_ID = 4210
    }
}
