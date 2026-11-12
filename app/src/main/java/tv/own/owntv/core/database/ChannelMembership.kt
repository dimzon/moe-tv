package tv.own.owntv.core.database

/**
 * The M:N visibility rule that replaced the old single `channels.categoryId` checks.
 *
 * Before the redesign a channel had one category, so "is this channel hidden/sealed" was
 * `categoryId == null || categoryId !in hidden`. Now a channel belongs to many genres, and (per the
 * redesign decision) it is hidden from the common lists only when **every** one of its categories is
 * hidden — a channel still reachable through a visible genre stays visible. A channel with no
 * categories at all is always visible, mirroring the old `categoryId == null` keep.
 *
 * The map is built once per filter pass from [ChannelCategoryDao.allMemberships] (a few hundred rows
 * for ~250 channels) so the many in-memory `Flow.map` filters ask the map instead of the DB per row.
 */
object ChannelMembership {
    /** `hexId → its category ids`, from the raw membership rows. */
    fun mapOf(rows: List<ChannelCategoryEntity>): Map<String, Set<Long>> =
        rows.groupBy({ it.channelHexId }, { it.categoryId }).mapValues { it.value.toSet() }

    /**
     * Whether a channel is visible given the set of hidden/sealed category ids [excluded].
     * [categories] is the channel's category set from [mapOf] (null/empty = no genres).
     */
    fun visible(categories: Set<Long>?, excluded: Set<Long>): Boolean =
        categories.isNullOrEmpty() || categories.any { it !in excluded }
}
