package tv.own.myowntv.features.shell

import tv.own.owntv.core.database.ChannelMembership
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Whether the last-watched channel's name may be shown on the Live rail.
 *
 * Two independent reasons hide it: the channel is reachable through no open category (all sealed),
 * or every category it belongs to was hidden in Customize. This is the same M:N rule as
 * [ChannelMembership.visible] elsewhere — one open, non-hidden category is enough.
 *
 * [categoryKeyOf] resolves a category's DB id to its stable Customize key; it is passed in so this
 * rule can be unit-tested without a CategoryDao.
 */
internal suspend fun canShowLastChannelTitle(
    channel: ChannelEntity,
    sealedCategoryIds: Set<Long>,
    hiddenCategoryKeys: Set<String>,
    categoryKeyOf: suspend (Long) -> String?,
): Boolean {
    val categories = ChannelMembership.catsOf(channel).orEmpty()
    return ChannelMembership.visible(channel, sealedCategoryIds) &&
        (categories.isEmpty() || categories.any { id ->
            // An unresolved category is treated as open, matching ChannelMembership.visible's
            // handling of ids that are not in the excluded set.
            categoryKeyOf(id)?.let { it !in hiddenCategoryKeys } ?: true
        })
}
