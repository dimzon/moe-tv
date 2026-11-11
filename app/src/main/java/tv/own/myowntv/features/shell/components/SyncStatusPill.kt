package tv.own.myowntv.features.shell.components
import kotlinx.coroutines.delay
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.tv.material3.Text
import tv.own.owntv.core.sync.EpgActivityTracker
import tv.own.owntv.core.sync.SyncActivityTracker
import tv.own.myowntv.R
import tv.own.myowntv.ui.stage.stageGlass
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.stageText
import tv.own.myowntv.ui.theme.mpx
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.util.classifySyncFailure
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.ui.components.OwnTVSpinner
import tv.own.myowntv.ui.components.compactCount

/**
 * Unobtrusive "sync running" pill (shell overlay, bottom middle). Reflects live catalog syncs
 * (a backgrounded first import, auto refresh — all funnel through SyncManager →
 * [SyncActivityTracker]) AND EPG/guide syncs (manual resync from Settings, auto startup
 * refresh — [EpgSyncWorker] → [EpgActivityTracker]). Semi-transparent, never focusable, renders
 * nothing when no sync is active. The shell hides it during fullscreen playback.
 *
 * **One line per running sync.** It used to show a single line for whichever sync happened to be
 * first, with the rest collapsed into "(+2 more) · EPG too" — so with a playlist and a guide running
 * together you could see neither one's progress. Two playlists syncing at once were entirely
 * indistinguishable. Now every active sync gets its own row with its own counter, capped at
 * [MAX_ROWS] so a many-source setup can't grow the overlay across the screen.
 */
