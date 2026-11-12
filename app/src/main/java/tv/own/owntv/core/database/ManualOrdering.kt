package tv.own.owntv.core.database

import androidx.room.Embedded
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * One channel plus the two inputs the manual-order sort needs, as read by the base queries.
 * [pos] is the saved `content_order.position` (null = never moved); [mp] is the membership position
 * (0 for provider folders, the custom-category member's own position otherwise).
 */
data class ManualOrderRow(
    @Embedded val channel: ChannelEntity,
    val pos: Int?,
    val mp: Int,
)

/**
 * The manual (Move) ordering, in plain Kotlin instead of the old `slots` CTE.
 *
 * The CTE used `ROW_NUMBER() OVER (…)`, a window function that the system SQLite lacks below 3.25 —
 * so it forced the bundled engine. This is the same rule, dependency-free (runs on the system SQLite
 * at minSdk 26):
 *  - moved rows (a saved [pos]) keep a dense run of their positions — `pos − rank + 1` — so a block of
 *    consecutive moves stays contiguous and gaps left by deletions collapse;
 *  - unmoved rows fill the remaining slots in [alpha] (A–Z) or membership/sortOrder order;
 *  - the two sets interleave by that slot, moved before unmoved on a tie, moved by their saved pos.
 *
 * Pure and total on the input, so the browse PagingSource and the Move-session snapshot order
 * identically — one source of truth, unit-testable without a database.
 */
object ManualOrdering {
    fun order(rows: List<ManualOrderRow>, alpha: Boolean): List<ChannelEntity> {
        // (slot, group, tie) is the sort key the old query's final ORDER BY used.
        class Keyed(val channel: ChannelEntity, val slot: Int, val grp: Int, val tie: Int)

        val moved = rows.filter { it.pos != null }
            .sortedWith(compareBy({ it.pos }, { it.channel.id }))
            .mapIndexed { i, r -> Keyed(r.channel, r.pos!! - i, 0, r.pos) }

        // Unmoved rank key: when alpha, name first; otherwise the CASE yields NULL for every row and
        // the tie-breaks (membership position, provider sortOrder, name) decide — same as the SQL.
        val unmoved = rows.filter { it.pos == null }
            .sortedWith(
                compareBy({ if (alpha) it.channel.name else "" }, { it.mp }, { it.channel.sortOrder }, { it.channel.name }),
            )
            .mapIndexed { i, r -> Keyed(r.channel, i, 1, 0) }

        return (moved + unmoved)
            .sortedWith(compareBy({ it.slot }, { it.grp }, { it.tie }))
            .map { it.channel }
    }
}
