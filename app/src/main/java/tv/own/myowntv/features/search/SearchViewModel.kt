@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.myowntv.features.search
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.myowntv.ui.stage.PlaylistMark
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.content.SearchIntent
import tv.own.owntv.core.content.SearchKind
import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Cross-section search over the app's channels and the guide.
 *
 * The searching itself is core's [SearchReader]: the FTS expression, the hidden items and
 * categories and the Customize renames are the same rules on a phone as on a
 * television, so they are not written twice. What stays here is the screen's own behaviour —
 * debouncing and the recents list. A result is never played here: OK goes to it. Core's result
 * object still carries the (VOD-dead) movie and series lists; the television only ever reads the
 * channels and programmes out of it.
 */
class SearchViewModel(
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val searchReader: SearchReader,
) : ViewModel() {

    // Observe the sources reactively so adding/removing a playlist refreshes Search
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    // Per-section Off flags split the id sets so an Off section never surfaces in results.
    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveProfileSources(emptyList()))

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val results: StateFlow<SearchResults> = _query
        .map { it.trim() }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.length < 2) {
                flowOf(SearchResults())
            } else {
                flowOf(search(q))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) {
        if (q.trim() != _query.value.trim()) lastFocusKey = null
        _query.value = q
        if (q.isNotBlank()) _intent.value = null // typing overrides an active launcher intent
    }

    // --- Empty-state launcher (recent search terms + Continue / Unwatched / Channels) ---

    /** Persisted recent search terms (most-recent first). */
    val recentSearches: StateFlow<List<String>> = settings.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearRecentSearches() { viewModelScope.launch { settings.clearRecentSearches() } }

    /** Save the current query into recents — called when the user actually opens a result. */
    fun rememberCurrentQuery() { viewModelScope.launch { settings.addRecentSearch(_query.value) } }

    private val _intent = MutableStateFlow<SearchIntent?>(null)
    val intent: StateFlow<SearchIntent?> = _intent.asStateFlow()

    /** Selecting an intent clears any typed query so the curated list shows; null returns to the launcher. */
    fun setIntent(i: SearchIntent?) {
        _intent.value = i
        if (i != null) _query.value = ""
    }

    /** Curated results for the active empty-state intent (bounded; reuses favourites/history queries). */
    val curatedResults: StateFlow<SearchResults> = combine(_intent, ctx) { i, c -> i to c }
        .flatMapLatest { (i, c) ->
            if (i == null) flowOf(SearchResults())
            else flowOf(searchReader.curated(c, i))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    private suspend fun search(q: String): SearchResults = searchReader.search(ctx.value, q)

    // --- Stage page ---

    /** The filter buttons switched on; none = everything. Kept across new search text; Back clears it. */
    private val _kinds = MutableStateFlow<Set<SearchKind>>(emptySet())
    val kinds: StateFlow<Set<SearchKind>> = _kinds.asStateFlow()
    fun toggleKind(k: SearchKind) { _kinds.value = if (k in _kinds.value) _kinds.value - k else _kinds.value + k }
    fun showOnly(k: SearchKind) { _kinds.value = setOf(k) }
    fun clearKinds() { _kinds.value = emptySet() }

    /** The row a "Go to" left from ("c12", "m34", "s56"), so Back to Search lands on it again. */
    var lastFocusKey: String? = null

    /** Which playlist a row comes from, as on the browse screens; empty with one playlist. */
    val playlistMarks: StateFlow<Map<Long, PlaylistMark>> = ctx
        .map { aps -> if (aps.sources.size < 2) emptyMap() else aps.sources.withIndex().associate { (i, s) -> s.id to PlaylistMark.of(s.name, i) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}
