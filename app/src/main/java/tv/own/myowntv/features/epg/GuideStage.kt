package tv.own.myowntv.features.epg
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

import tv.own.myowntv.ui.theme.stageTone
import tv.own.myowntv.ui.theme.stageShadow
import tv.own.myowntv.ui.theme.LocalStagePalette
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import tv.own.myowntv.ui.components.chNavPaging
import tv.own.myowntv.ui.components.jumpLazyListTo
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.myowntv.features.home.durationText
import tv.own.myowntv.features.live.LivePlate
import tv.own.myowntv.ui.components.ChannelLogoTile
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.MyOwnTVPopup
import tv.own.myowntv.ui.components.OwnTVSpinner
import tv.own.myowntv.ui.components.longPressMenuGuard
import tv.own.myowntv.ui.components.trapAllFocusExit
import tv.own.myowntv.ui.format.rememberSystemTimeFormatter
import tv.own.myowntv.ui.stage.StageButton
import tv.own.myowntv.ui.stage.StageFocus
import tv.own.myowntv.ui.stage.StageGroupLabel
import tv.own.myowntv.ui.stage.StageLiveBadge
import tv.own.myowntv.ui.stage.StageMenu
import tv.own.myowntv.ui.stage.StageMenuHeader
import tv.own.myowntv.ui.stage.StageMenuItem
import tv.own.myowntv.ui.stage.StageSurface
import tv.own.myowntv.ui.stage.StageSwitch
import tv.own.myowntv.ui.stage.StageTool
import tv.own.myowntv.ui.stage.drawBoxShadow
import tv.own.myowntv.ui.stage.drawInnerRing
import tv.own.myowntv.ui.stage.stageFocusLook
import tv.own.myowntv.ui.stage.stageGlass
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.mpxSp
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText

/*
 * The TV Guide in the Stage design: the preview and the focused programme on top, the
 * ruler, the channel labels, and every menu the guide opens. EpgScreen owns the state and the focus
 * plumbing; these only draw.
 */

private const val Tabular = "tnum"

/**
 * The top-left video (640×360, radius 28): the focused channel through Live TV's preview player — no
 * second decoder — with the LIVE badge and "259 · Sky Cinema Family · Toy Story 4" along the bottom.
 */
@Composable
internal fun GuideVideo(
    channel: ChannelEntity?,
    channelLine: String?,
    nowTitle: String?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewState by previewEngine.state.collectAsStateWithLifecycle()
    // The picture's compositing path — the same choice the full-screen player honours, so the
    // guide preview and the player agree on whether screenshots and recordings show the video.
    val videoSurface by org.koin.compose.koinInject<tv.own.owntv.core.settings.SettingsRepository>()
        .videoSurfaceMode.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.SettingsRepository.VideoSurface.SURFACE_VIEW)
    val playing = showVideo && previewState != tv.own.owntv.player.LivePreviewEngine.State.ERROR &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.IDLE
    val r = 28.mpx
    val videoShadow = tv.own.myowntv.ui.theme.stageShadow(0.55f)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .drawBehind { drawBoxShadow(videoShadow, 80.mpx.toPx(), r.toPx(), dy = 30.mpx.toPx()) }
            .clip(RoundedCornerShape(r))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (channel != null) {
            ChannelLogoTile(logoUrl = channel.logoUrl, modifier = Modifier.size(200.mpx), fill = Color.Transparent) {
                MyOwnTVIcon(MyOwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.size(48.mpx))
            }
        }
        if (playing) tv.own.myowntv.player.ExoPreviewSurface(
            engine = previewEngine, modifier = Modifier.fillMaxSize(),
            useTextureView = videoSurface == tv.own.owntv.core.settings.SettingsRepository.VideoSurface.TEXTURE_VIEW,
        )
        if (showVideo && previewState == tv.own.owntv.player.LivePreviewEngine.State.LOADING) OwnTVSpinner(sizeDp = 24)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.58f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
        Box(Modifier.fillMaxSize().drawBehind { drawInnerRing(Color.White.copy(alpha = 0.08f), 1.mpx.toPx(), r.toPx()) })
        if (channel != null) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 20.mpx, top = 18.mpx)) { StageLiveBadge(stringResource(R.string.player_live)) }
            Text(
                buildAnnotatedString {
                    append(channelLine.orEmpty())
                    if (!nowTitle.isNullOrBlank()) withStyle(SpanStyle(color = StageColors.Muted, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) { append(" · $nowTitle") }
                },
                style = stageText(19, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.mpx, end = 22.mpx, bottom = 18.mpx),
            )
        }
    }
}

