package tv.own.myowntv.features.settings.lock
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.requiresPin
import tv.own.myowntv.features.categorylock.PinLockedScreen

/**
 * Shows [content] (Settings) only once the Settings PIN has been entered, when its
 * Settings lock is on. Back from the PIN prompt calls [onDismiss] without opening Settings.
 */
@Composable
fun SettingsPinGate(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val vm = koinViewModel<SettingsPinViewModel>()
    val lock by vm.lock.collectAsState()
    if (!lock.loaded) return
    // Decided when the visit starts: leaving Settings locks it again, and turning the lock on
    // from inside Settings does not throw the user out.
    var unlocked by remember { mutableStateOf(!lock.requiresPin(unlocked = false)) }
    val pin = lock.pin
    if (pin != null && lock.requiresPin(unlocked)) {
        PinLockedScreen(
            title = stringResource(MainSection.SETTINGS.labelRes),
            pin = pin,
            door = tv.own.owntv.core.categorylock.PinDoor.SETTINGS,
            onUnlock = { unlocked = true },
            onDismiss = onDismiss,
        )
        return
    }
    content()
}
