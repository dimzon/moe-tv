package tv.own.owntv.core.sync.local

/**
 * This device's agreement to sync by itself with one paired device: one per device, on each side.
 *
 * Since the sync-matrix redesign (phase ③) an agreement carries **no section set**: what travels is
 * decided by this device's own [tv.own.owntv.core.settings.SyncMatrix] ticks, applied on both the
 * export and the apply side of every pass. The agreement is now purely the *when* — the timing and
 * the attempt bookkeeping. The app has one user, so an agreement pairs the two DEVICES outright:
 * their data is merged into this device's single viewer, whatever the other device is called.
 *
 * [lastSuccessAt] moves only when a pass completed; a failed or unreachable try moves [lastAttemptAt]
 * and [failures] only, so the next try backs off without pretending anything was synced.
 */
data class AutoSyncAgreement(
    val deviceId: String,
    val timing: LocalAutoSyncTiming = LocalAutoSyncTiming.DEFAULT,
    val lastAttemptAt: Long = 0,
    val lastSuccessAt: Long = 0,
    val failures: Int = 0,
) {
    /**
     * The earliest moment the next pass may start. With no failure, [timing]'s gap after the last
     * success; after failures, also not before the backoff after the last try — 15 minutes doubled
     * per failure, never more than the gap itself (and never more than a day).
     */
    fun dueAt(): Long {
        // Never synced: the first chance is the first pass, whatever the timing.
        val afterSuccess = if (lastSuccessAt == 0L) 0L else lastSuccessAt + timing.gapMs
        if (failures <= 0) return afterSuccess
        return maxOf(afterSuccess, lastAttemptAt + backoffMs(failures, timing.gapMs))
    }

    fun isDue(now: Long): Boolean = dueAt() <= now

    /** After one try: a success clears the failures, anything else counts one more. */
    fun afterAttempt(at: Long, success: Boolean): AutoSyncAgreement =
        if (success) copy(lastAttemptAt = at, lastSuccessAt = at, failures = 0)
        else copy(lastAttemptAt = at, failures = failures + 1)

    companion object {
        private const val BACKOFF_STEP_MS = 15 * 60_000L
        private const val BACKOFF_CAP_MS = 24 * 3600_000L

        /** 15 minutes doubled per failure in a row, capped by [gapMs] (when it has one) and by a day. */
        fun backoffMs(failures: Int, gapMs: Long): Long {
            val doubled = BACKOFF_STEP_MS shl (failures - 1).coerceIn(0, 10)
            val ceiling = if (gapMs > 0) minOf(gapMs, BACKOFF_CAP_MS) else BACKOFF_CAP_MS
            return minOf(doubled, ceiling).coerceAtLeast(BACKOFF_STEP_MS)
        }
    }
}
