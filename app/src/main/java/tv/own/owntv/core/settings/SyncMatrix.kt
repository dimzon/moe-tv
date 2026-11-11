package tv.own.owntv.core.settings

/**
 * The 4×2 sync matrix (phase ③, PHASE3_CONTRACT decision 3): per data category, whether this device
 * **gives** its rows to the other devices (export side) and whether it **reads** theirs (apply side).
 *
 * The flags live in the local DataStore only — they are this device's own choice and are deliberately
 * NOT part of the sync payload. Both sides of a pairing send everything they have; each device gates
 * its own export with "give" and its own apply with "read". There is no per-device section
 * negotiation any more, so a pair of devices with different matrices still work — each one simply
 * moves what its own ticks allow.
 *
 * Defaults: history gives=false (watch history is private by default), read=true; every other
 * category gives and reads.
 *
 * The LWW rule underneath (see the payload's per-row `updatedAt` and
 * [tv.own.owntv.core.backup.UserDataResolver]) means a row only ever moves when the incoming copy is
 * newer: fresh local edits are never overwritten by older ones from the household.
 */
data class SyncMatrix(
    val giveFavorites: Boolean,
    val readFavorites: Boolean,
    val giveOrder: Boolean,
    val readOrder: Boolean,
    val giveHistory: Boolean,
    val readHistory: Boolean,
    val giveReminders: Boolean,
    val readReminders: Boolean,
) {
    /**
     * Whether the "give" side of [kind]'s category is on. [kind] is a sync-payload kind string
     * ([KIND_FAVORITES] / [KIND_ORDER] / [KIND_HISTORY] / [KIND_REMINDERS]); anything else —
     * a kind that left the payload, such as the old `member`/custom-category one — has no matrix
     * flag and answers false.
     */
    fun gives(kind: String): Boolean = when (kind) {
        KIND_FAVORITES -> giveFavorites
        KIND_ORDER -> giveOrder
        KIND_HISTORY -> giveHistory
        KIND_REMINDERS -> giveReminders
        else -> false
    }

    /** Whether the "read" side of [kind]'s category is on — same kinds as [gives]. */
    fun reads(kind: String): Boolean = when (kind) {
        KIND_FAVORITES -> readFavorites
        KIND_ORDER -> readOrder
        KIND_HISTORY -> readHistory
        KIND_REMINDERS -> readReminders
        else -> false
    }

    companion object {
        /** Sync-payload kind of the favorites (закладки). */
        const val KIND_FAVORITES = "fav"

        /** Sync-payload kind of the manual channel order (порядок каналов). */
        const val KIND_ORDER = "order"

        /** Sync-payload kind of the watch history (история). */
        const val KIND_HISTORY = "his"

        /** Sync-payload kind of the programme reminders (напоминания). */
        const val KIND_REMINDERS = "prog"

        /** The four kinds that make up the payload, in contract order. */
        val KINDS = setOf(KIND_FAVORITES, KIND_ORDER, KIND_HISTORY, KIND_REMINDERS)

        /**
         * What a fresh install ticks. History is private by default (give=false, read=true);
         * favorites, order and reminders give and read.
         */
        val DEFAULTS = SyncMatrix(
            giveFavorites = true,
            readFavorites = true,
            giveOrder = true,
            readOrder = true,
            giveHistory = false,
            readHistory = true,
            giveReminders = true,
            readReminders = true,
        )
    }
}
