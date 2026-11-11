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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import tv.own.myowntv.ui.theme.mpx
import androidx.compose.foundation.layout.fillMaxWidth

/**
 * A numeric input dialog: type a number directly OR nudge it with − / + buttons. Mirrors
 * [StepperDialog]'s chrome but adds a real text field (so big values like 100 are one tap, not 100
 * stepper presses) and an advisory warning when the value exceeds [warnAbove].
 *
 * The [onSet] callback fires live as the value changes (typed or stepped), exactly like
 * [StepperDialog] — so the caller persists immediately and the chip behind the dialog stays in sync.
 *
 * Validation:
 *  - The text field only accepts digits; on Done it is parsed, clamped to [min]..[max], and committed.
 *  - [max] is a hard cap (protects against typos like 999999 on slow TVs).
 *  - [warnAbove] is *advisory only*: a warning line appears, but the user can still save — matching the
 *    product decision "if he still says yes it goes".
 */
@Composable
fun NumberInputDialog(
    title: String,
    value: Int,
    min: Int = 1,
    max: Int,
    step: Int = 1,
    warnAbove: Int? = null,
    warningText: String? = null,
    suffix: String = "",
    /** What the field holds. Defaults to the channel-skip wording this dialog was first written for. */
    fieldLabel: String? = null,
    onSet: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A number nudged by ◀ ▶, saved as it changes: in the page's panel on a settings page,
    // a Stage popup elsewhere. The unit and the advisory warning sit above it; the warning never blocks.
    val showWarn = warnAbove != null && value > warnAbove && warningText != null
    val stepper: @Composable () -> Unit = {
        tv.own.myowntv.features.settings.PanelStepper(
            value = value.toString(),
            onStep = { d -> onSet((value + d * step).coerceIn(min, max)) },
            onReset = onReset,
            onDone = onDismiss,
            preview = if (showWarn || suffix.isNotBlank()) ({
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
                    if (suffix.isNotBlank()) Text(suffix, style = tv.own.myowntv.ui.theme.stageText(15, 600), color = tv.own.myowntv.ui.theme.StageColors.Muted)
                    if (showWarn) Text(
                        warningText.orEmpty(), style = tv.own.myowntv.ui.theme.stageText(15, 600), color = tv.own.myowntv.ui.theme.StageColors.Warn,
                        modifier = Modifier.padding(top = 8.mpx).fillMaxWidth().background(tv.own.myowntv.ui.theme.StageColors.Warn.copy(alpha = 0.12f), RoundedCornerShape(14.mpx)).padding(horizontal = 16.mpx, vertical = 10.mpx),
                    )
                }
            }) else null,
        )
    }
    if (tv.own.myowntv.features.settings.panelEditor(onDismiss) { stepper() }) return
    tv.own.myowntv.ui.stage.StagePopup(onDismiss = onDismiss, title = title, body = fieldLabel) { stepper() }
}

