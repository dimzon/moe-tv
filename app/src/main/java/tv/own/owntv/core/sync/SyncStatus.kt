package tv.own.owntv.core.sync

import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

/**
 * Per-section choice on Add (Now / Later / Off). Edit uses only On(=Now) / Off — now-vs-later
 * only matters during the first import.
 */
enum class SyncScopeChoice {
    Now,
    Later,
    Off,
    ;

    companion object {
        fun parse(raw: String?, default: SyncScopeChoice = Now): SyncScopeChoice = when (raw?.trim()?.lowercase()) {
            "now", "true", "on", "1", "yes" -> Now
            "later" -> Later
            "off", "false", "0", "no" -> Off
            else -> default
        }
    }
}

/** Live source-sync progress reported as per-content counters. */
data class ImportStage(
    val liveProcessed: Int = 0,
    val liveActive: Boolean = false,
    /**
     * Which stream the connection measurement is opening, and how many it may open, or 0/0 when it
     * is not running.
     *
     * Carried on the ordinary progress object so the measurement appears on whatever screen is
     * already watching a sync — it runs *before* a single row is written and can take two minutes,
     * which without a word on screen is indistinguishable from the app having hung.
     */
    val measuringStream: Int = 0,
    val measuringOf: Int = 0,
) {
    val measuringConnections: Boolean get() = measuringOf > 0

    val totalProcessed: Int
        get() = liveProcessed
}

/** Xtream / M3U progress is reported in the live phase. */
enum class SyncPhase {
    LIVE,
}

/** Terminal result of a sync run. */
sealed interface SyncResult {
    data class Success(
        val warnings: List<SyncWarning> = emptyList(),
        /** Category churn on a resync (always 0 on a source's first sync, where everything is "new"). */
        val categoriesAdded: Int = 0,
        val categoriesRemoved: Int = 0,
    ) : SyncResult

    data object Cancelled : SyncResult
    data class Failed(val message: String) : SyncResult
}

sealed interface SyncWarningKind {
    data object GENERIC : SyncWarningKind
    data object PAGE_FAILURE : SyncWarningKind
    data class CATALOG_SHRINK(val stored: Int, val percentFewer: Int) : SyncWarningKind

    /**
     * The provider's bulk list stopped part-way and the per-category fallback finished the job.
     *
     * Measured on a real playlist: the bulk call ran for 242 seconds and the peer sent a connection
     * reset part-way through. The catalogue still ended up complete, because the fallback recovered
     * it — but it took ten minutes instead of thirty-six seconds, and *nothing said so*. The run
     * reported `Success`, and the only trace was a warning under a log tag nobody was filtering on.
     * That silence is the defect this kind exists to end.
     *
     * [recovered] says whether the fallback got everything back, which is what decides whether the
     * user is looking at a slow sync or a short catalogue.
     */
    data class BULK_TRUNCATED(val itemsBeforeCutoff: Int, val recovered: Boolean) : SyncWarningKind
}

data class SyncWarning(
    val phase: String,
    /** Raw provider/exception text, only used for an otherwise-unclassified phase error. */
    val message: String = "",
    val kind: SyncWarningKind = SyncWarningKind.GENERIC,
    val count: Int = 0,
)

/**
 * Which sections of a source's catalogue a sync pass covers. The app is live-only now, so there is
 * exactly one section: live channels. The `movies`/`series` sections and their persisted
 * `sources.syncMovies`/`syncSeries` columns are retired (v57) — the columns stay in the schema for
 * backup round-tripping and are simply never read.
 */
data class SyncContentTypes(
    val live: Boolean = true,
) {
    val hasAny: Boolean get() = live

    /** enabled - priority: the background remainder AFTER the foreground pass (enabled-relative). */
    fun remainderAfter(priority: SyncContentTypes) = SyncContentTypes(
        live = !priority.live && live,
    )

    /** True iff this pass covered every section in [target]. Compare against [enabledFor], NOT [enabledOf]. */
    fun isCompleteFor(target: SyncContentTypes): Boolean = !target.live || live

    /** The single derivation used at every sync boundary: request ∩ enabled. */
    fun effectiveFor(source: SourceEntity): SyncContentTypes = intersect(enabledOf(source))

    private fun intersect(other: SyncContentTypes) = SyncContentTypes(live = live && other.live)

    companion object {
        /** Raw persisted enabledScope (Off = false). */
        fun enabledOf(s: SourceEntity) = SyncContentTypes(s.syncLive)

        /** enabledScope — the completion TARGET. */
        fun enabledFor(s: SourceEntity) = SyncContentTypes(s.syncLive)

        fun fromChoices(live: SyncScopeChoice) = SyncContentTypes(live = live != SyncScopeChoice.Off)

        fun priorityFromChoices(live: SyncScopeChoice) = SyncContentTypes(live = live == SyncScopeChoice.Now)
    }
}

data class SyncRunStats(
    val sourceId: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val result: SyncResult,
    val phaseTiming: Map<String, Long>,
    val processedCounts: Map<String, Int>,
    val phaseErrors: Map<String, String>,
    val usedFallback: Boolean,
)
