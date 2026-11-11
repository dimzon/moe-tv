package tv.own.myowntv.features.shell
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.categorylock.lockAwareCount
import tv.own.owntv.core.categorylock.sealedCategoryIds

/** The rail's details lines (Size = Extra wide), as data; the shell turns them into text. */
data class RailDetails(
    val lastChannel: String? = null,
)

/**
 * The number beside Live TV in the open Stage rail, for the playlists being shown ("All playlists"
 * or the one picked), and the details line under it. Titles in adult or locked categories stay
 * hidden exactly as in the Continue pill: the count leaves out what a locked category holds, and
 * both follow a lock closing or opening, not only new history. Sources are global: every screen
 * counts the same set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RailCountsViewModel(
    channelDao: ChannelDao,
    settings: SettingsRepository,
    sourceDao: SourceDao,
    historyDao: HistoryDao,
    categoryDao: CategoryDao,
    customize: CustomizationStore,
) : ViewModel() {

    val counts: StateFlow<Map<MainSection, Int>> =
        combine(sourceDao.observeAll(), settings.defaultSourceId) { sources, defaultId ->
            val visible = if (defaultId > 0 && sources.any { it.id == defaultId }) {
                sources.filter { it.id == defaultId }
            } else {
                sources
            }
            // The live-only app surfaces only sources whose live sync scope is on. An empty id list
            // would make the IN clause meaningless; -1 matches nothing.
            visible.filter { it.syncLive }.map { it.id }.ifEmpty { listOf(-1L) }
        }
        .flatMapLatest { ids ->
            lockAwareCount(customize.sealedCategoryIds(MediaType.LIVE), { channelDao.countAll(ids) }) {
                channelDao.countAllExcluding(ids, it)
            }.map { live -> mapOf(MainSection.LIVE_TV to live) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val details: StateFlow<RailDetails> =
        combine(
            historyDao.observeMostRecentOfType(MediaType.LIVE),
            customize.observe(MediaType.LIVE),
            customize.sealedCategoryIds(MediaType.LIVE),
        ) { h, cust, sealed -> Triple(h, cust, sealed) }
            .map { (h, cust, sealed) ->
                // A channel's title shows only when its category is neither hidden in Customize nor
                // sealed by a lock; with the section's customizations, so a rename shows here too.
                h?.let { channelDao.getById(it.itemId) }?.takeIf { ch ->
                    val catKey = ch.categoryId?.let { categoryDao.getById(it) }?.let(CustomizeKeys::category)
                    ch.categoryId !in sealed && (catKey == null || catKey !in cust.hiddenCategories)
                }?.let { cust.displayName(it) }
            }
            .map { RailDetails(lastChannel = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RailDetails())
}
