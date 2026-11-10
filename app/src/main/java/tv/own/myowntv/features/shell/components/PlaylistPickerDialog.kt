package tv.own.myowntv.features.shell.components
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

import tv.own.myowntv.ui.theme.StageColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import tv.own.myowntv.ui.theme.mpx
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.myowntv.ui.components.trapAllFocusExit

/**
 * The Stage playlist switcher: the options-menu style, 520 wide under the playlist pill, over
 * the Stage scrim with the pill itself redrawn above it, lit, at [pillBounds]. Rows: "All playlists"
 * with the layers icon, then each playlist with its mark and channel count; a ✓ marks the current one.
 * Under the list, outside its scroll, "Resync now" for the current choice (one playlist, or all).
 */
@Composable
fun StagePlaylistMenu(
    playlists: List<SourceEntity>,
    activeId: Long,
    pillLabel: String,
    pillBounds: androidx.compose.ui.geometry.Rect?,
    onSelect: (Long) -> Unit,
    onResync: () -> Unit,
    onDismiss: () -> Unit,
) {
    val channelDao = org.koin.compose.koinInject<tv.own.owntv.core.database.dao.ChannelDao>()
    val counts by androidx.compose.runtime.produceState(emptyMap<Long, Int>(), playlists) {
        value = playlists.associate { it.id to runCatching { channelDao.countForSourceOnce(it.id) }.getOrDefault(0) }
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { java.text.NumberFormat.getIntegerInstance(locale) }
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().background(StageColors.Scrim)) {
        if (pillBounds != null) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            with(density) {
                tv.own.myowntv.ui.stage.StagePill(
                    text = pillLabel,
                    icon = tv.own.myowntv.ui.components.MyOwnTVIcon.LAYERS,
                    trailingIcon = tv.own.myowntv.ui.components.MyOwnTVIcon.CHEVRON_DOWN,
                    onClick = {},
                    enabled = false,
                    highlighted = true,
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(pillBounds.left.toInt(), pillBounds.top.toInt()) }
                        .width(pillBounds.width.toDp()),
                )
            }
        }
        tv.own.myowntv.ui.stage.StageMenu(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 104.mpx, end = 100.mpx, bottom = 24.mpx)
                .width(520.mpx)
                .trapAllFocusExit()
                .focusGroup(),
        ) {
            tv.own.myowntv.ui.stage.StageMenuHeader(
                title = stringResource(R.string.content_playlist_picker_title),
                subtitle = stringResource(R.string.content_playlist_picker_subtitle),
            )
            // The list scrolls under a fixed heading: without it the menu ran off the bottom of the screen
            // at about 17 playlists, and the rest could not be reached (#6).
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                tv.own.myowntv.ui.stage.StageMenuItem(
                    text = stringResource(R.string.content_all_playlists),
                    icon = tv.own.myowntv.ui.components.MyOwnTVIcon.LAYERS,
                    checked = activeId <= 0,
                    onClick = { onSelect(-1L); onDismiss() },
                    modifier = if (activeId <= 0) Modifier.focusRequester(selectedFocus) else Modifier,
                )
                playlists.forEachIndexed { index, source ->
                    val current = source.id == activeId
                    tv.own.myowntv.ui.stage.StageMenuItem(
                        text = source.name,
                        leading = { tv.own.myowntv.ui.stage.StagePlaylistMark(tv.own.myowntv.ui.stage.PlaylistMark.of(source.name, index)) },
                        value = if (current) null else counts[source.id]?.let(numbers::format),
                        checked = current,
                        onClick = { onSelect(source.id); onDismiss() },
                        modifier = if (current) Modifier.focusRequester(selectedFocus) else Modifier,
                    )
                }
            }
            tv.own.myowntv.ui.stage.StageMenuItem(
                text = stringResource(R.string.settings_sources_resync_now_full),
                icon = tv.own.myowntv.ui.components.MyOwnTVIcon.REFRESH,
                value = playlists.firstOrNull { it.id == activeId }?.name ?: stringResource(R.string.content_all_playlists),
                onClick = { onResync(); onDismiss() },
            )
        }
    }
}
