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

import android.content.Context
import androidx.core.content.edit

/**
 * Settings › App › Auto start: open OwnTV by itself when the TV starts up, when it wakes from sleep,
 * or both. Device-wide rather than per profile, because both are about the television, not the viewer.
 * Both are off until the user turns them on.
 */
internal data class AutoStartOptions(val onBoot: Boolean = false, val onWake: Boolean = false)

/**
 * Where [AutoStartOptions] are kept — in the app, not in core's settings storage, and deliberately so:
 * both are about this television, not the viewer, so they belong in no profile and in no backup that
 * may be restored onto another box. SharedPreferences rather than DataStore: [AutoStartReceiver] reads
 * them synchronously in the small `:autostart` process, which starts nothing else of the app.
 * That process only reads them right after it starts (a boot or an update always starts it afresh), so
 * the copy SharedPreferences caches per process is never a stale one.
 */
internal class AutoStartStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): AutoStartOptions = AutoStartOptions(
        onBoot = prefs.getBoolean(KEY_BOOT, false),
        onWake = prefs.getBoolean(KEY_WAKE, false),
    )

    fun write(options: AutoStartOptions) {
        prefs.edit {
            putBoolean(KEY_BOOT, options.onBoot)
            putBoolean(KEY_WAKE, options.onWake)
        }
    }

    private companion object {
        const val PREFS = "owntv_auto_start"
        const val KEY_BOOT = "on_boot"
        const val KEY_WAKE = "on_wake"
    }
}
