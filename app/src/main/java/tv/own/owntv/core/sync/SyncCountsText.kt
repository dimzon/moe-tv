package tv.own.owntv.core.sync
import tv.own.owntv.core.i18n.firstLocaleCompat

import android.content.res.Resources
import android.icu.text.CompactDecimalFormat
import tv.own.myowntv.R

/**
 * The words that go with an import's numbers: how much arrived, what went wrong on the way, and what
 * is still queued. Both apps show the same sentences over the same [SyncCounts], so they are written
 * once here and each app decides only where to put them.
 */

/** "12K channels" — the parts a caller wants, joined. Empty when nothing arrived. */
fun SyncCounts.breakdownText(res: Resources, includeEpg: Boolean = false): String {
    val parts = buildList {
        if (channels > 0) add(res.getQuantityString(R.plurals.sync_count_channels, channels, compactCount(res, channels)))
        if (includeEpg && epg > 0) add(res.getQuantityString(R.plurals.sync_count_epg, epg, compactCount(res, epg)))
    }
    return parts.joinToString(res.getString(R.string.sync_counts_separator))
}

/** The one line under "All set!". A sync that reports no numbers still succeeded, and says so. */
fun SyncCounts.summaryText(res: Resources, includeEpg: Boolean = false): String {
    val breakdown = breakdownText(res, includeEpg)
    return if (breakdown.isBlank()) {
        res.getString(R.string.sync_counts_success)
    } else {
        res.getString(R.string.sync_counts_synced, breakdown)
    }
}

/** The live tally while the import runs — only the phases that are actually running. */
fun SyncProgressCounts.displayText(res: Resources): String {
    val parts = buildList {
        if (liveActive && live > 0) add(res.getQuantityString(R.plurals.sync_count_channels, live, compactCount(res, live)))
    }
    return parts.joinToString(res.getString(R.string.sync_counts_separator))
}

/**
 * Compact notation keeps the pill readable without losing the raw plural quantity.
 * `CompactDecimalFormat` is API 24+; on Android 6 plain grouping is used instead (e.g. "12,345"
 * rather than "12K") — still readable, never a crash.
 */
fun compactCount(res: Resources, value: Int): String {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
        return java.text.NumberFormat.getIntegerInstance(res.configuration.firstLocaleCompat()).format(value.toLong())
    }
    val locale = res.configuration.firstLocaleCompat()
    return CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
        .format(value.toLong())
}

fun SyncProgressDisplay.primaryText(res: Resources): String = when (phase) {
    SyncProgressPhase.PREPARING -> res.getString(R.string.sync_progress_preparing)
    SyncProgressPhase.CONNECTING -> res.getString(R.string.sync_progress_connecting)
    SyncProgressPhase.SYNCING ->
        counts?.displayText(res).orEmpty().ifBlank { res.getString(R.string.sync_progress_preparing) }
}

fun SyncProgressDisplay.detailText(res: Resources): String = when (phase) {
    SyncProgressPhase.SYNCING -> res.getString(R.string.sync_progress_syncing)
    SyncProgressPhase.PREPARING, SyncProgressPhase.CONNECTING ->
        res.getString(R.string.sync_progress_connecting)
}

fun SyncWarning.labelText(res: Resources): String = when (phase.trim().uppercase()) {
    SyncPhase.LIVE.name -> res.getString(R.string.sync_phase_live)
    else -> phase.replaceFirstChar { it.uppercase() }
}

/** Everything the sync survived but wants to mention, as one sentence. Null when it was clean. */
fun List<SyncWarning>.warningText(res: Resources): String? {
    if (isEmpty()) return null
    val rendered = ArrayList<String>(size)
    for (warning in this) {
        rendered += when (val kind = warning.kind) {
            SyncWarningKind.PAGE_FAILURE ->
                res.getQuantityString(R.plurals.sync_warning_page_failures, warning.count, warning.count)
            SyncWarningKind.GENERIC -> if (warning.message.isBlank()) {
                warning.labelText(res)
            } else {
                res.getString(R.string.sync_warning_phase_error, warning.labelText(res), warning.message)
            }
            is SyncWarningKind.CATALOG_SHRINK -> res.getQuantityString(
                R.plurals.sync_warning_catalog_shrink,
                kind.stored,
                kind.stored,
                kind.percentFewer,
            )
        }
    }
    return res.getString(
        R.string.sync_import_warnings,
        rendered.joinToString(res.getString(R.string.sync_counts_separator)),
    )
}

fun SyncContentTypes.remainderText(res: Resources): String? =
    if (!hasAny) null else res.getString(R.string.sync_remainder_note)
