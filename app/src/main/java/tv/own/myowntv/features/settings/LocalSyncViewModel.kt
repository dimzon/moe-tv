package tv.own.myowntv.features.settings
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.companion.CompanionController
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SyncMatrix
import tv.own.owntv.core.sync.local.DiscoveredDevice
import tv.own.owntv.core.sync.local.LocalSyncManager
import tv.own.owntv.core.sync.local.PairedDevice
import tv.own.owntv.core.sync.local.SyncDirection
import tv.own.owntv.core.sync.local.SyncFailure

/**
 * Local sync from the television's side — the same feature the phone has, driven with a D-pad.
 *
 * The television is usually the one being connected TO: it shows a PIN and a QR code and the phone's
 * camera reads it. But it can also go the other way, because a sync only one device can start is not
 * much of a sync — so the phone can be found on the network here too, and the PIN typed with the
 * remote.
 *
 * Everything below is core's ([LocalSyncManager]); this only sequences the screen.
 */
class LocalSyncViewModel(
    private val sync: LocalSyncManager,
    private val settings: SettingsRepository,
) : ViewModel() {


    val paired: StateFlow<List<PairedDevice>> = sync.pairedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * The 4×2 sync matrix (phase ③): what this device gives and reads, per category. The ticks are
     * local to this device and drive every sync — manual and automatic — so they live here, on the
     * main sync screen, not on a per-device row.
     */
    val syncMatrix: StateFlow<SyncMatrix> = settings.syncMatrix
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncMatrix.DEFAULTS)

    fun setSyncMatrix(matrix: SyncMatrix) {
        viewModelScope.launch { settings.setSyncMatrix(matrix) }
    }

    val hosting: StateFlow<CompanionServerState> = sync.hostState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CompanionServerState.Idle)

    val deviceName: String get() = sync.deviceName

    /**
     * Devices asking, while this one is hosting, to sync with it automatically.
     *
     * The far side is suspended on the HTTP request until this is answered, so the question is not
     * something to come back to later: it is answered now or it times out as a no.
     */
    val autoSyncRequests: StateFlow<List<CompanionController.AutoSyncRequest>> = sync.autoSyncRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The answer for a request on the screen. Unknown ids — already timed out — are ignored. */
    fun answerAutoSync(requestId: String, accepted: Boolean) = sync.answerAutoSync(requestId, accepted)

    var found by mutableStateOf<List<DiscoveredDevice>>(emptyList())
        private set

    var step by mutableStateOf<Step?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<SyncFailure?>(null)
        private set


    sealed interface Step {
        data object FindDevice : Step
        data class EnterPin(val address: String, val port: Int) : Step
        data class ChooseDirection(val device: PairedDevice) : Step
        data class Confirm(
            val device: PairedDevice?,
            val direction: SyncDirection,
            val file: File,
            val preview: BackupManager.Preview,
            /** The key that opens [file]. Worked out by core, never typed by anyone. */
            val password: String?,
        ) : Step
        data class Result(val received: BackupManager.ImportSummary?, val sent: Boolean) : Step
    }

    private var discovery: Job? = null
    private var incomingWatcher: Job? = null

    /** Opens the listener the moment the screen appears: on a television, waiting is the usual role. */
    fun startHosting() {
        busy = true
        viewModelScope.launch {
            sync.startHosting().onFailure { error = SyncFailure.Unknown }
            busy = false
        }
        // Guarded: hosting can be switched off and on again, and a second collector would preview the
        // same arriving container twice.
        if (incomingWatcher?.isActive != true) incomingWatcher = viewModelScope.launch {
            sync.incoming.collect { file ->
                val payload = sync.previewIncoming(file).getOrNull() ?: return@collect
                step = Step.Confirm(
                    device = null,
                    direction = SyncDirection.RECEIVE,
                    file = payload.file,
                    preview = payload.preview,
                    password = payload.password,
                )
            }
        }
    }

    fun stopHosting() = sync.stopHosting()

    fun beginPairing() {
        step = Step.FindDevice
        found = emptyList()
        discovery?.cancel()
        discovery = viewModelScope.launch {
            sync.discover().collect { device ->
                if (found.none { it.address == device.address }) found = found + device
            }
        }
    }

    fun chooseAddress(address: String, port: Int) {
        discovery?.cancel()
        step = Step.EnterPin(address, port)
    }

    /**
     * The pairing this discovered device already has here, or null.
     *
     * Its announced id first, because that is the thing that does not change; the address only as a
     * fallback for a device too old to announce one, where a router handing out a new lease would
     * make a known device look new.
     */
    fun pairedMatch(device: DiscoveredDevice): PairedDevice? {
        val known = paired.value
        return device.deviceId.takeIf { it.isNotBlank() }?.let { id -> known.firstOrNull { it.id == id } }
            ?: known.firstOrNull { it.address == device.address }
    }

    /**
     * Picking a device out of the found list. One that is already paired goes straight to its actions:
     * asking for a PIN again would be asking the user to prove something this device already knows.
     */
    fun choose(device: DiscoveredDevice) {
        val existing = pairedMatch(device)
        if (existing == null) {
            chooseAddress(device.address, device.port)
        } else {
            discovery?.cancel()
            step = Step.ChooseDirection(existing)
        }
    }

    fun submitPin(pin: String) {
        val current = step as? Step.EnterPin ?: return
        busy = true
        viewModelScope.launch {
            sync.pair(current.address, current.port, pin)
                .onSuccess { step = Step.ChooseDirection(it) }
                .onFailure { error = SyncFailure.NotAuthorized }
            busy = false
        }
    }

    fun unpair(device: PairedDevice) {
        viewModelScope.launch { sync.unpair(device.id) }
    }

    fun chooseDevice(device: PairedDevice) {
        step = Step.ChooseDirection(device)
    }

    /**
     * Picks the direction and runs it. Since phase ③ there is no per-operation section choice: what
     * leaves and what lands is the device's own sync matrix, applied inside [LocalSyncManager].
     */
    fun chooseDirection(direction: SyncDirection) {
        val device = (step as? Step.ChooseDirection)?.device ?: return
        busy = true
        viewModelScope.launch {
            when (direction) {
                SyncDirection.SEND -> sync.send(device)
                    .onSuccess { step = Step.Result(received = null, sent = true) }
                    .onFailure { error = reason() }
                SyncDirection.RECEIVE, SyncDirection.MERGE -> sync.fetch(device)
                    .onSuccess { payload ->
                        step = Step.Confirm(device, direction, payload.file, payload.preview, payload.password)
                    }
                    .onFailure { error = reason() }
            }
            busy = false
        }
    }

    /** [deviceSettings]: the other device's hardware settings were ticked on the confirm step. */
    fun confirm(deviceSettings: Boolean = false) {
        val current = step as? Step.Confirm ?: return
        busy = true
        viewModelScope.launch {
            sync.apply(current.file, current.password, deviceSettings)
                .onSuccess { summary ->
                    val sent = current.device != null && current.direction == SyncDirection.MERGE &&
                        sync.send(current.device).isSuccess
                    step = Step.Result(received = summary, sent = sent)
                }
                .onFailure { error = reason() }
            busy = false
        }
    }


    fun cancel() {
        (step as? Step.Confirm)?.file?.delete()
        discovery?.cancel()
        step = null
        sync.clearProgress()
    }

    fun dismissError() {
        error = null
    }

    override fun onCleared() {
        discovery?.cancel()
        sync.stopHosting()
        super.onCleared()
    }

    private fun reason(): SyncFailure =
        (sync.progress.value as? tv.own.owntv.core.sync.local.SyncProgress.Failed)?.reason ?: SyncFailure.Unknown

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
