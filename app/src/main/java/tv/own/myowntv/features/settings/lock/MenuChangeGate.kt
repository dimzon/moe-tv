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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import tv.own.owntv.core.categorylock.MenuChangeGuard
import tv.own.owntv.core.categorylock.PinDoor
import tv.own.myowntv.features.categorylock.PinPrompt

/**
 * Runs a long-press change (hide, move, rename, lock a category, remove from History) at once, or —
 * with the Settings lock's long-press switch on — after the Settings PIN. [MenuChangeGateHost] draws
 * the prompt; one right PIN opens every change until the app is left.
 */
class MenuChangeGate(private val guard: MenuChangeGuard) {

    class Pending(val ask: MenuChangeGuard.Ask, val action: () -> Unit)

    var pending by mutableStateOf<Pending?>(null)
        private set

    fun run(action: () -> Unit) {
        val ask = guard.pinToAsk() ?: return action()
        pending = Pending(ask, action)
    }

    fun unlocked() {
        val done = pending ?: return
        pending = null
        if (guard.unlocked(done.ask)) done.action()
    }

    fun dismiss() {
        pending = null
    }
}

/** The Settings PIN prompt for a guarded long-press change. Placed once, over the whole app. */
@Composable
fun MenuChangeGateHost() {
    val gate: MenuChangeGate = koinInject()
    val waiting = gate.pending ?: return
    PinPrompt(pin = waiting.ask.pin, door = PinDoor.SETTINGS, onUnlock = gate::unlocked, onDismiss = gate::dismiss)
}