/** "Starts in 58 min · 20:05 – 21:40 · 95 min · Sky Cinema Family" — or "Live now · …", or just the times once it has been on. */
@Composable
internal fun programmeEyebrow(p: EpgProgrammeEntity, channelName: String?, now: Long): String {
    val formatTime = rememberSystemTimeFormatter()
    val range = stringResource(R.string.content_live_time_range_plain, formatTime(p.startMs), formatTime(p.stopMs))
    val lead = when {
        p.startMs > now -> stringResource(R.string.content_epg_starts_in, durationText(((p.startMs - now) + 59_999) / 60_000))
        p.stopMs > now -> stringResource(R.string.content_live_now)
        else -> null
    }
    val length = ((p.stopMs - p.startMs) / 60_000L).toInt().takeIf { it > 0 }?.let { stringResource(R.string.player_duration_minutes, it) }
    return listOfNotNull(lead, range, length, channelName?.takeIf { it.isNotBlank() }).joinToString(" · ")
}

/** The details line: "Сериал · Сезон 2 Серия 17 · 16+ · 95 min" — only what the feed provides. */
@Composable
internal fun programmeDetailsLine(p: EpgProgrammeEntity): String? {
    val d = p.details
    val length = ((p.stopMs - p.startMs) / 60_000L).toInt().takeIf { it > 0 }
        ?.let { stringResource(R.string.player_duration_minutes, it) }
    return (d.categoryList + listOfNotNull(d.subtitle, d.ageRating, length))
        .takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The programme block beside the video: eyebrow (accent, clock), title 60/800, details, 2-line synopsis. */
@Composable
internal fun GuideProgrammeBlock(
    eyebrow: String?,
    title: String?,
    details: String?,
    synopsis: String?,
    modifier: Modifier = Modifier,
) {
    val a = stageAccent
    Column(modifier) {
        if (eyebrow != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                MyOwnTVIcon(MyOwnTVIcon.CLOCK, a.accent, Modifier.size(18.mpx))
                Text(eyebrow, style = stageText(18, 700, 0.02.em), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (title != null) {
            Text(
                title, style = stageText(60, 800, (-1.5).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.mpx, bottom = 6.mpx),
            )
        }
        if (details != null) Text(details, style = stageText(19, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            synopsis.orEmpty(),
            style = stageText(19, 400).copy(lineHeight = (19 * 1.5f).mpxSp), color = stageTone(Color(0xFFCCD6D2), StageColors.Body),
            maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 1000.mpx).padding(top = 12.mpx, bottom = 20.mpx),
        )
    }
}

/**
 * The ruler above the grid: a time every half hour (17/700 muted, tabular), none within 12 minutes of
 * now, and the accent chip on the now-line ("19:07"). Drawn from the shared scroll position.
 */
@Composable
internal fun GuideRuler(
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    scrollPx: () -> Int,
    modifier: Modifier = Modifier,
) {
    val formatTime = rememberSystemTimeFormatter()
    val a = stageAccent
    val slotMs = GuideGridDefaults.SlotMin * 60_000L
    val first = windowStart + (slotMs - windowStart % slotMs) % slotMs
    val times = generateSequence(first) { it + slotMs }.takeWhile { it < windowEnd }
        .filter { kotlin.math.abs(it - now) > 12 * 60_000L }.toList()
    val nowIn = now in windowStart..windowEnd
    Layout(
        modifier = modifier,
        content = {
            times.forEach { t ->
                Text(formatTime(t), style = stageText(17, 700).copy(fontFeatureSettings = Tabular), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (nowIn) {
                Text(
                    formatTime(now), style = stageText(15, 800).copy(fontFeatureSettings = Tabular), color = a.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(a.accent, RoundedCornerShape(8.mpx)).padding(horizontal = 9.mpx, vertical = 3.mpx),
                )
            }
        },
    ) { measurables, constraints ->
        val scrollPx = scrollPx()
        val ppm = GuideGridDefaults.PxPerMin.toPx()
        val chipShift = 34.mpx.toPx()
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, pl ->
                val isChip = nowIn && i == placeables.lastIndex
                val t = if (isChip) now else times[i]
                val x = ((t - windowStart) / 60_000f * ppm - scrollPx - if (isChip) chipShift else 0f).toInt()
                if (x + pl.width < 0 || x > constraints.maxWidth) return@forEachIndexed
                // The chip sits 4 higher than the labels (top 518 against 522).
                pl.place(IntOffset(x, if (isChip) 0 else 4.mpx.roundToPx()))
            }
        }
    }
}

/**
 * A channel's label column: the number (38 wide, 17/700 dim), the
 * logo on a 58×40 plate and the name 17/700. The row the focus is in has its number and name in accent.
 */
@Composable
internal fun GuideChannelLabel(channel: ChannelEntity, name: String, selected: Boolean, numberWidth: Dp, modifier: Modifier = Modifier) {
    val a = stageAccent
    Row(
        modifier.padding(start = 8.mpx),
        horizontalArrangement = Arrangement.spacedBy(10.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            channel.number?.toString().orEmpty(),
            style = stageText(17, 700).copy(fontFeatureSettings = Tabular), color = if (selected) a.accent else StageColors.Dim,
            // Always whole: the column fits the longest number in the list; only the name is cut.
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(numberWidth),
        )
        LivePlate(channel.logoUrl, 58.mpx, 40.mpx, radius = 10.mpx)
        Text(name, style = stageText(17, 700), color = if (selected) a.accent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** The 3 px accent now-line with its glow, over the rows. */
internal fun Modifier.guideNowLine(accent: Color): Modifier = drawBehind {
    val w = 3.mpx.toPx()
    drawBoxShadow(accent, 14.mpx.toPx(), w / 2f)
    drawRoundRect(accent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f))
}

// ---------------------------------------------------------------------------------------------
// Menus
// ---------------------------------------------------------------------------------------------

/** Every guide menu: the Stage menu over the scrim, at its place ([x] of 1920, [top]), scrolling at large zooms. */
@Composable
internal fun GuideMenuHost(x: Float, top: Dp, width: Dp, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { onDismiss() }
    MyOwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        BoxWithConstraints(Modifier.fillMaxSize().background(StageColors.Scrim).longPressMenuGuard()) {
            StageMenu(
                Modifier
                    .padding(start = (maxWidth * (x / 1920f)).coerceAtMost(maxWidth - width), top = top, bottom = 24.mpx)
                    .width(width)
                    .verticalScroll(rememberScrollState())
                    .trapAllFocusExit()
                    .focusGroup(),
                content = content,
            )
        }
    }
}

/** What a programme can do right now, worked out by the screen; null actions are shown disabled. */
internal class GuideProgrammeActions(
    val reminderSet: Boolean,
    val onReminder: (() -> Unit)?,
    val onWatchChannel: () -> Unit,
    val isFavorite: Boolean,
    val onFavorite: () -> Unit,
    val epgOffsetValue: String?,
    val onEpgOffset: (() -> Unit)?,
)

/**
 * What [p] on [ch] can do at [now], through the guide's view model: Remind me until it starts, Watch
 * the channel, favourite it, and shift its guide by a per-channel offset. The TV Guide and Live TV's
 * schedule both build their menu from this, so the two never disagree.
 */
@Composable
internal fun guideProgrammeActions(
    vm: EpgViewModel,
    ch: ChannelEntity,
    p: EpgProgrammeEntity,
    now: Long,
    onWatch: () -> Unit,
    onEpgOffset: () -> Unit,
): GuideProgrammeActions {
    val reminderRows by vm.reminderRows.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteChannelIds.collectAsStateWithLifecycle()
    return GuideProgrammeActions(
        reminderSet = reminderRows.any { it.channelId == ch.id && it.startMs == p.startMs },
        onReminder = if (p.startMs > now) ({ vm.toggleReminder(ch, p) }) else null,
        onWatchChannel = onWatch,
        isFavorite = ch.id in favoriteIds,
        onFavorite = { vm.toggleFavoriteChannel(ch) },
        epgOffsetValue = vm.currentEpgShift(ch)?.let { tv.own.myowntv.features.live.liveEpgShiftLabel(it) }
            ?: stringResource(R.string.content_epg_offset_global_short),
        onEpgOffset = onEpgOffset,
    )
}

/**
 * ☰ Programme options, 560 wide at the right: "Wish" + "20:05 – 21:40 · 259 Sky Cinema Family";
 * DETAILS Program details (focused first) · NEW Remind me (5 min before) · WATCH Watch channel ·
 * CHANNEL favourite, EPG time offset (Global). Also Live TV's ▶ Schedule → OK menu.
 */
@Composable
internal fun GuideProgrammeMenu(title: String, subtitle: String, actions: GuideProgrammeActions, leadMinutes: Int, onDetails: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    GuideMenuHost(x = 1150f, top = 64.mpx, width = 560.mpx, onDismiss = onDismiss) {
        StageMenuHeader(title = title, subtitle = subtitle)
        StageGroupLabel(stringResource(R.string.content_menu_group_details).uppercase(locale))
        StageMenuItem(
            text = stringResource(R.string.content_programme_details), icon = MyOwnTVIcon.INFO,
            onClick = { onDismiss(); onDetails() },
            modifier = Modifier.focusRequester(focus),
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_new).uppercase(locale))
        StageMenuItem(
            text = stringResource(if (actions.reminderSet) R.string.content_reminder_set else R.string.content_remind_me),
            icon = MyOwnTVIcon.BELL,
            value = if (actions.reminderSet) null else tv.own.myowntv.features.live.reminderLeadText(leadMinutes),
            checked = actions.reminderSet,
            enabled = actions.onReminder != null,
            onClick = { actions.onReminder?.invoke(); onDismiss() },
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_watch).uppercase(locale))
        StageMenuItem(
            text = stringResource(R.string.content_epg_watch_channel), icon = MyOwnTVIcon.LIVE_TV,
            onClick = { onDismiss(); actions.onWatchChannel() },
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_channel).uppercase(locale))
        StageMenuItem(
            text = stringResource(if (actions.isFavorite) R.string.content_epg_remove_favourite else R.string.content_epg_add_favourite),
            icon = MyOwnTVIcon.FAVORITE,
            onClick = { actions.onFavorite(); onDismiss() },
        )
        if (actions.onEpgOffset != null) {
            StageMenuItem(
                text = stringResource(R.string.content_epg_time_offset), icon = MyOwnTVIcon.CLOCK, value = actions.epgOffsetValue,
                onClick = { onDismiss(); actions.onEpgOffset.invoke() },
            )
        }
    }
}

/** Order ▾: "Order channels by · Opens this way next time" and the orders, ✓ on the current one. */
@Composable
internal fun GuideOrderMenu(
    current: SettingsRepository.GuideSort,
    onPick: (SettingsRepository.GuideSort) -> Unit,
    showEmpty: Boolean,
    onShowEmpty: (Boolean) -> Unit,
    showPreview: Boolean,
    onShowPreview: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GuideMenuHost(x = 560f, top = 520.mpx, width = 470.mpx, onDismiss = onDismiss) {
        StageMenuHeader(title = stringResource(R.string.content_epg_order_title), subtitle = stringResource(R.string.content_epg_order_hint))
        GuideSortOrder.forEach { (sort, icon) ->
            StageMenuItem(
                text = guideSortLabel(sort), icon = icon, checked = sort == current || (sort == SettingsRepository.GuideSort.PROVIDER && current == SettingsRepository.GuideSort.LIVE_TV),
                onClick = { onPick(sort); onDismiss() },
                modifier = if (sort == current || (sort == SettingsRepository.GuideSort.PROVIDER && current == SettingsRepository.GuideSort.LIVE_TV)) Modifier.focusRequester(focus) else Modifier,
            )
        }
        // SHOW, as the Series page's order menu has it: a filter, not an order.
        StageGroupLabel(stringResource(R.string.content_menu_group_show).uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale))
        StageMenuItem(
            text = stringResource(R.string.content_epg_show_empty), icon = MyOwnTVIcon.EYE_OFF,
            value = stringResource(if (showEmpty) R.string.common_on else R.string.common_off),
            onClick = { onShowEmpty(!showEmpty); onDismiss() },
        )
        StageMenuItem(
            text = stringResource(R.string.content_epg_show_preview), icon = MyOwnTVIcon.VIDEO,
            value = stringResource(if (showPreview) R.string.common_on else R.string.common_off),
            onClick = { onShowPreview(!showPreview); onDismiss() },
        )
    }
}

