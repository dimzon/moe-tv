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

import android.content.Context
import java.io.File

/**
 * Tells the app's own process that auto start, in the `:autostart` process, just opened OwnTV.
 *
 * Not an intent extra: a running OwnTV is only brought to the front, and Android hands the activity a
 * new intent, if at all, after it has started and its playback has come back. A small file is shared by
 * both processes and can be read before that ([AutoStartProfileGate]).
 */
internal object AutoStartLaunchMark {

    private const val FILE_NAME = "auto_start_launch"

    fun set(context: Context) {
        runCatching { file(context).writeText("") }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** How long ago auto start opened OwnTV, in milliseconds, and forget it; null if it did not. */
    fun consume(context: Context): Long? = runCatching {
        val file = file(context)
        if (!file.exists()) return@runCatching null
        val age = System.currentTimeMillis() - file.lastModified()
        file.delete()
        age
    }.getOrNull()

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE_NAME)
}
