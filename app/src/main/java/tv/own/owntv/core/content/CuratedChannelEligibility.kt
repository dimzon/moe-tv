package tv.own.owntv.core.content

import tv.own.owntv.core.database.ChannelMembership
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Whether a channel may appear in a curated (before-the-user-types) search list.
 *
 * Curated lists keep user-hidden categories — they only filter out channels that are unreachable
 * because every one of their categories is sealed by a lock. That is the same M:N rule the rest of
 * the app applies; a channel with no categories is never sealed.
 *
 * Pure and top-level so the predicate is unit-testable without a DAO or a ViewModel.
 */
internal fun isCuratedChannel(
    channel: ChannelEntity,
    liveSourceIds: Collection<Long>,
    sealedCategoryIds: Set<Long>,
): Boolean = channel.sourceId in liveSourceIds &&
    ChannelMembership.visible(channel, sealedCategoryIds)
