package tv.own.owntv.core.live

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.seals
import tv.own.owntv.core.customize.sealsItemIn
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.model.MediaType
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
    sourceIds: List<Long>,
    query: String,
    sort: SettingsRepository.SortMode,
    channelDao: ChannelDao,
    customCategoryDao: CustomCategoryDao,
    contextKey: (Long) -> String?,
    hasManualOrder: (String) -> Boolean,
): PagingSource<Int, ChannelEntity> {
    val ids = sourceIds.ifEmpty { listOf(-1L) }
    val playlist = sort == SettingsRepository.SortMode.PLAYLIST
    return if (query.isBlank()) {
        when (key) {
            LiveKey.All -> if (playlist) channelDao.pagingAllOriginal(ids) else channelDao.pagingAll(ids)
            LiveKey.Favorites -> channelDao.pagingFavoritesManual(ContentOrderEntity.FAV_CONTEXT, ids)
            LiveKey.History -> channelDao.pagingHistory(ids)
            is LiveKey.Custom -> customCategoryDao.pagingChannels(key.id, ids, alpha = !playlist)
            is LiveKey.Folder -> {
                val ctxKey = contextKey(key.id) ?: ""
                // Fast path: no manual order in this folder → the plain indexed query has
                // the identical (sortOrder, name) order without the join-sort.
                if (!hasManualOrder(ctxKey)) {
                    if (playlist) channelDao.pagingByCategory(key.id) else channelDao.pagingByCategoryAlpha(key.id)
                } else {
                    channelDao.pagingByCategoryManual(key.id, ctxKey, alpha = !playlist)
                }
            }
        }
    } else {
        when (key) {
            LiveKey.All -> channelDao.searchAll(query, ids)
            LiveKey.Favorites -> channelDao.searchFavorites(query, ids)
            LiveKey.History -> channelDao.searchHistory(query, ids)
            is LiveKey.Custom -> customCategoryDao.searchChannels(query, key.id, ids)
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
    sourceIds: List<Long>,
    hiddenCats: Set<Long>,
    channelDao: ChannelDao,
    customCategoryDao: CustomCategoryDao,
    sealedCats: Set<Long> = emptySet(),
): Flow<Int> {
    val ids = sourceIds.ifEmpty { listOf(-1L) }
    val sealed = sealedCats.toList()
    return when (key) {
        LiveKey.All -> if (hiddenCats.isEmpty()) channelDao.countAll(ids) else channelDao.countAllExcluding(ids, hiddenCats.toList())
        LiveKey.Favorites -> if (sealed.isEmpty()) channelDao.countFavorites(ids)
            else channelDao.countFavoritesExcluding(ids, sealed)
        LiveKey.History -> if (sealed.isEmpty()) channelDao.countHistory(ids)
            else channelDao.countHistoryExcluding(ids, sealed)
        is LiveKey.Custom -> if (sealed.isEmpty()) customCategoryDao.countMembers(MediaType.LIVE, key.id, ids)
            else customCategoryDao.countMembersExcluding(MediaType.LIVE, key.id, ids, sealed)
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
fun isChannelVisible(ch: ChannelEntity, cust: SectionCustomizations, hiddenCats: Set<Long>, key: LiveKey? = null): Boolean =
    CustomizeKeys.channel(ch) !in cust.hiddenItems && !cust.sealsItemIn(ch.categoryId) && !cust.seals(key) &&
        (!hiddenCategoryApplies(key) || ch.categoryId == null || ch.categoryId !in hiddenCats)

/** Whether a hidden provider category filters the list shown for [key] (see [isChannelVisible]). */
fun hiddenCategoryApplies(key: LiveKey?): Boolean =
    key !is LiveKey.Custom && key != LiveKey.Favorites && key != LiveKey.History
