package tv.own.owntv.core.sync

data class SyncProgressCounts(
    val live: Int,
    val liveActive: Boolean,
) {
    val hasItems: Boolean
        get() = live > 0
}

enum class SyncProgressPhase { PREPARING, CONNECTING, SYNCING }

data class SyncProgressDisplay(
    val counts: SyncProgressCounts?,
    val phase: SyncProgressPhase,
)

fun ImportStage.progressCounts(): SyncProgressCounts = SyncProgressCounts(
    live = liveProcessed,
    liveActive = liveActive,
)

fun ImportStage.importProgressDisplay(): SyncProgressDisplay =
    importProgressDisplay(progressCounts())

fun importProgressDisplay(counts: SyncProgressCounts?): SyncProgressDisplay = SyncProgressDisplay(
    counts = counts,
    phase = when {
        counts == null -> SyncProgressPhase.PREPARING
        counts.hasItems -> SyncProgressPhase.SYNCING
        else -> SyncProgressPhase.CONNECTING
    },
)

fun resyncProgressPercent(baseItemCount: Int, totalProcessed: Int): Int? =
    if (baseItemCount > 0 && totalProcessed > 0) {
        ((totalProcessed * 100L) / baseItemCount).coerceIn(1, 99).toInt()
    } else {
        null
    }
