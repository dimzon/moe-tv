package tv.own.myowntv.features.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.categorylock.lockAwareCount
import tv.own.owntv.core.categorylock.sealedCategoryIds

/** The rail's details lines (Size = Extra wide), as data; the shell turns them into text. */
data class RailDetails(
    val lastChannel: String? = null,
)

/**
 * The number beside Live TV in the open Stage rail, for the built-in RuTube source, and the details
 * line under it. Titles in adult or locked categories stay
 * hidden exactly as in the Continue pill: the count leaves out what a locked category holds, and
 * both follow a lock closing or opening, not only new history. Sources are global: every screen
 * counts the same set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RailCountsViewModel(
    channelDao: ChannelDao,
    historyDao: HistoryDao,
    categoryDao: CategoryDao,
    customize: CustomizationStore,
) : ViewModel() {

    val counts: StateFlow<Map<MainSection, Int>> =
        lockAwareCount(customize.sealedCategoryIds(MediaType.LIVE), { channelDao.countAll() }) {
            channelDao.countAllExcluding(it)
        }.map { live -> mapOf(MainSection.LIVE_TV to live) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val details: StateFlow<RailDetails> =
        combine(
            historyDao.observeMostRecent(),
            customize.observe(MediaType.LIVE),
            customize.sealedCategoryIds(MediaType.LIVE),
        ) { h, cust, sealed -> Triple(h, cust, sealed) }
            .map { (h, cust, sealed) ->
                // The title shows only when the channel is reachable through at least one open
                // category and none of its categories is hidden in Customize; with the section's
                // customizations, so a rename shows here too.
                h?.let { channelDao.getById(it.itemId) }
                    ?.takeIf { ch ->
                        canShowLastChannelTitle(ch, sealed, cust.hiddenCategories) { cid ->
                            categoryDao.getById(cid)?.let(CustomizeKeys::category)
                        }
                    }
                    ?.let { cust.displayName(it) }
            }
            .map { RailDetails(lastChannel = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RailDetails())
}
