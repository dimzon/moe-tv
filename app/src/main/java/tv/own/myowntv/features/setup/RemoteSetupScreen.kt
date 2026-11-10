package tv.own.myowntv.features.setup
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import tv.own.myowntv.ui.theme.stageTone
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import tv.own.myowntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.companion.CompanionPayload
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.myowntv.features.settings.SettingHelp
import tv.own.myowntv.features.settings.SettingPanelHeading
import tv.own.myowntv.features.settings.SettingValue
import tv.own.myowntv.features.settings.StageFullPage
import tv.own.myowntv.features.settings.StageSettingRow
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.companionLockedText
import tv.own.myowntv.ui.components.displayText
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText

/**
 * Add a source › From your phone: the small LAN web server starts as the page opens;
 * the panel shows the QR, the address and the PIN. A submission hands off to the Type it here
 * form, pre-filled, where the user presses Start import ([onPayloadReceived]). New PIN restarts the server
 * with a fresh PIN. The listener stops when this page leaves composition.
 *
 * Editing a playlist uses the same page with its own [parents] and [note]; the submission then
 * pre-fills the edit form, where the user presses Save.
 */
@Composable
fun RemoteSetupScreen(
    state: CompanionServerState,
    payloads: Flow<CompanionPayload>,
    onStartListener: (port: Int) -> Unit,
    onStopListener: () -> Unit,
    onPayloadReceived: (CompanionPayload) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    parents: List<String> = listOf(stringResource(R.string.setup_add_source)),
    note: String = stringResource(R.string.settings_remote_details_note),
) {
    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state == CompanionServerState.Idle) onStartListener(CompanionLink.DEFAULT_PORT)
        runCatching { rowsFocus.requestFocus() }
    }
    // A remote submission hands off to the Type it here form; the host navigates away (which stops the server).
    LaunchedEffect(payloads) { payloads.collect(onPayloadReceived) }
    DisposableEffect(Unit) { onDispose { onStopListener() } }

    // The heading is drawn above the QR by [RemotePanel], so the help itself carries none.
    val help = SettingHelp(
        title = "",
        text = note,
        hints = listOf(stringResource(R.string.common_back) to stringResource(R.string.settings_key_stop_back)),
    )
    val panel: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = { RemotePanel(state) }
    StageFullPage(
        parents = parents,
        title = stringResource(R.string.setup_from_phone),
        count = "",
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
        settingsRoot = false,
        panelTop = panel,
    ) {
        val (line, problem) = when (state) {
            CompanionServerState.Idle, CompanionServerState.Starting -> stringResource(R.string.setup_opening) to false
            is CompanionServerState.Listening -> stringResource(R.string.settings_remote_nothing_yet) to false
            is CompanionServerState.Failed -> state.failure.displayText() to true
            CompanionServerState.Locked -> companionLockedText() to true
        }
        StageSettingRow(
            icon = MyOwnTVIcon.PHONE,
            title = stringResource(R.string.settings_remote_waiting),
            desc = line,
            value = if (problem) null else SettingValue.Custom {
                Text("…", style = stageText(18, 700), color = StageColors.Dim)
            },
            onClick = {},
            help = help,
        )
        StageSettingRow(
            icon = MyOwnTVIcon.REFRESH,
            title = stringResource(R.string.settings_remote_new_pin),
            desc = stringResource(R.string.settings_line_remote_new_pin),
            value = null,
            // Restarting mints a fresh PIN — also the way out of a failed or locked server.
            onClick = { onStopListener(); onStartListener(CompanionLink.DEFAULT_PORT) },
            help = help,
        )
    }
}

/** The panel: SCAN WITH YOUR PHONE, the QR beside "or open" + the address + the PIN, then the note. */
@Composable
private fun RemotePanel(state: CompanionServerState) {
    SettingPanelHeading(stringResource(R.string.settings_remote_scan))
    val listening = state as? CompanionServerState.Listening ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalAlignment = Alignment.CenterVertically) {
        listening.qr?.let { qr ->
            Box(Modifier.size(200.mpx).clip(RoundedCornerShape(18.mpx)).background(Color.White).padding(12.mpx)) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.common_qr_code_companion_url),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Column {
            Text(stringResource(R.string.settings_remote_or_open), style = stageText(17, 500), color = PanelText)
            listening.urls.forEach { url ->
                Text(url, style = stageText(21, 800), color = StageColors.Text, modifier = Modifier.padding(top = 4.mpx))
            }
            Row(Modifier.padding(top = 6.mpx), horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.local_sync_pin_label), style = stageText(17, 500), color = PanelText)
                Text(listening.pin, style = stageText(30, 800, 0.4.em), color = stageAccent.accent)
            }
        }
    }
    Box(Modifier.size(18.mpx))
}

private val PanelText: Color @Composable get() = stageTone(Color(0xFFD3DCD8), StageColors.Body)
