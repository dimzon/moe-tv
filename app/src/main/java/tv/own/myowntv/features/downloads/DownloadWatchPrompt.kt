package tv.own.myowntv.features.downloads
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
import tv.own.owntv.player.ffmpeg.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.myowntv.R
import tv.own.owntv.core.download.DownloadWatchGuard
import tv.own.myowntv.ui.stage.StageButton
import tv.own.myowntv.ui.stage.StagePopup
import tv.own.myowntv.ui.stage.StagePopupCheck
import tv.own.myowntv.ui.stage.StagePopupOption
import tv.own.myowntv.ui.theme.mpx

/**
 * Core's question when playback opens on a one-stream playlist that a download was using — the
 * download is already paused by then. "Keep downloading" closes the player through [onClosePlayer].
 * Back is the same as watching: the picture is already on, and the download stays paused.
 */
@Composable
fun DownloadWatchPrompt(onClosePlayer: () -> Unit, guard: DownloadWatchGuard = koinInject()) {
    val prompt by guard.prompt.collectAsStateWithLifecycle()
    val p = prompt ?: return
    key(p) {
        var remember by remember { mutableStateOf(false) }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
        StagePopup(
            onDismiss = { guard.answer(watch = true, remember = remember) },
            title = stringResource(R.string.content_download_watch_title),
            body = stringResource(R.string.content_download_watch_body, p.playlistName),
            width = 820.mpx,
            buttons = {
                StageButton(
                    stringResource(R.string.content_download_watch_keep), height = 56.mpx, textSize = 19,
                    onClick = { guard.answer(watch = false, remember = remember); onClosePlayer() },
                )
                StageButton(
                    stringResource(R.string.content_download_watch_watch), height = 56.mpx, textSize = 19, tinted = true,
                    onClick = { guard.answer(watch = true, remember = remember) },
                    modifier = Modifier.focusRequester(first),
                )
            },
        ) {
            Column(Modifier.padding(top = 4.mpx)) {
                StagePopupOption(
                    title = stringResource(R.string.content_download_watch_remember),
                    onClick = { remember = !remember },
                    leading = { f -> StagePopupCheck(remember, f) },
                )
            }
        }
    }
}
