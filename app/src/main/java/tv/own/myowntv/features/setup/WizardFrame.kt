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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.stage.StageFocus
import tv.own.myowntv.ui.stage.StageKeyHints
import tv.own.myowntv.ui.stage.StagePill
import tv.own.myowntv.ui.stage.StageSurface
import tv.own.myowntv.ui.stage.stageBackground
import tv.own.myowntv.ui.stage.stageGlass
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText

/** One footer button of a wizard step: its label and what OK on it does. */
internal class WizardAction(val label: String, val onClick: () -> Unit, val focus: FocusRequester? = null, val enabled: Boolean = true)

/** The five steps the dots show: which one a page belongs to. */
internal enum class WizardStep { LANGUAGE, DISPLAY, PROFILE, PLAYLIST, READY }

/**
 * Every setup step's frame: the Stage background, the OwnTV lockup top left, the step
 * dots top right, one glass panel (1040 wide, centred, from y 170) with a 42 px title, 19 px text and the
 * step's rows or cards, then [back] bottom-left and [next] bottom-right, and the key hints at the bottom.
 * Back on the remote calls [back] (or [onBack] where the step has no Back button).
 */
@Composable
internal fun WizardFrame(
    step: WizardStep,
    title: String,
    sub: String?,
    back: WizardAction?,
    next: WizardAction?,
    onBack: (() -> Unit)? = back?.onClick,
    width: Dp = 1040.mpx,
    /** More buttons beside Back (the display step's Reset). */
    extraLeft: List<WizardAction> = emptyList(),
    body: @Composable ColumnScope.() -> Unit = {},
) {
    if (onBack != null) BackHandler { onBack() }
    val a = stageAccent
    BoxWithConstraints(Modifier.fillMaxSize().stageBackground(a.accent)) {
        // The OwnTV lockup: the play mark on its cream tile, the name beside it.
        Row(
            Modifier.padding(start = 64.mpx, top = 56.mpx),
            horizontalArrangement = Arrangement.spacedBy(14.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.mpx).background(LockupTile, RoundedCornerShape(12.mpx)), contentAlignment = Alignment.Center) {
                MyOwnTVIcon(MyOwnTVIcon.PLAY, LockupMark, Modifier.size(22.mpx))
            }
            Text(stringResource(R.string.app_name), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StepDots(step, Modifier.align(Alignment.TopEnd).padding(end = 64.mpx, top = 66.mpx))

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 170.mpx, bottom = 120.mpx)
                .width(width.coerceAtMost(maxWidth - 128.mpx))
                .heightIn(max = maxHeight - 290.mpx)
                .stageGlass(32.mpx)
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.mpx, vertical = 44.mpx),
        ) {
            Text(title, style = stageText(42, 800, (-1f / 46f).em), color = StageColors.Text)
            if (!sub.isNullOrBlank()) {
                Text(sub, style = stageText(19, 500).copy(lineHeight = 29.5f.mpxLine()), color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx))
            }
            Column(Modifier.padding(top = 28.mpx), verticalArrangement = Arrangement.spacedBy(6.mpx), content = body)
            if (back != null || next != null || extraLeft.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 30.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    (listOfNotNull(back) + extraLeft).forEach { b ->
                        StagePill(b.label, onClick = b.onClick, enabled = b.enabled, modifier = b.focus?.let { Modifier.focusRequester(it) } ?: Modifier)
                    }
                    Spacer(Modifier.weight(1f))
                    if (next != null) {
                        StagePill(
                            next.label, onClick = next.onClick, trailingIcon = MyOwnTVIcon.CHEVRON, enabled = next.enabled,
                            modifier = next.focus?.let { Modifier.focusRequester(it) } ?: Modifier,
                        )
                    }
                }
            }
        }
        StageKeyHints(
            listOf(
                stringResource(R.string.common_ok) to stringResource(R.string.content_key_select),
                stringResource(R.string.common_back) to stringResource(R.string.setup_key_previous_step),
            ),
            Modifier.align(Alignment.BottomCenter).padding(bottom = 44.mpx),
            textSize = 15,
        )
    }
}

/** The dots: past steps accent, the current one a 26 px accent bar with its name, later ones white 18%. */
@Composable
private fun StepDots(current: WizardStep, modifier: Modifier) {
    val a = stageAccent
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
        WizardStep.entries.forEach { s ->
            val on = s == current
            val reached = s.ordinal <= current.ordinal
            Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = if (on) 26.mpx else 10.mpx, height = 10.mpx)
                        .background(if (reached) a.accent else StageColors.Ink.copy(alpha = 0.18f), RoundedCornerShape(5.mpx)),
                )
                if (on) Text(stringResource(s.labelRes), style = stageText(15, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val WizardStep.labelRes: Int get() = when (this) {
    WizardStep.LANGUAGE -> R.string.settings_language
    WizardStep.DISPLAY -> R.string.setup_step_display
    WizardStep.PROFILE -> R.string.settings_group_profile
    WizardStep.PLAYLIST -> R.string.setup_step_playlist
    WizardStep.READY -> R.string.setup_step_ready
}

/** One choice card of a step (`.tile2`): icon, 23 px title, 16 px line; focused = FX. */
internal class WizardCard(val icon: MyOwnTVIcon, val title: String, val line: String, val onClick: () -> Unit)

/**
 * The cards side by side, equal widths and heights, 16 apart; the first takes [firstFocus]. Each card is
 * as tall as the tallest and starts its icon at the top, so a one-line card lines up with a two-line one.
 */
@Composable
internal fun WizardCards(cards: List<WizardCard>, firstFocus: FocusRequester? = null) {
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
        cards.forEachIndexed { i, card ->
            StageSurface(
                onClick = card.onClick,
                radius = 22.mpx,
                focusStyle = StageFocus.FX,
                idle = Modifier.background(StageColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(22.mpx)),
                contentAlignment = Alignment.TopStart,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 170.mpx)
                    .then(if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
            ) { focused ->
                Column(Modifier.fillMaxWidth().padding(24.mpx)) {
                    MyOwnTVIcon(card.icon, if (focused) StageColors.Text else StageColors.Muted, Modifier.size(30.mpx))
                    Text(card.title, style = stageText(23, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.mpx))
                    Text(card.line, style = stageText(16, 500).copy(lineHeight = 23.mpxLine()), color = StageColors.Muted, modifier = Modifier.padding(top = 6.mpx))
                }
            }
        }
    }
}

private val LockupTile = Color(0xFFEFE6D2)
private val LockupMark = Color(0xFF14535C)

@Composable
private fun Float.mpxLine() = with(androidx.compose.ui.platform.LocalDensity.current) { this@mpxLine.mpx.toSp() }

@Composable
private fun Int.mpxLine() = toFloat().mpxLine()
