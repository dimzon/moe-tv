package tv.own.myowntv.features.categorylock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.myowntv.R
import tv.own.owntv.core.util.Pin
import tv.own.owntv.core.categorylock.PinAttempt
import tv.own.owntv.core.categorylock.PinAttemptLimiters
import tv.own.owntv.core.categorylock.PinDoor
import tv.own.myowntv.features.categorylock.pinEntryTitle
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageText
import tv.own.myowntv.ui.theme.PopupFontTheme

/**
 * The PIN-locked UIs the lock features share: the numeric PIN dialog, the prompt that verifies
 * against a [PinDoor]'s stored PIN, and the full "screen locked" page. They lived with the profiles
 * feature; profiles are gone, the one global PIN stays — Settings lock, category lock and the
 * Customize PIN all authenticate through these.
 */

/** Numeric PIN entry. Calls [onSubmit] with the entered digits. [compact] renders the small
 *  popup-menu treatment (narrow panel, Caladea font) used by the Customize screen. */
@Composable
internal fun PinDialog(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit, compact: Boolean = false) {
    val dialog: @Composable () -> Unit = { PinDialogBody(title, onSubmit, onDismiss, compact) }
    if (compact) PopupFontTheme(content = dialog) else dialog()
}

@Composable
private fun PinDialogBody(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit, compact: Boolean) {
    var pin by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Explicit start/end links: spatial D-pad search from Cancel would otherwise wander into
    // the locked content BEHIND the dialog before reaching OK.
    val cancelFocus = remember { FocusRequester() }
    val okFocus = remember { FocusRequester() }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = title,
        eyebrow = null,
        width = if (compact) 560.mpx else 700.mpx,
        buttons = {
            tv.own.myowntv.ui.stage.StagePopupButton(
                stringResource(R.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancelFocus).focusProperties { end = okFocus },
                primary = false,
            )
            tv.own.myowntv.ui.stage.StagePopupButton(
                stringResource(R.string.common_ok),
                onClick = { onSubmit(pin) },
                modifier = Modifier.focusRequester(okFocus).focusProperties { start = cancelFocus },
                enabled = pin.length >= 4,
            )
        },
    ) {
        tv.own.myowntv.features.settings.StageFieldRow(
            icon = tv.own.myowntv.ui.components.MyOwnTVIcon.PENCIL,
            label = stringResource(R.string.profiles_pin),
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = stringResource(R.string.profiles_pin_placeholder),
            password = true,
            keyboardType = KeyboardType.NumberPassword,
        )
    }
}

/**
 * Stands in for a PIN-locked screen until the PIN is entered: the screen's [title], a "PIN-locked"
 * note, and the PIN prompt over them (see [PinPrompt]); Back or Cancel calls [onDismiss]. [pin] is the
 * stored value — a salted hash, or a PIN saved before hashing — of the lock on [door].
 */
@Composable
internal fun PinLockedScreen(
    title: String,
    pin: String,
    door: PinDoor,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp, top = tv.own.myowntv.features.shell.components.StageContentTop)
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text(title, style = stageText(42, 800), color = StageColors.Text)
        Text(
            stringResource(R.string.settings_customize_pin_locked),
            style = stageText(18, 500),
            color = StageColors.Muted,
            modifier = Modifier.padding(top = 10.mpx),
        )
    }
    PinPrompt(pin = pin, door = door, onUnlock = onUnlock, onDismiss = onDismiss)
}

/**
 * The PIN prompt on its own, for a door that stays on screen behind it. A wrong PIN keeps the prompt
 * up and says so; Back or Cancel calls [onDismiss]. Wrong entries count only against [door]'s own
 * limit, so guessing at one lock never makes another wait.
 */
@Composable
internal fun PinPrompt(pin: String, door: PinDoor, onUnlock: () -> Unit, onDismiss: () -> Unit) {
    val limiter = koinInject<PinAttemptLimiters>().of(door)
    var wrong by remember { mutableStateOf(false) }
    var waitSeconds by remember { mutableIntStateOf(limiter.waitSeconds()) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(waitSeconds > 0) {
        while (waitSeconds > 0) {
            delay(1_000)
            waitSeconds = limiter.waitSeconds()
        }
    }
    key(attempt) {
        PinDialog(
            title = pinEntryTitle(wrong = wrong, waitSeconds = waitSeconds),
            onSubmit = { entered ->
                when (val result = limiter.attempt { Pin.verifyCustomize(entered, pin) }) {
                    PinAttempt.Accepted -> onUnlock()
                    PinAttempt.Wrong -> { wrong = true; attempt++ }
                    is PinAttempt.Wait -> { wrong = false; waitSeconds = result.seconds; attempt++ }
                }
            },
            onDismiss = onDismiss,
            compact = true,
        )
    }
}
