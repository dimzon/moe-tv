@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.myowntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Home screen's own settings. The Trending machinery (availability, the dev "rebuild now") went with
 * the trending rail — Movies & Series are gone, and Home now draws only live rails.
 */
class HomeSettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {
    val config: StateFlow<HomeConfig> = settings.homeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeConfig())

    /** Whether the expanded Home hero plays its video. Defaults off on low-RAM devices. */
    val heroPreviewEnabled: StateFlow<Boolean> = settings.heroPreviewEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.heroPreviewDefault)

    fun setHeroPreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHeroPreviewEnabled(enabled) }
    }

    fun setRowHidden(row: HomeRow, hidden: Boolean) {
        updateConfig { config -> config.copy(hidden = if (hidden) config.hidden + row else config.hidden - row) }
    }

    fun move(row: HomeRow, up: Boolean) {
        updateConfig { config ->
            val rows = config.settingsRows.toMutableList()
            val from = rows.indexOf(row)
            if (from < 0) {
                config
            } else {
                val to = if (up) from - 1 else from + 1
                if (to !in rows.indices) {
                    config
                } else {
                    rows.add(to, rows.removeAt(from))
                    config.copy(order = rows + config.order.filterNot { it.implemented })
                }
            }
        }
    }

    fun moveToEdge(row: HomeRow, top: Boolean) {
        updateConfig { config ->
            val rows = config.settingsRows.toMutableList()
            val from = rows.indexOf(row)
            if (from < 0) {
                config
            } else {
                val to = if (top) 0 else rows.lastIndex
                if (to == from) {
                    config
                } else {
                    rows.add(to, rows.removeAt(from))
                    config.copy(order = rows + config.order.filterNot { it.implemented })
                }
            }
        }
    }

    fun setHeroInclude(included: Boolean) {
        updateConfig { config -> config.copy(heroIncludeLive = included) }
    }

    fun setLiveRowMode(row: HomeRow, mode: HomeLiveRowMode) {
        updateConfig { config ->
            when (row) {
                HomeRow.RECENT_CHANNELS -> config.copy(recentLiveMode = mode)
                HomeRow.FAVORITE_CHANNELS -> config.copy(favoriteLiveMode = mode)
                else -> config
            }
        }
    }

    private fun updateConfig(transform: (HomeConfig) -> HomeConfig) {
        viewModelScope.launch {
            settings.updateHomeConfig { current -> transform(current) }
        }
    }
}
