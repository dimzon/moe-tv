@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.myowntv.features.settings
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
import tv.own.owntv.player.ffmpeg.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.model.HeroKind
import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.model.HomeTrendingStyle
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.TrendingDao
import tv.own.owntv.core.database.entity.TrendingSnapshotEntity
import tv.own.owntv.core.sync.TrendingActivityTracker
import tv.own.owntv.core.trending.TrendingAvailability
import tv.own.owntv.core.trending.trendingAvailability
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeSettingsViewModel(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val trendingDao: TrendingDao,
    private val trendingActivity: TrendingActivityTracker,
    private val syncScheduler: CatalogSyncScheduler,
) : ViewModel() {
    val config: StateFlow<HomeConfig> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(HomeConfig()) else settings.homeConfig(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeConfig())

    /** Whether the expanded Home hero plays its video. Defaults off on low-RAM devices. */
    val heroPreviewEnabled: StateFlow<Boolean> = settings.heroPreviewEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.heroPreviewDefault)

    fun setHeroPreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHeroPreviewEnabled(enabled) }
    }

    private data class TrendingSettingsData(
        val sourceIds: Set<Long> = emptySet(),
        val states: List<TrendingSnapshotEntity> = emptyList(),
        val metadataEnabled: Boolean = true,
    )

    private val trendingData = combine(settings.activeProfileId, settings.metadataConfigFlow) { profileId, metadata ->
        profileId to metadata.enabled
    }.flatMapLatest { (profileId, metadataEnabled) ->
        if (profileId < 0) {
            flowOf(TrendingSettingsData(metadataEnabled = metadataEnabled))
        } else {
            flow {
                val sourceIds = sourceDao.sourceIdsForProfile(profileId).toSet()
                if (sourceIds.isEmpty()) {
                    emit(TrendingSettingsData(metadataEnabled = metadataEnabled))
                } else {
                    emitAll(
                        trendingDao.observeStatesForSources(sourceIds.toList()).map { states ->
                            TrendingSettingsData(sourceIds, states, metadataEnabled)
                        },
                    )
                }
            }
        }
    }

    val trendingAvailability: StateFlow<TrendingAvailability> = combine(trendingData, trendingActivity.active) { data, active ->
        trendingAvailability(
            states = data.states,
            metadataEnabled = data.metadataEnabled,
            building = active.keys.any { it in data.sourceIds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendingAvailability.WaitingForSync)

    private val _devRebuild = MutableStateFlow(DevRebuildState.IDLE)

    /**
     * Maintainer-only ("Rebuild Now Trending" in Home settings, compiled out unless
     * `BuildConfig.DEV_TOOLS` is set). Normal rebuilds only re-download the trending list every few
     * days; this forces one so a reported problem can be reproduced immediately. Deliberately
     * unthrottled — it never reaches a normal build, and a maintainer chasing a bug needs to be able
     * to press it as often as the bug requires.
     */
    fun rebuildTrendingNow() {
        if (_devRebuild.value != DevRebuildState.IDLE) return
        viewModelScope.launch {
            val profileId = settings.activeProfileId.first()
            val sourceIds = if (profileId < 0) emptyList() else sourceDao.sourceIdsForProfile(profileId)
            if (sourceIds.isEmpty()) return@launch
            sourceIds.forEach { syncScheduler.enqueueTrendingRefresh(it, force = true) }
            _devRebuild.value = DevRebuildState.STARTED
            delay(2_500)
            _devRebuild.value = DevRebuildState.IDLE
        }
    }

    val devRebuild: StateFlow<DevRebuildState> = _devRebuild.asStateFlow()

    enum class DevRebuildState { IDLE, STARTED }

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

    fun setHeroInclude(kind: HeroKind, included: Boolean) {
        updateConfig { config ->
            when (kind) {
                HeroKind.LIVE -> config.copy(heroIncludeLive = included)
                HeroKind.MOVIES -> config.copy(heroIncludeMovies = included)
                HeroKind.SERIES -> config.copy(heroIncludeSeries = included)
            }
        }
    }

    /** How Now Trending is drawn: the full hero, or a row of posters. */
    fun setTrendingStyle(style: HomeTrendingStyle) {
        updateConfig { it.copy(trendingStyle = style) }
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
            val pid = settings.activeProfileId.first()
            if (pid < 0) return@launch
            settings.updateHomeConfig(pid) { current -> transform(current) }
        }
    }
}
