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

import tv.own.myowntv.ui.theme.LocalStagePalette
import tv.own.myowntv.ui.theme.stageText
import tv.own.myowntv.ui.theme.StageColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.format.rememberSystemTimeFormatter
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.stage.drawBoxShadow
import tv.own.myowntv.ui.components.drawStageGlyph

internal object GuideGridDefaults {
    /** The channel column when the guide widths are not customised (48 → 344 of 1920). */
    const val ChannelColShare = 296f / 1920f
    /** `.gcell` rows: 56 high, 62 apart. */
    val RowHeight = 56.mpx
    val RowGap = 6.mpx
    /** 3.5 hours across the 1512 px timeline. */
    val PxPerMin = 7.2.mpx
    const val SlotMin = 30
}

/**
 * One channel's programmes as Stage cells (`.gcell`): white 4.5%, the one on now 8.5%, past ones
 * at half opacity, radius 14, 3 px apart; title 18/700, then the reminder bell icon and the time in
 * 14 dim. The cell at [focusTime] is the focused one: accent fill, 2 px focus
 * ring and glow. One Canvas per row rather than a node per programme, so a week of guide scrolls light.
 */
@Composable
internal fun ProgrammeStripCanvas(
    programmes: List<EpgProgrammeEntity>,
    windowStart: Long,
    windowEnd: Long,
    /** Read while drawing, not in composition: a 30-second tick redraws the strip, not every row. */
    now: () -> Long,
    focusTime: Long?,
    reminderStarts: Set<Long>,
    /** How far the timeline is scrolled, shared by every row and the ruler. Read only while drawing. */
    scrollPx: () -> Int,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val a = tv.own.myowntv.ui.theme.stageAccent
    val px = with(density) { 1.mpx.toPx() }
    val pxPerMin = with(density) { GuideGridDefaults.PxPerMin.toPx() }
    val titleStyle = tv.own.myowntv.ui.theme.stageText(18, 700).copy(textDirection = TextDirection.Content)
    val timeStyle = tv.own.myowntv.ui.theme.stageText(14, 400).copy(fontFeatureSettings = "tnum", textDirection = TextDirection.Content)
    val formatTime = rememberSystemTimeFormatter()
    val rangeTemplate = stringResource(R.string.content_live_time_range_plain)
    val labels = remember(programmes, formatTime, rangeTemplate) {
        programmes.map { String.format(java.util.Locale.ROOT, rangeTemplate, formatTime(it.startMs), formatTime(it.stopMs)) }
    }
    val textColor = tv.own.myowntv.ui.theme.StageColors.Text
    val dimColor = tv.own.myowntv.ui.theme.StageColors.Dim
    val ink = tv.own.myowntv.ui.theme.StageColors.Ink
    val palette = tv.own.myowntv.ui.theme.LocalStagePalette.current
    // A row with nothing in the window says so, rather than looking broken.
    val emptyText = stringResource(R.string.content_epg_no_programme)
    // Clipped at the timeline's left edge only, so a focused cell's glow still spills up and down.
    Canvas(Modifier.fillMaxSize()) {
        val scrollPx = scrollPx()
        val currentNow = now()
        clipRect(left = 0f, top = -size.height, right = size.width + 40 * px, bottom = size.height * 2) {
        // The focused cell's ring. In light the focus colour is the accent, the cell's own fill, so the
        // ring stands off it with a page-coloured gap first (`.mine.light .gcell.f`: 2.5 page + 2.5 accent).
        fun focusRing(x0: Float, w: Float, h: Float, r: CornerRadius) {
            if (palette.isLight) {
                for ((colour, out) in listOf(palette.page to 1.25f * px, a.accent to 3.75f * px)) {
                    drawRoundRect(colour, Offset(x0 - out, -out), Size(w + 2 * out, h + 2 * out), CornerRadius(r.x + out), style = Stroke(2.5f * px))
                }
            } else {
                drawRoundRect(a.focus, Offset(x0 - px, -px), Size(w + 2 * px, h + 2 * px), CornerRadius(r.x + px), style = Stroke(2 * px))
            }
        }
        val viewW = size.width
        val h = size.height
        val r = CornerRadius(14 * px)
        // Feeds sometimes carry two programmes for the same time. Each one is drawn from where the one
        // before it ends, so they never sit on top of each other; one hidden completely is left out.
        var shownUntil = windowStart
        var drawnAny = false
        // The cursor on a stretch with nothing on (a feed that stops): that gap is the focused cell.
        if (focusTime != null && programmes.none { focusTime in it.startMs until it.stopMs }) {
            val gapStart = (programmes.lastOrNull { it.stopMs <= focusTime }?.stopMs ?: windowStart).coerceAtLeast(windowStart)
            val gapEnd = (programmes.firstOrNull { it.startMs > focusTime }?.startMs ?: windowEnd).coerceAtMost(windowEnd)
            val gx0 = (((gapStart - windowStart) / 60_000f) * pxPerMin - scrollPx + 3 * px).coerceAtLeast(0f)
            val gx1 = (((gapEnd - windowStart) / 60_000f) * pxPerMin - scrollPx - 3 * px).coerceAtMost(viewW)
            if (gx1 > gx0) {
                val box = androidx.compose.ui.geometry.Rect(gx0, 0f, gx1, h)
                drawBoxShadow(a.accent.copy(alpha = 0.4f), 34 * px, r.x, dy = 12 * px, bounds = box)
                drawRoundRect(a.accent, Offset(gx0, 0f), Size(gx1 - gx0, h), r)
                focusRing(gx0, gx1 - gx0, h, r)
                val label = measurer.measure(emptyText, timeStyle.copy(color = a.onAccent), maxLines = 1, softWrap = false)
                drawText(label, topLeft = Offset(gx0 + 14 * px, (h - label.size.height) / 2f))
                drawnAny = true
            }
        }
        programmes.forEachIndexed { i, p ->
            val s0 = p.startMs.coerceIn(windowStart, windowEnd).coerceAtLeast(shownUntil)
            val e0 = p.stopMs.coerceIn(windowStart, windowEnd)
            if (e0 <= s0) return@forEachIndexed
            shownUntil = e0
            val x = ((s0 - windowStart) / 60_000f) * pxPerMin - scrollPx.toFloat() + 3 * px
            val w = ((e0 - s0) / 60_000f) * pxPerMin - 6 * px
            if (w <= 0f || x + w <= 0f || x >= viewW) return@forEachIndexed
            drawnAny = true
            val focused = focusTime != null && focusTime in p.startMs until p.stopMs
            val isNow = currentNow in p.startMs until p.stopMs
            val fade = if (!focused && p.stopMs <= currentNow) 0.5f else 1f
            val box = androidx.compose.ui.geometry.Rect(x, 0f, x + w, h)
            if (focused) {
                drawBoxShadow(a.accent.copy(alpha = 0.4f), 34 * px, r.x, dy = 12 * px, bounds = box)
                drawRoundRect(a.accent, Offset(x, 0f), Size(w, h), r)
                focusRing(x, w, h, r)
            } else {
                drawRoundRect(ink.copy(alpha = (if (isNow) 0.085f else 0.045f) * fade), Offset(x, 0f), Size(w, h), r)
            }
            // Text starts at the visible part of a cell that began before the timeline's left edge.
            val tx = maxOf(x, 0f)
            val textW = (x + w - tx - 28 * px).toInt()
            if (textW <= 4) return@forEachIndexed
            val titleColor = if (focused) a.onAccent else textColor.copy(alpha = fade)
            val smallColor = if (focused) Color.Black.copy(alpha = 0.6f) else dimColor.copy(alpha = fade)
            val iconColor = if (focused) a.onAccent else a.accent.copy(alpha = fade)
            val title = measurer.measure(p.title, titleStyle.copy(color = titleColor), overflow = TextOverflow.Ellipsis, maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = textW))
            drawText(title, topLeft = Offset(tx + 14 * px, 8 * px))
            // The small line: icons first, 8 apart, then the time.
            var cx = tx + 14 * px
            val lineTop = 8 * px + title.size.height + 3 * px
            val icon = 14 * px
            fun glyph(g: MyOwnTVIcon, c: Color, filled: Boolean = false) {
                if (cx + icon > x + w - 14 * px) return
                drawStageGlyph(g, c, Offset(cx, lineTop + 2 * px), icon, filled)
                cx += icon + 8 * px
            }
            if (p.startMs in reminderStarts) glyph(MyOwnTVIcon.BELL, iconColor)
            val timeW = (x + w - 14 * px - cx).toInt()
            if (timeW > 4) {
                val time = measurer.measure(labels[i], timeStyle.copy(color = smallColor), overflow = TextOverflow.Ellipsis, maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = timeW))
                drawText(time, topLeft = Offset(cx, lineTop))
            }
        }
        // Nothing on anywhere in view: say so, rather than a row that looks broken.
        if (!drawnAny) {
            drawRoundRect(ink.copy(alpha = 0.03f), Offset(3 * px, 0f), Size(viewW - 6 * px, h), r)
            val label = measurer.measure(emptyText, timeStyle.copy(color = dimColor), maxLines = 1, softWrap = false)
            drawText(label, topLeft = Offset(17 * px, (h - label.size.height) / 2f))
        }
    } }
}