@Composable
fun SyncStatusPill(modifier: Modifier = Modifier) {
    val catalogTracker: SyncActivityTracker = koinInject()
    val epgTracker: EpgActivityTracker = koinInject()
    val connectivity: ConnectivityObserver = koinInject()
    val activeCatalog by catalogTracker.active.collectAsStateWithLifecycle()
    val activeEpg by epgTracker.active.collectAsStateWithLifecycle()
    val lastCompleted by catalogTracker.lastCompleted.collectAsStateWithLifecycle()
    val sportsTracker: tv.own.owntv.core.sports.SportsActivityTracker = koinInject()
    val sportsRebuild by sportsTracker.rebuild.collectAsStateWithLifecycle()
    // A failed rebuild is said once, for 5 s, when it happens — not again each time the pill returns.
    var sportsSeen by remember { mutableStateOf(sportsRebuild) }
    var sportsFailed by remember { mutableStateOf(false) }
    LaunchedEffect(sportsRebuild) {
        val was = sportsSeen
        sportsSeen = sportsRebuild
        if (sportsRebuild == tv.own.owntv.core.sports.RebuildState.Failed && was is tv.own.owntv.core.sports.RebuildState.Running) {
            sportsFailed = true
            delay(5000)
            sportsFailed = false
        }
    }

    val completedQueue = remember { mutableStateListOf<SyncActivityTracker.CompletedSync>() }
    var currentCompleted by remember { mutableStateOf<SyncActivityTracker.CompletedSync?>(null) }

    val anyActive = activeCatalog.isNotEmpty() || activeEpg.isNotEmpty() ||
        sportsRebuild is tv.own.owntv.core.sports.RebuildState.Running

    LaunchedEffect(lastCompleted) {
        val completed = lastCompleted ?: return@LaunchedEffect
        if (completedQueue.none { it.timestamp == completed.timestamp && it.sourceId == completed.sourceId }) {
            completedQueue.add(completed)
        }
    }

    LaunchedEffect(currentCompleted, completedQueue.size) {
        if (currentCompleted == null && completedQueue.isNotEmpty()) {
            val next = completedQueue.removeAt(0)
            currentCompleted = next
            catalogTracker.consumeCompleted(next.timestamp)
        }
    }

    LaunchedEffect(currentCompleted) {
        if (currentCompleted != null) {
            delay(5000)
            currentCompleted = null
        }
    }

    if (!anyActive && currentCompleted == null && !sportsFailed) return

    // Catalog syncs first (they're the slower, more interesting ones), then guides, in a stable
    // order so a row doesn't jump around as progress updates arrive.
    val rows = buildList<SyncLine> {
        activeCatalog.values.sortedBy { it.sourceId }.forEach { add(SyncLine.Catalog(it)) }
        activeEpg.values.sortedBy { it.sourceId }.forEach { add(SyncLine.Epg(it)) }
        if (sportsRebuild is tv.own.owntv.core.sports.RebuildState.Running) add(SyncLine.Sports)
    }
    val shown = rows.take(MAX_ROWS)
    val hidden = rows.size - shown.size
    val lineCount = shown.size + (if (hidden > 0) 1 else 0) +
        (if (currentCompleted != null) 1 else 0) + (if (sportsFailed) 1 else 0)

    // The Stage glass card (as the top-right pills): fully round for one line, a rounded card for a stack.
    val radius = if (lineCount > 1) 22.mpx else 30.mpx
    Column(
        modifier = modifier
            .padding(bottom = 28.mpx)
            .widthIn(max = 1100.mpx)
            .stageGlass(radius, overContent = true)
            .padding(horizontal = 24.mpx, vertical = 12.mpx),
        verticalArrangement = Arrangement.spacedBy(6.mpx),
    ) {
        shown.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OwnTVSpinner(sizeDp = 14)
                Text(
                    line.text(),
                    style = stageText(16, 600),
                    color = StageColors.ItemText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hidden > 0) {
            Text(
                pluralStringResource(R.plurals.sync_status_more, hidden, hidden),
                style = stageText(16, 600),
                color = StageColors.ItemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        currentCompleted?.let { completed ->
            Text(
                completedLine(completed, online = connectivity.isOnlineNow()),
                style = stageText(16, 600),
                color = StageColors.ItemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (sportsFailed) {
            Text(
                stringResource(R.string.sports_rebuild_failed),
                style = stageText(16, 600),
                color = StageColors.ItemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private sealed interface SyncLine {
    data class Catalog(val sync: SyncActivityTracker.ActiveSync) : SyncLine
    data class Epg(val sync: EpgActivityTracker.ActiveEpgSync) : SyncLine
    data object Sports : SyncLine
}

@Composable
private fun SyncLine.text(): String = when (this) {
    SyncLine.Sports -> stringResource(R.string.sports_rebuilding)
    is SyncLine.Catalog -> {
        val res = LocalContext.current.resources
        val counts = sync.stage?.let {
            SyncProgressCounts(
                live = it.liveProcessed,
                liveActive = it.liveActive || it.liveProcessed > 0,
            )
        }
        val countsText = counts?.displayText(res).orEmpty()
        if (countsText.isBlank()) stringResource(R.string.sync_status_catalog, sync.sourceName)
        else stringResource(R.string.sync_status_catalog_with_counts, sync.sourceName, countsText)
    }
    is SyncLine.Epg -> {
        val context = LocalContext.current
        val countParts = buildList {
            if (sync.channels > 0) add(pluralStringResource(R.plurals.sync_count_channels, sync.channels, compactCount(context, sync.channels)))
            if (sync.programmes > 0) add(pluralStringResource(R.plurals.sync_count_epg, sync.programmes, compactCount(context, sync.programmes)))
        }
        val countsText = countParts.joinToString(stringResource(R.string.sync_counts_separator))
        if (countsText.isBlank()) stringResource(R.string.sync_status_epg, sync.sourceName)
        else stringResource(R.string.sync_status_epg_with_counts, sync.sourceName, countsText)
    }
}

@Composable
private fun completedLine(completed: SyncActivityTracker.CompletedSync, online: Boolean): String = when (val result = completed.result) {
    is SyncResult.Success -> {
        val changes = buildList {
            if (result.categoriesAdded > 0) {
                add(pluralStringResource(R.plurals.sync_categories_added, result.categoriesAdded, result.categoriesAdded))
            }
            if (result.categoriesRemoved > 0) {
                add(pluralStringResource(R.plurals.sync_categories_removed, result.categoriesRemoved, result.categoriesRemoved))
            }
        }.joinToString(stringResource(R.string.sync_counts_separator))
        if (changes.isBlank()) stringResource(R.string.sync_status_complete, completed.sourceName)
        else stringResource(R.string.sync_status_complete_with_changes, completed.sourceName, changes)
    }
    is SyncResult.Failed -> stringResource(
        R.string.sync_status_failed,
        completed.sourceName,
        classifySyncFailure(result.message, online).displayText(LocalContext.current.resources),
    )
    SyncResult.Cancelled -> stringResource(R.string.sync_status_cancelled, completed.sourceName)
}

/** Beyond this many concurrent syncs the pill summarises the rest, rather than covering the screen. */
private const val MAX_ROWS = 4
