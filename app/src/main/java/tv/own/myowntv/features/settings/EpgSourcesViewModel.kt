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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.work.EpgSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncState
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.SettingsRepository

/** Manage standalone EPG (XMLTV) sources: list, add (auto-sync), edit, re-sync, delete. */
class EpgSourcesViewModel(
    private val store: EpgSourceStore,
    private val epgRepository: EpgRepository,
    private val sourceRepository: SourceRepository,
    private val settings: SettingsRepository,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    private val epgSyncScheduler: EpgSyncScheduler,
    private val liveEpgReader: LiveEpgReader,
) : ViewModel() {

    /** An existing playlist's EPG feed, offered as a one-tap "fill from playlist" option. */
    data class PlaylistEpg(val name: String, val url: String)

    val sources: StateFlow<List<EpgSource>> =
        store.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per-source EPG auto-refresh selection (Off / Startup / staleness threshold). */
    val autoRefresh: StateFlow<Map<Long, EpgRefresh>> = settings.epgAutoRefresh
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setAutoRefresh(source: EpgSource, refresh: EpgRefresh) {
        viewModelScope.launch { settings.setEpgAutoRefresh(source.id, refresh) }
    }

    /** EPG sources whose own `<icon src>` logos replace the playlist's channel logos. */
    val useLogos: StateFlow<Set<Long>> = settings.epgUseLogos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setUseLogos(source: EpgSource, enabled: Boolean) {
        viewModelScope.launch { settings.setEpgUseLogos(source.id, enabled) }
    }

    fun add(name: String, url: String, userAgent: String? = null, autoRefresh: EpgRefresh = EpgRefresh.OFF, useLogos: Boolean = false) {
        viewModelScope.launch {
            val source = store.add(name, url, userAgent)
            settings.setEpgAutoRefresh(source.id, autoRefresh)
            if (useLogos) settings.setEpgUseLogos(source.id, true)
            epgSyncScheduler.enqueueSync(source.id, "add")
        }
    }

    /**
     * Move [source] one place up or down the list: its guide priority. Live TV's cached now/next
     * came from the old order, so it is dropped; the Guide drops its rows itself the next time it
     * opens, because the ordered source ids it loaded from no longer match.
     */
    fun move(source: EpgSource, up: Boolean) {
        viewModelScope.launch {
            store.move(source.id, up)
            liveEpgReader.clearCache()
        }
    }

    fun resync(source: EpgSource) {
        viewModelScope.launch {
            val base = epgDao.countForSources(listOf(source.id))
            epgSyncScheduler.enqueueSync(source.id, "manual_resync", base)
        }
    }

    fun update(source: EpgSource, name: String, url: String, userAgent: String?) {
        viewModelScope.launch {
            val updated = source.copy(name = name.trim().ifBlank { source.name }, url = url.trim(), userAgent = userAgent?.trim()?.takeIf { it.isNotBlank() })
            store.update(updated)
            val base = epgDao.countForSources(listOf(updated.id))
            epgSyncScheduler.enqueueSync(updated.id, "update", base)
        }
    }

    /** Sources whose guide data is being deleted right now — the row shows a "Deleting…" status and
     *  hides its actions until the (potentially large) DELETE finishes. */
    private val _deletingIds = MutableStateFlow<Set<Long>>(emptySet())
    val deletingIds: StateFlow<Set<Long>> = _deletingIds.asStateFlow()

    fun delete(source: EpgSource) {
        if (source.id in _deletingIds.value) return
        viewModelScope.launch {
            epgSyncScheduler.cancelSync(source.id)
            _deletingIds.value = _deletingIds.value + source.id
            try {
                // NonCancellable + DB-clear-BEFORE-store-remove: a big EPG (100k+ programmes) can take a
                // while to delete. If the user leaves the screen mid-delete, finish anyway — otherwise the
                // source would already be gone from the list while its programmes were left orphaned with
                // nothing left to clean them up.
                withContext(NonCancellable) {
                    epgRepository.clear(source.id)
                    store.remove(source.id)
                }
            } finally {
                _deletingIds.value = _deletingIds.value - source.id
            }
        }
    }

    fun observeSync(sourceId: Long): Flow<EpgSyncState> = epgSyncScheduler.observeSync(sourceId)

    fun cancelSync(source: EpgSource) {
        epgSyncScheduler.cancelSync(source.id)
    }

    /** A source's status line: guide channels and the programmes stored for them. */
    suspend fun counts(id: Long): Pair<Int, Int> =
        epgDao.countGuideChannels(listOf(id)) to epgDao.countForSources(listOf(id))

    /** Playlists whose own EPG can pre-fill the add form (Xtream xmltv.php / M3U url-tvg). */
    suspend fun playlistEpgOptions(): List<PlaylistEpg> =
        sourceRepository.observeSources().first()
            // A comma-separated url-tvg names several feeds; offer each one on its own.
            .flatMap { src -> epgRepository.guideUrls(src).map { PlaylistEpg(src.name, it) } }
}
