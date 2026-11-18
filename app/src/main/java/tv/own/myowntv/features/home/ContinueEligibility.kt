package tv.own.myowntv.features.home

import tv.own.owntv.core.database.ChannelMembership
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Whether a watched channel may back the Home "Continue watching" chip.
 *
 * The M:N visibility rule is: a channel is hidden **only when every one of its categories is
 * sealed**. A channel reachable through at least one open category stays eligible, and a channel
 * with no categories at all is never sealed — the same rule [ChannelMembership.visible] applies
 * everywhere else in the app.
 *
 * Kept as a pure top-level function so the rule can be unit-tested without constructing a
 * ViewModel and six repositories.
 */
internal fun canContinueWatching(
    channel: ChannelEntity,
    sealedCategoryIds: Set<Long>,
): Boolean = !ChannelMembership.allExcluded(channel, sealedCategoryIds)
