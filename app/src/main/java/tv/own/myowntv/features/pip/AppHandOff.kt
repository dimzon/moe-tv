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

/**
 * A screen of another app that OwnTV itself opens: an external player for a live channel, a system
 * permission screen. Leaving the app that way is not the user leaving it, so it must not open the
 * picture-in-picture window (the stream would play twice, once in the window and once in the external
 * player, and Back would land on the launcher instead of OwnTV).
 *
 * [launch] holds the window and stops playback before the other screen opens, the same stop Home makes.
 * The activity then stops as well ([onActivityStopped]), and its next start brings playback back as it
 * always has. A screen that only covers part of OwnTV (a chooser that is cancelled) never stops the
 * activity, so its resume brings playback back instead. A launch that fails gives both back at once.
 *
 * Main thread only, like the engines it stops.
 */
internal class AppHandOff(
    private val holdWindow: (Boolean) -> Unit,
    private val stopPlayback: () -> Unit,
    private val restorePlayback: () -> Unit,
) {

    private var awaitingStop = false

    fun <T> launch(start: () -> T): T {
        holdWindow(true)
        stopPlayback()
        awaitingStop = true
        return try {
            start()
        } catch (e: RuntimeException) {
            awaitingStop = false
            holdWindow(false)
            restorePlayback()
            throw e
        }
    }

    /** The other screen covers OwnTV: the activity's own start brings playback back later. */
    fun onActivityStopped() {
        awaitingStop = false
    }

    fun onActivityResumed() {
        holdWindow(false)
        if (awaitingStop) {
            awaitingStop = false
            restorePlayback()
        }
    }
}
