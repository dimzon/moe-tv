package tv.own.myowntv.features.subtitles
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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.myowntv.ui.theme.mpx
import tv.own.owntv.core.database.dao.LinkedSubtitle
import tv.own.myowntv.ui.components.MyOwnTVIcon

/**
 * Per-item "Delete subtitles" popup opened from a movie/episode long-press.
 * Lists that item's downloaded subtitles; tapping one deletes it individually. Shares the app's selected
 * popup styling. [items] is re-supplied by the caller after each delete (empty → the caller closes).
 */
@Composable
fun SubtitleDeletePopup(
    contentTitle: String,
    items: List<LinkedSubtitle>,
    onDelete: (LinkedSubtitle) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    // Re-run after each delete: the focused row is disposed with the deletion, so land on the first
    // remaining subtitle (when none remain, the caller closes the popup).
    LaunchedEffect(items.size) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler { onDismiss() }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.player_subtitles_delete_title),
        body = contentTitle,
        eyebrow = null,
        width = 760.mpx,
        scroll = false,
        buttons = { tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.settings_close), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
            items(items, key = { it.cacheId }) { item ->
                tv.own.myowntv.ui.stage.StagePopupOption(
                    title = item.languageName ?: item.language ?: stringResource(R.string.player_subtitles_subtitle),
                    subtitle = item.releaseName,
                    onClick = { onDelete(item) },
                    modifier = if (item == items.first()) Modifier.focusRequester(firstFocus) else Modifier,
                    leading = { tv.own.myowntv.ui.stage.StagePopupIcon(MyOwnTVIcon.TRASH, tv.own.myowntv.ui.theme.StageColors.Danger) },
                )
            }
        }
    }
}
