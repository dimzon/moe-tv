package tv.own.owntv.core.epg

import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/**
 * Which guide wins when several sources carry the same channel. The app has one guide store now
 * (schema v5 dropped `sourceId`), so there is nothing to rank: [resolve] is [EpgDedupe] alone. The
 * class survives as the call sites' boundary, and so a future second store has one obvious place to
 * settle overlaps.
 */
class GuidePriority private constructor() {

    /** [rows] for one channel, plainly-identical broadcasts collapsed, in start order. */
    fun resolve(rows: List<EpgProgrammeEntity>): List<EpgProgrammeEntity> =
        if (rows.size < 2) rows else EpgDedupe.collapse(rows)

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun of(sourceIds: List<Long>): GuidePriority = GuidePriority()
    }
}
