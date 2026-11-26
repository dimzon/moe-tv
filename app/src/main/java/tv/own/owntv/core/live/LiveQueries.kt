package tv.own.owntv.core.live

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.seals
import tv.own.owntv.core.database.ManualOrderPagingSource
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Which query backs a Live TV rail selection, and which channels survive the user's customizations.
 *
 * Split out of the view models: these are pure functions of their arguments — every piece of state they
 * once read off the view model (the folder context keys, the contexts that carry a manual
 * order) is now passed in, so there is nothing here to get out of step with a tune.
 */

/** The paging query for one rail selection. [contextKey] is the folder's stable customization key and
 *  [hasManualOrder] says whether that folder actually carries manual-order rows — see the fast path. */
fun livePagingSource(
    key: LiveKey,
    query: String,
    sort: SettingsRepository.SortMode,
    channelDao: ChannelDao,
    customCategoryDao: CustomCategoryDao,
    contextKey: (Long) -> String?,
    hasManualOrder: (String) -> Boolean,
): PagingSource<Int, ChannelEntity> {
    val playlist = sort == SettingsRepository.SortMode.PLAYLIST
    return if (query.isBlank()) {
        when (key) {
            LiveKey.All -> if (playlist) channelDao.pagingAllOriginal() else channelDao.pagingAll()
            LiveKey.Favorites -> channelDao.pagingFavoritesManual(ContentOrderEntity.FAV_CONTEXT)
            LiveKey.History -> channelDao.pagingHistory()
            is LiveKey.Custom -> ManualOrderPagingSource({ customCategoryDao.baseChannelsManual(key.id) }, alpha = !playlist)
            is LiveKey.Folder -> {
                val ctxKey = contextKey(key.id) ?: ""
                // Fast path: no manual order in this folder → the plain indexed query has
                // the identical (sortOrder, name) order without the join-sort.
                if (!hasManualOrder(ctxKey)) {
                    if (playlist) channelDao.pagingByCategory(key.id) else channelDao.pagingByCategoryAlpha(key.id)
                } else {
                    ManualOrderPagingSource({ channelDao.baseByCategoryManual(key.id, ctxKey) }, alpha = !playlist)
                }
            }
        }
    } else {
        when (key) {
            LiveKey.All -> channelDao.searchAll(query)
            LiveKey.Favorites -> channelDao.searchFavorites(query)
            LiveKey.History -> channelDao.searchHistory(query)
            is LiveKey.Custom -> customCategoryDao.searchChannels(query, key.id)
            is LiveKey.Folder -> channelDao.searchInCategory(query, key.id)
        }
    }
}

/**
 * The live row count for one rail selection. [sealedCats] are the locked provider folders: unlike
 * [hiddenCats] they leave Favorites, History and custom categories too (see [isChannelVisible]).
 */
fun liveCountFlow(
    key: LiveKey,
    hiddenCats: Set<Long>,
    channelDao: ChannelDao,
    customCategoryDao: CustomCategoryDao,
    sealedCats: Set<Long> = emptySet(),
): Flow<Int> {
    val sealed = sealedCats.toList()
    return when (key) {
        LiveKey.All -> if (hiddenCats.isEmpty()) channelDao.countAll() else channelDao.countAllExcluding(hiddenCats.toList())
        LiveKey.Favorites -> if (sealed.isEmpty()) channelDao.countFavorites()
            else channelDao.countFavoritesExcluding(sealed)
        LiveKey.History -> if (sealed.isEmpty()) channelDao.countHistory()
            else channelDao.countHistoryExcluding(sealed)
        is LiveKey.Custom -> if (sealed.isEmpty()) customCategoryDao.countMembers(key.id)
            else customCategoryDao.countMembersExcluding(key.id, sealed)
        is LiveKey.Folder -> channelDao.countByCategory(key.id)
    }
}

/**
 * A channel the user has not hidden, in a category they have not hidden.
 *
 * A hidden provider category hides its channels only where they appear *because of* that category.
 * In a list the user built — a custom category, Favorites, History — the channel is there on its own
 * merit, so hiding its provider folder must not empty it (the Guide already worked this way). A
 * locked category is stricter: its channels stay out of every list until the PIN opens it.
 */
fun isChannelVisible(
    ch: ChannelEntity,
    cust: SectionCustomizations,
    hiddenCats: Set<Long>,
    key: LiveKey? = null,
    membership: Map<String, Set<Long>> = tv.own.owntv.core.database.ChannelMembership.snapshot,
): Boolean {
    val categories = membership[ch.remoteId]
    val sealed = !categories.isNullOrEmpty() && categories.all { it in cust.sealedCategoryIds }
    val hidden = hiddenCategoryApplies(key) && !categories.isNullOrEmpty() && categories.all { it in hiddenCats }
    return CustomizeKeys.channel(ch) !in cust.hiddenItems && !sealed && !cust.seals(key) && !hidden
}

/** Whether a hidden provider category filters the list shown for [key] (see [isChannelVisible]). */
fun hiddenCategoryApplies(key: LiveKey?): Boolean =
    key !is LiveKey.Custom && key != LiveKey.Favorites && key != LiveKey.History
