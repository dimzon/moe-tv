package tv.own.myowntv.features.settings
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
import tv.own.owntv.core.profile.*
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

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.myowntv.ui.theme.mpx
import androidx.compose.ui.focus.onFocusChanged
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.owntv.core.sync.work.EpgSyncState
import tv.own.owntv.core.util.classifySyncFailure
import tv.own.myowntv.ui.components.displayText

/** One guide on the EPG Sources list: its status line, its badge and the panel with its actions. */
@Composable
internal fun EpgRow(
    source: EpgSource,
    autoRefresh: EpgRefresh,
    counts: suspend () -> Pair<Int, Int>,
    syncState: EpgSyncState,
    deleting: Boolean,
    rowFocus: FocusRequester?,
    actionsFocus: FocusRequester,
    keepPanel: Boolean,
    priority: String?,
    orderActions: List<StageAction>,
    onFocused: () -> Unit,
    onResync: () -> Unit,
    onCancelSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val count by produceState<Pair<Int, Int>?>(initialValue = null, source.id, source.lastSyncAt, source.lastError) {
        value = runCatching { counts() }.getOrNull()
    }
    val activeSync = syncState as? EpgSyncState.Syncing
    val syncPercent = activeSync?.let {
        if (it.baseProgrammes > 0 && it.programmes > 0) {
            ((it.programmes.toLong() * 100) / it.baseProgrammes).toInt().coerceAtMost(99)
        } else {
            null
        }
    }
    val subtitle = if (tv.own.owntv.core.repository.EpgRepository.stalkerSourceIdOf(source.url) != null) {
        stringResource(R.string.settings_epg_sources_portal_guide)
    } else {
        // Never the account's user name and password: an XMLTV address often carries both.
        tv.own.owntv.core.network.HttpClient.redactUrl(source.url)
    }
    val status = epgSourceStatus(source, count, activeSync)
    val failed = source.lastError != null && activeSync == null
    val badge = when {
        deleting -> stringResource(R.string.settings_epg_sources_deleting)
        activeSync != null -> syncPercent?.let { stringResource(R.string.settings_epg_sources_syncing_percent, it) }
            ?: stringResource(R.string.settings_epg_sources_syncing_label)
        autoRefresh.mode != EpgAutoRefresh.OFF -> stringResource(R.string.settings_sources_auto_refresh, epgRefreshLabel(autoRefresh))
        else -> null
    }
    val back = rowFocus ?: remember { FocusRequester() }
    val actions = epgSourceActions(deleting, syncState, orderActions, onResync, onCancelSync, onEdit, onDelete)
    StageSettingRow(
        icon = MyOwnTVIcon.EPG,
        title = source.name,
        // A failed guide shows its error in red on the right, so the line under the name is its address.
        desc = if (failed) subtitle else status,
        value = when {
            failed -> SettingValue.Custom {
                Text(status, style = tv.own.myowntv.ui.theme.stageText(18, 700), color = tv.own.myowntv.ui.theme.StageColors.Danger, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 360.mpx))
            }
            badge != null -> SettingValue.Action(badge)
            else -> null
        },
        onClick = { if (actions.isNotEmpty()) runCatching { actionsFocus.requestFocus() } },
        keepPanel = keepPanel,
        help = SettingHelp(
            title = source.name,
            // The address on one line, the counts under it.
            text = subtitle,
            hints = listOf(
                "▶" to stringResource(R.string.settings_key_actions),
                stringResource(R.string.common_back) to stringResource(R.string.common_nav_settings),
            ),
            extra = {
                Text(status, style = tv.own.myowntv.ui.theme.stageText(17, 500), color = if (failed) tv.own.myowntv.ui.theme.StageColors.Danger else tv.own.myowntv.ui.theme.StageColors.Muted)
                priority?.let { Text(it, style = tv.own.myowntv.ui.theme.stageText(17, 500), color = tv.own.myowntv.ui.theme.StageColors.Muted) }
                if (actions.isNotEmpty()) StageActionColumn(actions, back, actionsFocus)
            },
        ),
        modifier = Modifier
            .focusRequester(back)
            .focusProperties { if (actions.isNotEmpty()) right = actionsFocus }
            .onFocusChanged { if (it.isFocused) onFocused() },
    )
}

/** The line under a guide's name: live sync progress, the last error, or what the guide holds. */
@Composable
private fun epgSourceStatus(
    source: EpgSource,
    count: Pair<Int, Int>?,
    activeSync: EpgSyncState.Syncing?,
): String {
    return when {
        activeSync != null -> when {
            activeSync.programmes > 0 -> stringResource(
                R.string.settings_epg_sources_status_count,
                pluralStringResource(R.plurals.settings_epg_sources_status_count_channels, activeSync.channels, activeSync.channels),
                pluralStringResource(R.plurals.settings_epg_sources_status_count_programmes, activeSync.programmes, activeSync.programmes),
            )
            activeSync.channels > 0 -> pluralStringResource(
                R.plurals.settings_epg_sources_status_count_channels,
                activeSync.channels,
                activeSync.channels,
            )
            else -> stringResource(R.string.settings_epg_sources_connecting)
        }
        source.lastError != null -> stringResource(
            R.string.settings_epg_sources_error,
            classifySyncFailure(source.lastError, online = true).displayText(),
        )
        count != null && count.second > 0 -> stringResource(
            R.string.settings_epg_sources_status_count,
            pluralStringResource(R.plurals.settings_epg_sources_status_count_channels, count.first, count.first),
            pluralStringResource(R.plurals.settings_epg_sources_status_count_programmes, count.second, count.second),
        )
        source.lastSyncAt != null -> stringResource(R.string.settings_epg_sources_status_synced)
        else -> stringResource(R.string.settings_epg_sources_not_synced)
    }
}

/** Re-sync (Cancel while syncing), Edit, the [orderActions] and Delete for one guide's panel. */
@Composable
private fun epgSourceActions(
    deleting: Boolean,
    syncState: EpgSyncState,
    orderActions: List<StageAction>,
    onResync: () -> Unit,
    onCancelSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
): List<StageAction> {
    // While the guide data is being deleted the actions go: the row is on its way out.
    if (deleting) return emptyList()
    return listOf(
        StageAction(
            MyOwnTVIcon.REFRESH,
            stringResource(if (syncState.isActive) R.string.common_cancel else R.string.settings_sources_resync),
            if (syncState.isActive) onCancelSync else onResync,
        ),
        StageAction(MyOwnTVIcon.PENCIL, stringResource(R.string.common_edit), onEdit),
    ) + orderActions + StageAction(MyOwnTVIcon.TRASH, stringResource(R.string.common_delete), onDelete, danger = true)
}
