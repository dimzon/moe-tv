package tv.own.myowntv.features.profiles
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
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
import tv.own.myowntv.ui.theme.mpx

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
        Text(title, style = tv.own.myowntv.ui.theme.stageText(42, 800), color = tv.own.myowntv.ui.theme.StageColors.Text)
        Text(
            stringResource(R.string.settings_customize_pin_locked),
            style = tv.own.myowntv.ui.theme.stageText(18, 500),
            color = tv.own.myowntv.ui.theme.StageColors.Muted,
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
