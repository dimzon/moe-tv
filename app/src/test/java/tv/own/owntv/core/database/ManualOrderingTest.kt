package tv.own.owntv.core.database

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Pins the manual-order sort that replaced the old `slots` CTE (`ROW_NUMBER() OVER …`). These cases
 * reproduce what the window-function query produced, so the Kotlin port cannot silently drift — the
 * reorder feature is the app's headline manual-order behaviour.
 */
class ManualOrderingTest {
    private fun ch(id: Long, name: String, sortOrder: Int) =
        ChannelEntity(remoteId = "c" + (id).toString(), name = name, sortOrder = sortOrder)

    private fun row(id: Long, name: String, sortOrder: Int = 0, mp: Int = 0, pos: Int? = null) =
        ManualOrderRow(ch(id, name, sortOrder), pos, mp)

    private fun List<ChannelEntity>.ids() = map { it.id }

    @Test
    fun `all unmoved, playlist order follows membership then sortOrder then name`() {
        val rows = listOf(
            row(1, "Zeta", sortOrder = 5, mp = 2),
            row(2, "alpha", sortOrder = 1, mp = 0),
            row(3, "Beta", sortOrder = 9, mp = 1),
        )
        // mp order: 2(0), 3(1), 1(2)
        assertEquals(listOf("c2", "c3", "c1"), ManualOrdering.order(rows, alpha = false).ids())
    }

    @Test
    fun `all unmoved, alpha sorts by name`() {
        val rows = listOf(row(1, "Zeta"), row(2, "alpha"), row(3, "Beta"))
        // case-sensitive byte order: "Zeta"(Z=0x5A) < "alpha"(a=0x61) < "Beta"(B=0x42)? -> B < Z < a
        assertEquals(listOf("c3", "c1", "c2"), ManualOrdering.order(rows, alpha = true).ids())
    }

    @Test
    fun `moved rows come first in saved-position order, unmoved fill after`() {
        val rows = listOf(
            row(10, "a", pos = 0),
            row(11, "b", pos = 1),
            row(12, "c"),          // unmoved
            row(13, "d"),          // unmoved
        )
        // moved dense slots: pos0-rank0=0, pos1-rank1=0 (both slot 0, grp 0, tie=pos) -> 10,11;
        // unmoved slots 0,1 (grp 1) come after the grp-0 rows at slot 0.
        assertEquals(listOf("c10", "c11", "c12", "c13"), ManualOrdering.order(rows, alpha = false).ids())
    }

    @Test
    fun `gaps in saved positions collapse to a dense run`() {
        // positions 5 and 9 with rank 1,2 -> slots 5-0=5 and 9-1=8; unmoved fill 0..; the moved rows
        // land at their compacted slots, interleaving with the unmoved ones by slot.
        val rows = listOf(
            row(1, "u1"),          // unmoved -> slot 0
            row(2, "u2"),          // unmoved -> slot 1
            row(3, "movedA", pos = 5),  // slot 5
            row(4, "movedB", pos = 9),  // slot 8
        )
        val ids = ManualOrdering.order(rows, alpha = false).ids()
        assertEquals(listOf("c1", "c2", "c3", "c4"), ids)
    }

    @Test
    fun `moved tie-break by id when positions collide`() {
        val rows = listOf(row(20, "x", pos = 3), row(19, "y", pos = 3))
        // same pos, rank by (pos, id): 19 before 20 -> both slot 3-rank+1; 19 rank1 slot3, 20 rank2 slot2?
        // 19: pos3-(1-1)=3; 20: pos3-(2-1)=2 -> slot2(20) before slot3(19).
        assertEquals(listOf("c20", "c19"), ManualOrdering.order(rows, alpha = false).ids())
    }
}
