@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
