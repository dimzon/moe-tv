package tv.own.myowntv.ui.components
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

import tv.own.myowntv.ui.theme.StageColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Text
import tv.own.myowntv.ui.theme.mpx

/** A clock number as two digits, "07". */
internal fun twoDigits(n: Int): String = n.toString().padStart(2, '0')

/**
 * One value column, with an explicit edit mode.
 *
 * OK steps *into* the wheel, Up/Down then change the value, and OK or Back steps back *out*. The
 * obvious design — Up/Down always editing the focused wheel — is a trap on a TV: the wheels sit above
 * the Play button, so a wheel that swallows Down leaves no way to reach it, and the dialog becomes a
 * one-way street. Only while editing are Up/Down consumed; the rest of the time they fall through to
 * ordinary spatial navigation.
 *
 * Back is consumed while editing too, otherwise it would close the whole dialog instead of finishing
 * the edit the user is in the middle of.
 */
@Composable
internal fun TimeWheel(
    value: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val a = tv.own.myowntv.ui.theme.stageAccent
    // OK arms the wheel (accent, arrows shown), ▲ ▼ turn it, OK or Back puts it down. Losing focus
    // (◀ ▶ to a neighbour) must not leave it armed behind the user's back.
    tv.own.myowntv.ui.stage.StageSurface(
        onClick = { editing = !editing },
        radius = 16.mpx,
        focusStyle = tv.own.myowntv.ui.stage.StageFocus.FX,
        idle = if (editing) Modifier.background(a.accent.copy(alpha = 0.18f), RoundedCornerShape(16.mpx)) else Modifier.background(StageColors.Ink.copy(alpha = 0.06f), RoundedCornerShape(16.mpx)),
        modifier = modifier
            .height(120.mpx)
            .onFocusChanged { if (!it.isFocused) editing = false }
            // Preview, so Back is taken before the dialog's own Back can dismiss everything.
            .onPreviewKeyEvent { e ->
                if (!editing || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { onUp(); true }
                    Key.DirectionDown -> { onDown(); true }
                    Key.Back -> { editing = false; true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) { _ ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Transparent rather than absent, so showing the arrows never reflows the row.
            Text("▲", style = tv.own.myowntv.ui.theme.stageText(14, 700), color = if (editing) a.accent else Color.Transparent)
            Text(value, style = tv.own.myowntv.ui.theme.stageText(28, 800), color = if (editing) a.accent else tv.own.myowntv.ui.theme.StageColors.Text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text("▼", style = tv.own.myowntv.ui.theme.stageText(14, 700), color = if (editing) a.accent else Color.Transparent)
        }
    }
}