/**
 * Hold OK / ☰ where nothing is on (a channel with no guide, or a gap in its feed): what the programme
 * menu offers for the channel itself — watch it, favourite it, and give it a guide.
 */
@Composable
internal fun GuideChannelMenu(
    channel: ChannelEntity,
    isFavorite: Boolean,
    epgOffsetValue: String,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onEpgOffset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    GuideMenuHost(x = 1150f, top = 64.mpx, width = 560.mpx, onDismiss = onDismiss) {
        StageMenuHeader(
            title = listOfNotNull(channel.number?.toString(), tv.own.owntv.core.live.ProviderTags.parse(channel.name).name).joinToString(" · "),
            subtitle = stringResource(R.string.content_epg_no_programme),
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_watch).uppercase(locale))
        StageMenuItem(text = stringResource(R.string.content_epg_watch_channel), icon = MyOwnTVIcon.LIVE_TV, onClick = { onDismiss(); onWatch() }, modifier = Modifier.focusRequester(focus))
        StageGroupLabel(stringResource(R.string.content_menu_group_channel).uppercase(locale))
        StageMenuItem(
            text = stringResource(if (isFavorite) R.string.content_epg_remove_favourite else R.string.content_epg_add_favourite),
            icon = MyOwnTVIcon.FAVORITE, onClick = { onFavorite(); onDismiss() },
        )
        StageMenuItem(text = stringResource(R.string.content_epg_time_offset), icon = MyOwnTVIcon.CLOCK, value = epgOffsetValue, onClick = { onDismiss(); onEpgOffset() })
    }
}

