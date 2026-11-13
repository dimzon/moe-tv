@file:OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class) // debounce, flatMapLatest

package tv.own.myowntv.features.epg
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.GuideWidthShares

sealed interface EpgMessage {
    data class NoChannelsForQuery(val query: String) : EpgMessage
    data object MismatchedIds : EpgMessage
}


data class EpgUiState(
    /** All channels with guide data in the window; each row loads its own programmes lazily. */
    val channels: List<ChannelEntity> = emptyList(),
    val windowStart: Long = 0,
    val windowEnd: Long = 0,
    val now: Long = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val message: EpgMessage? = null,
)

/**
 * Drives the EPG guide grid. Loads the EPG-capable channels and the programmes in a
 * rolling window from the DB — as many days forward as Settings' "Guide days to keep" stores —
 * and can re-download the guide from the RuTube API via [EpgRepository].
 */
class EpgViewModel(
    private val settings: SettingsRepository,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val epgRepository: EpgRepository,
    private val customize: CustomizationStore,
    private val favoriteDao: tv.own.owntv.core.database.dao.FavoriteDao,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val categoryDao: tv.own.owntv.core.database.dao.CategoryDao,
    private val customCategoryDao: tv.own.owntv.core.database.dao.CustomCategoryDao,
    /** Which guide feeds are syncing right now: the grid waits for them instead of reloading per batch. */
    private val epgActivity: tv.own.owntv.core.sync.EpgActivityTracker,
    /** "Recently watched" in the category list reads the same history Live TV does. */
    private val historyDao: tv.own.owntv.core.database.dao.HistoryDao,
    private val reminderManager: tv.own.owntv.core.reminder.ReminderManager,
) : ViewModel() {

    /** The categories' search text, held here so it survives the player (see LiveViewModel). */
    private val _categoryQuery = kotlinx.coroutines.flow.MutableStateFlow("")
    val categoryQuery: kotlinx.coroutines.flow.StateFlow<String> = _categoryQuery
    fun setCategoryQuery(query: String) { _categoryQuery.value = query }

    /** Every windowed guide read this screen makes. The caches around it stay here — what to keep
     *  depends on how the grid scrolls, which is the screen's business, not core's. */
    private val guideReader =
        tv.own.owntv.core.live.GuideReader(epgDao)

    // The user's customizations — shared by the Guide picker, guide rows, and manual EPG match.
    // It must be initialized before guideCategories (Kotlin property initializers run top-to-bottom).
    private val custom: StateFlow<SectionCustomizations> = customize.observe(MediaType.LIVE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /** Global guide shift in minutes; a per-channel override in [custom] wins over it. */
    private val epgOffset: StateFlow<Int> = settings.epgOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Guide category filter, from the same list as Live TV: null = All channels. */
    private val _categoryFilter = MutableStateFlow<tv.own.owntv.core.live.LiveKey?>(null)
    val categoryFilter: StateFlow<tv.own.owntv.core.live.LiveKey?> = _categoryFilter.asStateFlow()

    val guideWidthShares: StateFlow<GuideWidthShares?> = combine(
        settings.guideWidthEnabled,
        settings.guideWidthShares,
    ) { enabled, shares -> shares.takeIf { enabled } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setCategoryFilter(key: tv.own.owntv.core.live.LiveKey?) {
        _categoryFilter.value = key.takeUnless { it == tv.own.owntv.core.live.LiveKey.All }
    }

    private val _state = MutableStateFlow(EpgUiState())
    val state: StateFlow<EpgUiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The rows the grid has already read, newest-used first and **hard-capped**.
     *
     * This used to be an unbounded [java.util.concurrent.ConcurrentHashMap] pre-filled by one batch
     * query that read the entire guide window. On a real television that batch was 349,077
     * programmes in a single list, and the app died of an OutOfMemoryError inside it. Rows are now
     * read one channel at a time as they scroll into view, and the cache is an LRU: the grid keeps
     * what the user is actually looking at and forgets the rest, so memory is a function of the
     * screen rather than of the catalogue.
     *
     * Keyed by channel id and shift, not by EPG id: two channels can share one guide channel, and the
     * same channel at two offsets is two different rows.
     */
    private val rowCache = object : LinkedHashMap<String, List<EpgProgrammeEntity>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<EpgProgrammeEntity>>?) =
            size > MAX_CACHED_ROWS
    }

    private fun cacheKey(channel: ChannelEntity, shift: Int) = "${channel.id}|$shift"

    private fun cachedRow(key: String): List<EpgProgrammeEntity>? = synchronized(rowCache) { rowCache[key] }

    private fun putRow(key: String, rows: List<EpgProgrammeEntity>) {
        synchronized(rowCache) { rowCache[key] = rows }
    }

    private fun clearRows() {
        synchronized(rowCache) { rowCache.clear() }
    }

    private fun cachedRowCount(): Int = synchronized(rowCache) { rowCache.size }

    /** The row prefetch in flight. One at a time — see [prefetchAfter]. */
    private var prefetchJob: kotlinx.coroutines.Job? = null

    /** The load in flight, so the next one can cancel it instead of racing it. */
    private var loadJob: kotlinx.coroutines.Job? = null
    private val loadMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile private var loadedWindow: Pair<Long, Long>? = null
    // The window the cached rows belong to. A sort / filter / category change keeps the same window,
    // so the rows stay valid and nothing is re-read.
    @Volatile private var cachedWindow: Pair<Long, Long>? = null
    @Volatile private var lastStored = -1 // stored programme count the cache was built from (data-change guard)
    // Bumped when cached rows are dropped, so visible rows know to ask again.
    private val _cacheRevision = MutableStateFlow(0)
    val cacheRevision: StateFlow<Int> = _cacheRevision.asStateFlow()

    /** Minutes this channel's guide is shifted by (per-channel override, else the global offset). */
    private fun shiftFor(channel: ChannelEntity): Int =
        tv.own.owntv.core.epg.EpgShift.minutesFor(custom.value, channel, epgOffset.value)

    /** Synchronous cache peek — lets a re-composed row render instantly without a loading flash. */
    fun cachedProgrammes(channel: ChannelEntity): List<EpgProgrammeEntity>? =
        cachedRow(cacheKey(channel, shiftFor(channel)))

    /**
     * One row's programmes, read as the row scrolls into view.
     *
     * This is now the **only** way the grid gets programmes. It is one indexed query per channel, so
     * eight visible rows cost eight small reads instead of one read of the whole database — which is
     * what the deleted batch did, and what ran the television out of memory.
     */
    suspend fun programmesFor(channel: ChannelEntity): List<EpgProgrammeEntity> {
        val s = _state.value
        val shift = shiftFor(channel)
        val key = cacheKey(channel, shift)
        cachedRow(key)?.let { return it }
        // The read itself — window, shift and all — is core's; only what to keep is this screen's.
        val list = guideReader.row(channel, custom.value, epgOffset.value, s.windowStart, s.windowEnd)
        // An empty answer is not kept: while many rows load at once the provider fallback can still be
        // on its way, and a cached empty row stayed blank until something else re-read it.
        if (list.isNotEmpty()) putRow(key, list)
        prefetchAfter(channel)
        return list
    }

    /**
     * Warm the rows just below the one that was asked for, so flinging the grid does not draw blanks.
     *
     * One job at a time, and the newest wins: rows come into view one after another, so each request
     * cancels the previous run rather than adding to it. That is what keeps this a prefetch and not
     * the N+1 query storm the deleted batch load was originally written to prevent — at most
     * [PREFETCH_AHEAD] small indexed reads are ever in flight, sequentially, and the LRU bounds what
     * they cost to keep.
     */
    private fun prefetchAfter(channel: ChannelEntity) {
        val channels = _state.value.channels
        val from = channels.indexOfFirst { it.id == channel.id }
        if (from < 0) return
        val next = channels.subList((from + 1).coerceAtMost(channels.size), (from + 1 + PREFETCH_AHEAD).coerceAtMost(channels.size))
        if (next.isEmpty()) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            val s = _state.value
            for (ahead in next) {
                val key = cacheKey(ahead, shiftFor(ahead))
                if (cachedRow(key) != null) continue
                guideReader.row(ahead, custom.value, epgOffset.value, s.windowStart, s.windowEnd)
                    .takeIf { it.isNotEmpty() }?.let { putRow(key, it) }
            }
        }
    }

    /** One channel's programmes from [dayStart] for a day, whole — synopsis and details — for the
     *  Program details page. Not cached: the page reads one day at a time, on demand. */
    suspend fun dayProgrammes(channel: ChannelEntity, dayStart: Long): List<EpgProgrammeEntity> =
        guideReader.row(channel, custom.value, epgOffset.value, dayStart, dayStart + DAY_MS, withText = true)

    /** The whole programme — synopsis and details — for the programme block (the grid load drops both). */
    suspend fun programmeFull(programmeId: Long): EpgProgrammeEntity? =
        runCatching { epgDao.programmeById(programmeId) }.getOrNull()

    init {
        // Re-filter the grid as the user types (DB-level, so it searches ALL guide channels, not
        // just the visible rows). drop(1): the screen triggers the initial load itself.
        _query
            .drop(1)
            .debounce(300)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        // Reload the guide when its own sort, or (for the LIVE_TV mode) the Live sort, changes.
        // drop(1): the screen triggers the initial load itself.
        settings.sortGuide
            .drop(1)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        settings.guideShowEmpty
            .drop(1)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        // A catalogue sync repopulates `channels` without touching `epg_programmes`; without this
        // the guide kept the empty list it built before the first import finished.
        channelDao.countAll()
            .drop(1)
            .distinctUntilChanged()
            .debounce(GUIDE_DATA_SETTLE_MS)
            .onEach { load() }
            .launchIn(viewModelScope)
        // Reload when the category filter changes (#8).
        _categoryFilter
            .drop(1)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        // Reload when the guide's own data changes underneath an open screen — the guide re-synced. Leaving the Guide and coming back already picks it up (`load()` runs
        // on every mount and compares the stored count), but a background auto-refresh finishing
        // while the grid is on screen had no way to show itself. Room reports every write to the
        // programme table, so this waits for the writes to stop rather than reloading per batch, and
        // `load()`'s own stored-count guard means a settled sync that changed nothing costs nothing.
        epgDao.countFlow()
            // A big feed writes for minutes, one batch every few seconds; the debounce alone rebuilt the
            // whole grid (≈2 s of database work) every time a pause crossed it, all through the sync.
            // So nothing reloads while a feed is still syncing — the change is picked up when it ends.
            .combine(epgActivity.active.map { it.isEmpty() }.distinctUntilChanged()) { count, idle -> count to idle }
            .filter { (_, idle) -> idle }
            .map { (count, _) -> count }
            .distinctUntilChanged()
            .debounce(GUIDE_DATA_SETTLE_MS)
            // The first value is the guide as it already stands — unless a sync was running when the
            // screen opened, in which case the first value is that sync's result and must be loaded.
            .drop(if (epgActivity.active.value.isEmpty()) 1 else 0)
            .onEach { load() }
            .launchIn(viewModelScope)
        // Reload when a category lock closes or opens (the relock on leaving the app, a PIN entry), so
        // the grid and the zap list armed from it follow. drop(1): the screen triggers the initial load.
        customize.observeVisible(MediaType.LIVE).map { it.sealedCategories }
            .distinctUntilChanged()
            .drop(1)
            .onEach { load() }
            .launchIn(viewModelScope)
    }

    /** The Guide's current sort, for the header button. */
    val sortGuide: StateFlow<SettingsRepository.GuideSort> = settings.sortGuide
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.GuideSort.LIVE_TV)

    /** Order ▾ › Show › Channels without guide: list every channel, so a blank one can be matched from here. */
    val showEmpty: StateFlow<Boolean> = settings.guideShowEmpty
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setShowEmpty(show: Boolean) {
        viewModelScope.launch {
            settings.setGuideShowEmpty(show)
            // Do not wait for the preference collector: applying the filter is the point of the tap.
            load()
        }
    }

    /** Order ▾ › Show › Video preview: the focused channel plays in the pane; off shows its logo only. */
    val guidePreview: StateFlow<Boolean> = settings.guidePreview
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setGuidePreview(show: Boolean) {
        viewModelScope.launch { settings.setGuidePreview(show) }
    }

    /** "OK previews first" for the Guide: on, OK brings a channel into the preview; off, OK watches it. */
    val guidePreviewOnOk: StateFlow<Boolean> = settings.guidePreviewOnOk
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The Order menu's choice; remembered, so the guide opens this way next time. */
    fun setGuideSort(sort: SettingsRepository.GuideSort) {
        viewModelScope.launch { settings.setSortGuide(sort) }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** The channel last tuned from the guide — the screen refocuses its row after fullscreen exits. */
    var lastTunedChannelId: String? = null
        private set

    /** Set when the player closes. The guide is not on screen while the player is, so the mark is kept here:
     *  the next focus restore goes to the channel that was playing and arms its preview. */
    var playerClosed: Boolean = false

    /** Record that this channel was tuned from the guide, so focus returns to its row on Back.
     *  Call this before delegating playback to liveVm, so lastTunedChannelId is set correctly. */
    fun noteChannelTuned(channel: ChannelEntity) {
        lastTunedChannelId = channel.id
    }

    // A live-start path used to live here — a `play(channel)` that tuned a Guide channel straight
    // onto its own engine copy. It bypassed the ExoPlayer-first ladder and the learned decode
    // quirks, and it recorded its own history row. It was unreachable (the shell always supplies
    // the callbacks), so EpgScreen now REQUIRES them and this copy is gone: LiveViewModel is the
    // one place a live channel starts.

    /** Live channels the user has favourited — so the Guide can show/toggle a channel's star. */
    val favoriteChannelIds: StateFlow<Set<String>> = favoriteDao.observeFavoriteIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Add/remove a channel from Favourites directly from the Guide (channel long-press / detail). */
    fun toggleFavoriteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            if (favoriteChannelIds.value.contains(channel.id)) {
                userDataWriter.removeFavorite(channel.id)
            } else {
                favoriteDao.add(tv.own.owntv.core.database.entity.FavoriteEntity(itemId = channel.id))
            }
            // The "Favorites" guide sort shows only favourited channels — refresh it to match.
            if (settings.sortGuide.first() == SettingsRepository.GuideSort.FAVORITES) load()
        }
    }

    // --- Programme reminders ----------------------------------------------------------

    /** The reminders: the bell in a cell, and "Reminder set" on the programme's button. */
    val reminderRows: StateFlow<List<tv.own.owntv.core.database.entity.ReminderEntity>> =
        reminderManager.observe()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How early a reminder comes (Settings › Sources & guide › Reminder time) — the menu's "5 min before". */
    val reminderLeadMinutes: StateFlow<Int> = settings.reminderLeadMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.reminder.ReminderSchedule.DEFAULT_LEAD_MINUTES)

    fun reminderFor(channel: ChannelEntity, programme: EpgProgrammeEntity): tv.own.owntv.core.database.entity.ReminderEntity? =
        reminderRows.value.firstOrNull { it.channelId == channel.id && it.startMs == programme.startMs }

    /** Remind me — or, when this programme already has a reminder, take it away again. */
    fun toggleReminder(channel: ChannelEntity, programme: EpgProgrammeEntity) {
        viewModelScope.launch {
            reminderFor(channel, programme)?.let { reminderManager.dismiss(it.id); return@launch }
            reminderManager.add(
                channel.id, programme.title,
                programme.startMs, programme.stopMs, reminderLeadMinutes.value,
            )
        }
    }

    /** The channel's own guide shift in minutes, or null when it follows the global offset. */
    fun currentEpgShift(channel: ChannelEntity): Int? =
        tv.own.owntv.core.epg.EpgShift.overrideFor(custom.value, channel)

    /** The global guide shift — the per-channel dialog's "follow global" default. */
    fun globalEpgShift(): Int = epgOffset.value

    /** Shift this channel's guide by [minutes] (null → follow the global offset), then refresh. */
    fun setEpgShift(channel: ChannelEntity, minutes: Int?) {
        viewModelScope.launch {
            val key = CustomizeKeys.channel(channel)
            customize.setEpgShift(MediaType.LIVE, key, minutes)
            // Rows re-read the shift from `custom` — wait for the DataStore edit to land there first,
            // or the refresh below can still render with the old offset.
            kotlinx.coroutines.withTimeoutOrNull(1_000) { custom.first { it.epgShifts[key]?.toIntOrNull() == minutes } }
            // Cached rows are keyed by shift, so the old offset's rows are simply stale. One cache now,
            // so this drops them all rather than only the shifted ones.
            clearRows()
            _cacheRevision.value++ // visible rows re-read with the new shift
        }
    }

    private suspend fun refreshAllEpgFromNetwork() {
        runCatching { epgRepository.refresh() }
    }

    // A guide-local `zap(delta)` used to live here, stepping the guide list and calling the deleted
    // `play()`. It was already unreachable: a Guide tune goes through LiveViewModel.watchFromGuide,
    // which sets the shell's zapSource to LIVE_TV, so CH+/CH- always used LiveViewModel.zap — the one
    // with the ExoPlayer ladder, the pending-tune anchor and the bounded rebuild.

    /**
     * Rebuild the grid's channel list.
     *
     * **Serialised, and the newest wins.** A guide sync writes in batches and each batch changes the
     * stored programme count, so this is called again and again while one is running. It used to start
     * a fresh coroutine every time with nothing to stop the previous one; on a real television
     * six of them were in flight at once, each holding its own copy of the window, and the app died of
     * an OutOfMemoryError. Cancelling the one in progress makes the answer the latest one rather than
     * a race between six stale ones, and the mutex closes the gap while a cancelled load unwinds.
     */
    fun load() {
        val previous = loadJob
        loadJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            loadMutex.withLock { runLoad() }
        }
    }

    private suspend fun runLoad() {
        val loadStartedAt = android.os.SystemClock.elapsedRealtime()
        // Show the spinner only on the FIRST load. The EpgViewModel is shared, so on re-entry the guide
        // is already populated — keep the existing list on screen and refresh it silently, so opening the
        // Guide menu is instant instead of flashing a spinner and re-rendering from scratch every time.
        if (_state.value.channels.isEmpty()) _state.value = _state.value.copy(loading = true, message = null)
        val now = System.currentTimeMillis()
        val nowAligned = now - (now % HALF_HOUR_MS) // align to the half hour
        // Always retain two recent hours so the centered "now" marker has programmes on its left.
        val lookbackMs = GUIDE_VISIBLE_PAST_MS
        val windowStart = nowAligned - lookbackMs
        // How far the grid can scroll forward is the SAME value that decides how much is stored.
        // Storing a week and only letting the user reach tomorrow would be half a feature — and the
        // 48-hour horizon this replaced is the reason the guide appeared to "run dry" at all.
        val windowEnd = nowAligned + settings.guideDaysToKeep.first() * DAY_MS

        // Respect customizations: hidden channels stay out of the guide, renames show.
        val cust = customize.observeVisible(MediaType.LIVE).first()
        val q = _query.value.trim()
        val rawChannels = if (settings.guideShowEmpty.first()) channelDao.channelsForGuide(q, MAX_CHANNELS)
            else channelDao.channelsWithGuide(q, MAX_CHANNELS)
        val favoriteIds = favoriteDao.observeFavoriteIds().first().toSet()
        val sortGuideMode = settings.sortGuide.first()
        // Hidden categories keep their channels out of the guide too (parity with Live TV), and a
        // filter pointing at a now-hidden category falls back to "All" instead of an empty grid.
        val hiddenCatIds = if (cust.hiddenCategories.isEmpty()) {
            emptySet()
        } else {
            tv.own.owntv.core.content.AdultCategoryClassifier.hiddenCategoryIds(
                categoryDao.observe().first(),
                cust.hiddenCategories,
            )
        }
        val filter = _categoryFilter.value
        // A provider folder keeps out the channels the user moved from it into a custom category.
        val folderKey = (filter as? tv.own.owntv.core.live.LiveKey.Folder)
            ?.let { categoryDao.getById(it.id) }?.let(CustomizeKeys::category)
        val memberIds: Set<String>? = when (filter) {
            is tv.own.owntv.core.live.LiveKey.Custom -> if (filter.id in cust.sealedCategories) emptySet() else customCategoryDao.itemIds(filter.id).toSet()
            tv.own.owntv.core.live.LiveKey.Favorites -> favoriteIds
            tv.own.owntv.core.live.LiveKey.History ->
                historyDao.getAllOnce().mapTo(HashSet()) { it.itemId }
            else -> null
        }
        // Provider and A–Z are Live TV's own lists: in a folder, a custom category or Favourites the guide
        // takes the order the Live TV queries give, moved channels in their place; elsewhere, All included,
        // the playlist's order or plain A–Z.
        val alphaSort = sortGuideMode == SettingsRepository.GuideSort.ALPHA
        val liveTvSort = alphaSort || sortGuideMode == SettingsRepository.GuideSort.PROVIDER || sortGuideMode == SettingsRepository.GuideSort.LIVE_TV
        val liveOrder: Map<String, Int>? = if (!liveTvSort) null else when (filter) {
            is tv.own.owntv.core.live.LiveKey.Folder -> folderKey?.let { channelDao.snapshotByCategoryManual(filter.id, it, alphaSort, MAX_CHANNELS) }
            is tv.own.owntv.core.live.LiveKey.Custom -> customCategoryDao.snapshotChannels(filter.id, alphaSort, MAX_CHANNELS)
            tv.own.owntv.core.live.LiveKey.Favorites ->
                channelDao.snapshotFavoritesManual(tv.own.owntv.core.database.entity.ContentOrderEntity.FAV_CONTEXT, MAX_CHANNELS)
            else -> null
        }?.withIndex()?.associate { (i, ch) -> ch.id to i }
        // Heavy work — filter hidden, apply renames + manual EPG matches, sort, category-filter — runs off
        // the main thread (#3/#5) so a 50k-channel playlist never freezes the UI building the guide list.
        val channels = withContext(Dispatchers.Default) {
            val auto = rawChannels
                .filter { CustomizeKeys.channel(it) !in cust.hiddenItems && !tv.own.owntv.core.database.ChannelMembership.allExcluded(it, cust.sealedCategoryIds) }
                .filter {
                    memberIds != null || tv.own.owntv.core.database.ChannelMembership.visible(it, hiddenCatIds)
                }
                .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
            val matched = auto
            // The Order menu: Provider (also the old stored LIVE_TV value), A–Z,
            // Favourites first (and the legacy CATCHUP value, no longer offered in the menu).
            // The last two only float those channels to the top.
            val byAlpha = compareBy<ChannelEntity> { it.name.lowercase() }
            val byProvider = compareBy<ChannelEntity>({ it.sourceId }, { it.sortOrder }, { it.name.lowercase() })
            val byLiveTv = liveOrder?.let { order -> compareBy<ChannelEntity> { order[it.id] ?: Int.MAX_VALUE }.then(if (alphaSort) byAlpha else byProvider) }
            when (sortGuideMode) {
                SettingsRepository.GuideSort.ALPHA -> matched.sortedWith(byLiveTv ?: byAlpha)
                SettingsRepository.GuideSort.PROVIDER -> matched.sortedWith(byLiveTv ?: byProvider)
                SettingsRepository.GuideSort.FAVORITES -> matched.sortedWith(compareByDescending<ChannelEntity> { it.id in favoriteIds }.then(byProvider))
                // LIVE_TV, the old default, is the provider's order too (Channel number was the same thing).
                SettingsRepository.GuideSort.LIVE_TV -> matched.sortedWith(byLiveTv ?: byProvider)
            }.let { sorted ->
                when (filter) {
                    null, tv.own.owntv.core.live.LiveKey.All -> sorted
                    is tv.own.owntv.core.live.LiveKey.Folder -> sorted.filter { ch ->
                        tv.own.owntv.core.database.ChannelMembership.holds(ch, filter.id) && (folderKey == null || cust.movedFromOrigin[CustomizeKeys.channel(ch)] != folderKey)
                    }
                    else -> sorted.filter { it.id in memberIds.orEmpty() }
                }
            }
        }
        val stored = epgDao.countAll()


        // Per-row programmes are loaded in ONE batched query and grouped into rowCache here, instead of
        // each row firing its own programmesForChannel (an N+1 query storm on cold open). Only re-batch
        // when the window or sources actually change — a sort / filter / category change keeps the same
        // window, so the cache stays valid and we reuse it. Grouping runs off the main thread.
        // Also re-batch when the stored programme count changed (a sync, or a post-match top-up, added
        // data) even if the window is unchanged — so freshly-matched channels' guides appear.
        val windowChanged = cachedWindow != (windowStart to windowEnd) || stored != lastStored
        // Logged so a trace shows which of the three conditions fired: a
        // sync can re-trigger the whole-table scan through the count alone.
        val rebuildReason = when {
            !windowChanged -> "cache-reused"
            cachedWindow == null -> "first-load"
            cachedWindow != (windowStart to windowEnd) -> "window-moved"
            else -> "stored-count-changed"
        }
        lastStored = stored
        if (windowChanged) {
            // Dropping the cached rows is the whole of it. There is no preload any more: the rows
            // on screen re-read themselves through the indexed per-channel query, which costs one
            // small query each instead of materialising the window. That is also why this is now
            // safe to run repeatedly during a sync — it is a map clear, not a 349,077-row read.
            cachedWindow = windowStart to windowEnd
            clearRows()
            _cacheRevision.value++
        }

        // A first sync still running has nothing stored yet; the sync pill already says so.
        // (The old "guide synced channels but no programmes" case is gone with epg_channels: the
        // syncer only writes programmes now, so stored == 0 simply means no guide data yet.)
        val message = when {
            stored == 0 -> null
            channels.isEmpty() && q.isNotBlank() -> EpgMessage.NoChannelsForQuery(q)
            channels.isEmpty() -> EpgMessage.MismatchedIds
            else -> null
        }
        _state.value = EpgUiState(
            channels = channels, windowStart = windowStart, windowEnd = windowEnd, now = now,
            loading = false, message = message,
        )

        tv.own.owntv.core.CorePerf.log {
            "guide_load channels=${channels.size} rawChannels=${rawChannels.size} " +
                "stored=$stored " +
                "rebuilt=$windowChanged reason=$rebuildReason cachedRows=${cachedRowCount()} " +
                "lookbackH=${lookbackMs / 3_600_000} " +
                "totalMs=${android.os.SystemClock.elapsedRealtime() - loadStartedAt}"
        }

        // A second background pass used to merge the older guide history in afterwards, because
        // the blocking pass could not afford the whole lookback. Both are gone: a row is read for
        // the full window in one query, so the history is simply there when the row is drawn.
    }

    companion object {
        // A sync writes the guide in batches, so every batch is reported. This must be longer than
        // the gap between two batch writes, or it coalesces nothing and the grid reloads per batch —
        // which is exactly what it used to do. Measured on a real television:
        // batches land 2.3-6.1 s apart, and 1,500 ms never once coalesced them. 10 s clears the
        // longest observed gap with room to spare; a background refresh showing up ten seconds after
        // it settles is not something anyone is waiting on.
        private const val GUIDE_DATA_SETTLE_MS = 10_000L
        private const val HALF_HOUR_MS = 30L * 60 * 1000
        private const val GUIDE_VISIBLE_PAST_MS = 2L * 60 * 60 * 1000
        // How many guide rows to keep read. A screen shows ~8; this is generous enough that scrolling
        // back never re-reads, and small enough that the grid's memory does not grow with the
        // catalogue. ~100 programmes a row, so a few megabytes at the cap.
        private const val MAX_CACHED_ROWS = 240
        // Rows read ahead of the one that came into view. Roughly a screen's worth on a television,
        // measure before making it two screens; do not raise it on a hunch.
        private const val PREFETCH_AHEAD = 10
        private const val DAY_MS = 24L * 60 * 60 * 1000
        // Generous safety bound only (rows load lazily, so this is about the channel list itself).
        private const val MAX_CHANNELS = 20_000
    }
}
