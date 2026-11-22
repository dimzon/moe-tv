package tv.own.myowntv.features.home
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

import androidx.compose.runtime.Immutable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.home.GuideSliceState
import tv.own.owntv.core.home.HeroItem
import tv.own.owntv.core.home.HomeFeedReader
import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.categorylock.sealedCategoryKeys
import tv.own.owntv.player.HeroPreviewEngine

/**
 * The "Keep watching" hero card. The app is live TV only, so the cards are the channels last
 * watched; the preview the dwell starts is the channel's own stream.
 */
@Immutable
data class HomeHeroCard(
    val channel: ChannelEntity,
    val watchedAt: Long,
)

@Immutable
data class HomeUiState(
    val heroCards: List<HomeHeroCard> = emptyList(),
    val activeHeroIndex: Int = 0,
    val recentLive: List<ChannelEntity> = emptyList(),
    val favoriteLive: List<ChannelEntity> = emptyList(),
    val config: HomeConfig = HomeConfig(),
    val recentGuide: GuideSliceState = GuideSliceState(),
    val favoriteGuide: GuideSliceState = GuideSliceState(),
    /**
     * True until the first [HomeViewModel.loadHomeData] completes. Home's queries are already indexed,
     * but on a cold boot their first reads come off slow eMMC (pages not yet in the OS
     * page cache) — that's the ~half-second gap between the shell painting and `home-data`. While that
     * runs Home draws nothing over the background rather than flashing the empty state (which looks wrong
     * for a user who does have history). Flips to false the moment real data publishes, and stays false
     * thereafter (refreshes don't blank it again).
     */
    val isLoading: Boolean = true,
)

/** What the shared top-bar Continue chip points at. Live TV only: the channel last played. */
enum class ContinueKind { LIVE }
enum class ContinueAction { LAST_CHANNEL }

/** A single resumable target: semantic action and display name. */
@Immutable
data class ContinueTarget(
    val kind: ContinueKind,
    /** Display name (channel). */
    val name: String,
    val action: ContinueAction,
    val channelId: String = "",
)

