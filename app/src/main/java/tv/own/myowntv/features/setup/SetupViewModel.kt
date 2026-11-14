package tv.own.myowntv.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.SingleSource
import tv.own.owntv.core.settings.DataRefreshPolicy
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.dataRefreshPolicy
import tv.own.owntv.core.settings.setDataRefreshPolicy

/**
 * Drives the first-run wizard's only remaining data choice: how often the catalogue and the guide
 * refresh themselves. Content is no longer imported here — the built-in RuTube source is a constant
 * and [tv.own.owntv.core.sync.AutoRefresh] fills missing data from the application process.
 *
 * The wizard writes the choice straight through, like its language, zoom and icon steps: no draft
 * state and no Apply. Every choice, the six-hour default included, lands in both existing per-source
 * maps, so a backup carries the schedule the user saw.
 */
class SetupViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    val policy: StateFlow<DataRefreshPolicy> =
        settings.dataRefreshPolicy(SingleSource.id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataRefreshPolicy.DEFAULT)

    fun setPolicy(policy: DataRefreshPolicy) {
        viewModelScope.launch { settings.setDataRefreshPolicy(SingleSource.id, policy) }
    }
}
