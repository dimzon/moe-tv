package tv.own.myowntv.features.live
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.liveRestoreKey

/**
 * "Remember last category — Live TV", both ways: which changes of the category are saved, and bringing
 * the saved one back. Kept apart from [LiveViewModel] so it can be tested without Android.
 *
 * Only a category the user picks is saved. A startup default, a fallback, a restore or the jump to a
 * channel's own category is not, or it would replace the category the user actually chose.
 */
@OptIn(FlowPreview::class)
internal class LiveCategoryMemory(private val scope: CoroutineScope, private val save: suspend (LiveKey) -> Unit) {

    // A channel, not a shared flow: a pick made before the saver has started is kept, not dropped.
    private val picks = Channel<LiveKey>(Channel.CONFLATED)
    private var restoreJob: Job? = null

    init {
        // Debounced: the category list selects on focus, so walking down it picks every row on the way.
        scope.launch { picks.receiveAsFlow().debounce(SAVE_DEBOUNCE_MS).collect { save(it) } }
    }

    /** The user picked [key]: saved once the focus settles, and it ends a restore still waiting. */
    fun picked(key: LiveKey) {
        restoreJob?.cancel()
        picks.trySend(key)
    }

    /** The app put a category on screen (a startup choice): it wins over a restore still waiting, unsaved. */
    fun placed() {
        restoreJob?.cancel()
    }

    /**
     * Brings the saved category back: [stored] (null with the setting off) checked against [rail], the
     * first category list complete for this profile and these playlists. The wait ends with that list,
     * whether or not the category is in it, so a large provider's slow list cannot leave it waiting for
     * ever; a pick or a startup choice ends it sooner. Not saved, and the stored value is left alone.
     */
    fun restore(stored: suspend () -> String?, rail: suspend () -> List<LiveRailItem>, show: (LiveKey) -> Unit) {
        restoreJob?.cancel()
        restoreJob = scope.launch {
            val saved = stored() ?: return@launch
            restoreKey(saved, rail())?.let(show)
        }
    }

    companion object {
        const val SAVE_DEBOUNCE_MS = 800L

        fun restoreKey(stored: String, rail: List<LiveRailItem>): LiveKey? =
            liveRestoreKey(stored, rail, { it.key }, { it.sealed })

        /**
         * Where the TV Guide opens: with "TV Guide follows Live TV" on, Live TV's remembered category
         * ([stored]), or All channels when there is none it can show; off (null), the Guide keeps its own.
         */
        fun guideOpening(follows: Boolean, stored: String?, rail: List<LiveRailItem>): LiveKey? =
            if (!follows) null else stored?.let { restoreKey(it, rail) } ?: LiveKey.All
    }
}