class HomeViewModel(
    private val feed: HomeFeedReader,
    private val channelDao: ChannelDao,
    private val settings: SettingsRepository,
    private val heroPreviewEngine: HeroPreviewEngine,
    private val historyDao: tv.own.owntv.core.database.dao.HistoryDao,
    private val customize: CustomizationStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** "Channel numbers": the Favourites and Recent cards show the provider number with the name. */
    val showChannelNumbers: StateFlow<Boolean> = settings.directTune
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Single entry point for "rebuild the Home rails". Every trigger goes through here rather than
     * calling loadHomeData directly, because there is more than one of them and on a cold start they
     * all fire at once, and the shell runs its own refresh as soon as Home is the selected section.
     * Measured on a TCL, two full loads landed on the database
     * concurrently at the single most contended moment of launch. Conflating buffer + debounce
     * collapses that burst into one load, and collectLatest keeps a genuine invalidation from
     * queueing behind a stale in-flight pass.
     */
    private val reloadRequests = MutableSharedFlow<Unit>(
        replay = 1, // the first request is emitted before the collector below is running — don't lose it
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        viewModelScope.launch {
            reloadRequests.collectLatest {
                // A failed rail load (DB/IO) must not reach the thread's uncaught handler: the Home
                // screen is shared and a single bad query used to take the whole app down.
                runCatching { loadHomeData() }.onFailureReport()
            }
        }
        // A lock closing or opening (the relock on leaving the app, a PIN entry) changes which channels
        // the rows may show. drop(1): the shell asks for the first load.
        viewModelScope.launch {
            customize.sealedCategoryKeys().drop(1).collect { reloadRequests.emit(Unit) }
        }
    }

    // --- The single most-recent resumable channel, for the shared top-bar Continue chip. ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val continueTarget: StateFlow<ContinueTarget?> =
        // The section's customizations ride along so a rename in Customize retitles the chip at once.
        combine(historyDao.observeMostRecent(), customize.sealedCategoryKeys()) { h, _ -> h }
            .flatMapLatest { h ->
                if (h == null) return@flatMapLatest flowOf(null)
                customize.observe(MediaType.LIVE).map { cust -> resolveContinue(h, cust) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun resolveContinue(
        h: tv.own.owntv.core.database.entity.WatchHistoryEntity,
        cust: SectionCustomizations,
    ): ContinueTarget? = channelDao.getById(h.itemId)?.let { c ->
        if (!canContinueWatching(c, customize.sealedCategoryIds(MediaType.LIVE).first())) {
            return@let null
        }
        ContinueTarget(ContinueKind.LIVE, cust.displayName(c), ContinueAction.LAST_CHANNEL, channelId = c.id)
    }

    private val _heroFocused = MutableStateFlow(false)
    private val _previewEnabled = MutableStateFlow(true)
    private val _lastHeroInteractionMs = MutableStateFlow(0L)

    val lastHeroInteractionMs: StateFlow<Long> = _lastHeroInteractionMs.asStateFlow()

    val isPreviewActive: StateFlow<Boolean> =
        combine(_heroFocused, _previewEnabled, settings.heroPreviewEnabled, _uiState) { focused, enabled, setting, state ->
            focused && enabled && setting && state.heroCards.isNotEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPreviewEnabled(enabled: Boolean) {
        _previewEnabled.value = enabled
    }

    fun setHeroFocused(focused: Boolean) {
        _heroFocused.value = focused
    }

    fun navigateHero(index: Int) {
        val items = _uiState.value.heroCards
        if (index !in items.indices) return
        _uiState.value = _uiState.value.copy(activeHeroIndex = index)
    }

    fun onHeroUserNavigate(index: Int) {
        _lastHeroInteractionMs.value = System.currentTimeMillis()
        navigateHero(index)
    }

    fun stopPreview() {
        heroPreviewEngine.stop()
    }

    /**
     * Start the hero preview for [hero] with the SAME request identity the player would use: the
     * playlist's User-Agent plus the channel's own headers. Without them a source that needs a custom
     * UA or a Referer had a home screen that 403'd on every preview while the channel itself tuned
     * fine.
     */
    suspend fun startPreview(hero: HomeHeroCard) {
        // One built-in source: its identity and UA are the hardcoded singleton DTO.
        val source = tv.own.owntv.core.repository.BuiltInSource.current
        heroPreviewEngine.play(
            hero.channel.streamUrl, 0L, source.userAgent,
            tv.own.owntv.core.settings.SourceOverrides.headersWithReferer(hero.channel.httpHeaders, source),
        )
    }

    /** Rebuild the rails (debounced through [reloadRequests]). */
    fun refresh() {
        reloadRequests.tryEmit(Unit)
    }

    /**
     * The rails themselves are core's — the phone app builds the same feed from the same reader. What
     * stays here is what only a television does with it: the hero carousel's position. Core's feed
     * still resolves the (VOD-dead) continuation candidates; this TV surface keeps only the live ones.
     */
    private suspend fun loadHomeData() {
        val previous = _uiState.value
        val data = feed.load()
        val heroCards = data.heroItems.filterIsInstance<HeroItem.LiveHero>()
            .map { HomeHeroCard(it.channel, it.watchedAt) }
        _uiState.value = HomeUiState(
            heroCards = heroCards,
            activeHeroIndex = previous.activeHeroIndex
                .coerceIn(0, (heroCards.size - 1).coerceAtLeast(0)),
            recentLive = data.recentLive,
            favoriteLive = data.favoriteLive,
            config = data.config,
            recentGuide = data.recentGuide,
            favoriteGuide = data.favoriteGuide,
            isLoading = false,
        )
        tv.own.owntv.core.util.Perf.stamp("home-data")
    }

}
