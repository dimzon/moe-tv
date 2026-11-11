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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay

/**
 * Apply to the root of a menu/dialog opened by an OK press that may still be **held**. A held OK
 * auto-repeats, and those repeats land on the dialog the moment it appears — instantly "clicking" its
 * focused button before the user can choose. This swallows OK/Enter until the key is known to be up.
 *
 * Arming is time-based, not release-based. Waiting for a KeyUp looks correct and is not: the press
 * that opens the dialog is handled on **KeyDown** by the view underneath, so its KeyUp fires while the
 * popup is still being composed and never reaches this modifier. The guard then stayed disarmed and ate
 * the user's next, deliberate press — the long-standing "I have to press Watch from start twice" bug,
 * on both the Guide and the Live TV catch-up picker.
 *
 * A held key repeats far faster than [QUIET_MS], so any gap that long means the key is genuinely up,
 * whether or not the release event was ever delivered here.
 */
private const val QUIET_MS = 300L
private const val POLL_MS = 100L

fun Modifier.longPressMenuGuard(): Modifier = composed {
    var armed by remember { mutableStateOf(false) }
    // 0 = no OK repeat has reached this dialog, i.e. the opening press was already released.
    var lastOkDownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (!armed) {
            delay(POLL_MS)
            if (System.currentTimeMillis() - lastOkDownAt > QUIET_MS) armed = true
        }
    }
    onPreviewKeyEvent { e ->
        if (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter) {
            when (e.type) {
                // Keep pushing the quiet window out while the key is still repeating.
                KeyEventType.KeyDown -> if (!armed) lastOkDownAt = System.currentTimeMillis()
                // A release that *does* arrive arms immediately — no need to wait out the timer.
                KeyEventType.KeyUp -> armed = true
                else -> Unit
            }
            !armed // consume OK while the opening press may still be held
        } else {
            false
        }
    }
}