/** Provider, A–Z, Favourites first. No Channel number: it is the provider's order. (The stored
 *  CATCHUP value — catch-up is gone — is no longer offered; it reads as the provider's order.) */
private val GuideSortOrder = listOf(
    SettingsRepository.GuideSort.PROVIDER to MyOwnTVIcon.SORT,
    SettingsRepository.GuideSort.ALPHA to MyOwnTVIcon.SORT,
    SettingsRepository.GuideSort.FAVORITES to MyOwnTVIcon.FAVORITE,
)

@Composable
internal fun guideSortLabel(sort: SettingsRepository.GuideSort): String = stringResource(
    when (sort) {
        // LIVE_TV is the old stored default: it now means the provider's order.
        SettingsRepository.GuideSort.LIVE_TV, SettingsRepository.GuideSort.PROVIDER -> R.string.content_epg_sort_provider
        SettingsRepository.GuideSort.ALPHA -> R.string.content_epg_sort_alpha
        SettingsRepository.GuideSort.FAVORITES -> R.string.content_epg_sort_favorites_first
    },
)

/** Today ▾: the days the guide holds, the one on screen ticked; picking one jumps there at the same time of day. */
@Composable
internal fun GuideDayMenu(days: List<Long>, currentDay: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GuideMenuHost(x = 250f, top = 520.mpx, width = 400.mpx, onDismiss = onDismiss) {
        days.forEach { day ->
            StageMenuItem(
                text = guideDayLabel(day), icon = MyOwnTVIcon.CALENDAR, checked = day == currentDay,
                onClick = { onPick(day); onDismiss() },
                modifier = if (day == currentDay) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}

/** "Today", "Tomorrow", "Yesterday", else the weekday and date ("Thu 2 Oct"). [day] is local midnight. */
@Composable
internal fun guideDayLabel(day: Long): String {
    val today = localMidnight(System.currentTimeMillis())
    val formatDay = tv.own.myowntv.ui.format.rememberBestDateFormatter("EEEdMMM")
    return when ((day - today) / DAY_MS_APPROX) {
        0L -> stringResource(R.string.content_epg_today)
        1L -> stringResource(R.string.content_epg_tomorrow)
        -1L -> stringResource(R.string.content_epg_yesterday)
        else -> formatDay(day)
    }
}

private const val DAY_MS_APPROX = 24L * 60 * 60 * 1000

/** Local midnight of [ms], so days are counted as the viewer lives them (DST included). */
internal fun localMidnight(ms: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = ms
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
internal fun reminderEyebrow(reminder: tv.own.owntv.core.database.entity.ReminderEntity): String = programmeEyebrow(
    EpgProgrammeEntity(epgChannelId = "", startMs = reminder.startMs, stopMs = reminder.stopMs, title = reminder.title),
    null,
    System.currentTimeMillis(),
)

/**
 * The reminder prompt (Programme reminders = Ask to switch): the programme about to start, with
 * Watch channel and Dismiss. It opens over whatever is on screen, the player included.
 */
@Composable
internal fun ReminderPrompt(
    reminder: tv.own.owntv.core.database.entity.ReminderEntity,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val a = stageAccent
    val focus = remember { FocusRequester() }
    LaunchedEffect(reminder.id) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    val eyebrow = reminderEyebrow(reminder)
    MyOwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(Modifier.fillMaxSize().background(StageColors.Scrim), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(760.mpx)
                    .stageGlass(30.mpx, overContent = true)
                    .padding(30.mpx)
                    .trapAllFocusExit()
                    .focusGroup(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                    MyOwnTVIcon(MyOwnTVIcon.BELL, a.accent, Modifier.size(18.mpx))
                    Text(eyebrow, style = stageText(18, 700, 0.02.em), color = a.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    reminder.title, style = stageText(38, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.mpx, bottom = 24.mpx),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    StageButton(
                        stringResource(R.string.content_epg_watch_channel), onClick = onWatch, icon = MyOwnTVIcon.LIVE_TV,
                        height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(focus),
                    )
                    StageButton(stringResource(R.string.common_dismiss), onClick = onDismiss, height = 56.mpx, textSize = 19)
                }
            }
        }
    }
}
