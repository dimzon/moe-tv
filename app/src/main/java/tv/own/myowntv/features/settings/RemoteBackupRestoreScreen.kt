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

import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageText
import tv.own.myowntv.ui.theme.StageColors
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.companion.CompanionServerState
import java.io.File

/**
 * Remote restore: opens the LAN companion server in backup-upload mode and shows the PIN, a QR of the
 * URL, and the URL text so another device on the same Wi-Fi can send an OwnTV backup file to the TV. When an
 * upload arrives, [onBackupReceived] hands the saved file off to the normal restore flow (section
 * picker, password prompt). The listener stops automatically when this screen leaves composition.
 *
 * Shared by More → Backup & Restore and the first-run/add-profile setup wizard.
 */
@Composable
fun RemoteBackupRestoreScreen(
    state: CompanionServerState,
    backups: Flow<File>,
    onStart: (port: Int) -> Unit,
    onStop: () -> Unit,
    onBackupReceived: (File) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }

    // Open a fresh backup-upload session on entry; stop it when the screen leaves.
    LaunchedEffect(Unit) { onStart(CompanionLink.DEFAULT_PORT) }
    LaunchedEffect(backups) { backups.collect(onBackupReceived) }
    DisposableEffect(Unit) { onDispose { onStop() } }

    val restart = { onStart(CompanionLink.DEFAULT_PORT) }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onBack,
        title = stringResource(R.string.settings_restore_remote_title),
        body = stringResource(R.string.settings_restore_remote_description),
        width = 820.mpx,
        modifier = modifier,
        buttons = {
            when (state) {
                is CompanionServerState.Failed ->
                    tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.settings_try_again), onClick = restart, height = 56.mpx, textSize = 19)
                // Restarting mints a fresh PIN, so this is the recovery path — not a retry of a failure.
                CompanionServerState.Locked ->
                    tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.settings_new_pin), onClick = restart, height = 56.mpx, textSize = 19)
                else -> Unit
            }
            tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_back), onClick = onBack, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(actionFocus))
        },
    ) {
        tv.own.myowntv.ui.components.StageCompanionStatus(state)
    }
}

/**
 * Remote export: the TV has written the backup to a cache file and is serving it over the companion
 * server. Shows the PIN, a QR of the URL, and the URL text so another device on the same Wi-Fi can
 * open it, enter the PIN and download the file. The server is started by the ViewModel (after the
 * export finishes) and stopped when this screen leaves composition.
 */
@Composable
fun RemoteBackupExportScreen(
    state: CompanionServerState,
    preparing: Boolean,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { onStop() } }

    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onBack,
        title = stringResource(R.string.settings_download_remote_title),
        body = stringResource(R.string.settings_download_remote_description),
        width = 820.mpx,
        modifier = modifier,
        buttons = {
            tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = onBack, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(actionFocus))
        },
    ) {
        if (preparing) {
            Text(stringResource(R.string.settings_preparing_backup), style = stageText(18, 500), color = StageColors.Muted)
        } else {
            tv.own.myowntv.ui.components.StageCompanionStatus(state)
        }
    }
}
