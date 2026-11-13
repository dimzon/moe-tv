package tv.own.owntv.core.database

import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * The M:N visibility rule that replaced the old single `channels.categoryId` checks, plus the shared
 * snapshot the rule reads.
 *
 * Before the redesign a channel had one category, so "is this channel hidden/sealed" was
 * `categoryId == null || categoryId !in hidden`. Now a channel belongs to many genres, and (per the
 * redesign decision) it is hidden from a list only when **every** one of its categories is hidden —
 * a channel still reachable through a visible genre stays visible. A channel with no categories is
 * always visible, mirroring the old `categoryId == null` keep.
 *
 * [snapshot] (`hex → category ids`) is kept current by [ChannelMembershipStore], which observes
 * `channel_category`. Reading it as a static lets the many pure helpers and view models apply the
 * rule without threading the map through every signature. It changes only when a RuTube sync rewrites
 * the link table.
 */
object ChannelMembership {
    @Volatile
    var snapshot: Map<String, Set<Long>> = emptyMap()

    /** `hexId → its category ids`, from raw membership rows. */
    fun mapOf(rows: List<ChannelCategoryEntity>): Map<String, Set<Long>> =
        rows.groupBy({ it.channelHexId }, { it.categoryId }).mapValues { it.value.toSet() }

    /** The channel's category ids, or null when the map has no entry (never synced / no genres). */
    fun catsOf(channel: ChannelEntity): Set<Long>? = snapshot[channel.remoteId]

    /**
     * The channel's "primary" category — a stable stand-in for the old single `channels.categoryId`
     * used by folder keys / display names / peek targets. Picks the lowest id so it is deterministic
     * across syncs (the old column was the first genre by sort order; min-id is the stable equivalent
     * now that a channel has many). Null when the channel has no categories.
     */
    fun primaryCat(channel: ChannelEntity): Long? = catsOf(channel)?.minOrNull()

    /**
     * Kept unless EVERY category is in [excluded]. No categories (null or empty) → kept, matching the
     * old `categoryId == null` keep.
     */
    fun visible(channel: ChannelEntity, excluded: Set<Long>): Boolean {
        val cats = catsOf(channel)
        return cats.isNullOrEmpty() || cats.any { it !in excluded }
    }

    /** True when the channel has categories and all of them are in [excluded] (hidden / sealed). */
    fun allExcluded(channel: ChannelEntity, excluded: Set<Long>): Boolean {
        val cats = catsOf(channel)
        return cats != null && cats.isNotEmpty() && cats.all { it in excluded }
    }

    /** Whether the channel airs in [categoryId] — the in-memory twin of the junction-filtered SQL lists. */
    fun holds(channel: ChannelEntity, categoryId: Long): Boolean = catsOf(channel)?.contains(categoryId) == true
}
