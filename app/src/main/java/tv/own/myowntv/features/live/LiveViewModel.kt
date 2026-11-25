@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.myowntv.features.live
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

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.filter
import androidx.paging.map
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.myowntv.features.customize.MoveTarget
import tv.own.owntv.core.customize.CategoryRestorePlan
import tv.own.owntv.core.customize.planCategoryRestore
import tv.own.owntv.core.customize.restoreCategoryToDefault
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.sealsItemIn
import tv.own.owntv.core.categorylock.lockAwareCountsByContexts
import tv.own.owntv.core.categorylock.visibleOnceRead
import tv.own.owntv.core.categorylock.whileAllSealed
import tv.own.owntv.core.customize.CategoryMove
import tv.own.owntv.core.customize.CategoryRailEditor
import tv.own.owntv.core.customize.railCategories
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SourceOverrides
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.liveLandingKey
import tv.own.myowntv.features.search.positionOf
import tv.own.owntv.core.live.isChannelVisible
import tv.own.owntv.core.live.liveCountFlow
import tv.own.owntv.core.live.livePagingSource
import tv.own.owntv.core.live.serialize
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.live.LiveEpgReader

class LiveViewModel(
    private val appContext: Context,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    /** Shared with the Guide: one now/next cache, so an invalidation reaches both screens. */
    private val epgReader: LiveEpgReader,
    val previewEngine: tv.own.owntv.player.LivePreviewEngine,
    private val contentOrderDao: ContentOrderDao,
    private val customCategoryDao: CustomCategoryDao,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
    /** Cards the user pinned to the Android TV home screen (core's TvHomePublisher). */
    private val tvHomePublisher: TvHomePublisher,
) : ViewModel() {

    /** Ids of the channels already on the TV home screen — drives the long-press menu label. */
    val homePinnedIds: StateFlow<Set<String>> = tvHomePublisher.pinnedIds

    init {
        viewModelScope.launch { runCatching { tvHomePublisher.reload() } }
    }

    /** Pin/unpin a channel card on the Android TV launcher. Returns the new pinned state. */
    suspend fun toggleHomeScreen(channel: ChannelEntity): Boolean {
        tvHomePublisher.toggle(channel)
        return homePinnedIds.value.contains(channel.id)
    }

    data class ChannelMoveState(val items: List<ChannelEntity>, val activeIndex: Int, val contextKey: String, val carried: Set<String> = emptySet())
    private val _moveState = MutableStateFlow<ChannelMoveState?>(null)
    val moveState: StateFlow<ChannelMoveState?> = _moveState.asStateFlow()

    /** Hide and reorder a category from the rail — the same editor Settings → Customize writes through. */
    private val categoryEditor = CategoryRailEditor(customize, categoryDao)

    private val _categoryMoveState = MutableStateFlow<CategoryMove?>(null)
    val categoryMoveState: StateFlow<CategoryMove?> = _categoryMoveState.asStateFlow()

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** "OK previews first" for Live TV: focus no longer plays the preview, OK does. */
    val livePreviewOnOk: StateFlow<Boolean> = settings.livePreviewOnOk
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** List ordering for this section (Playlist order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortLive
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.PLAYLIST)

    fun toggleSort() {
        viewModelScope.launch {
            settings.setSortLive(
                if (sortMode.value == SettingsRepository.SortMode.PLAYLIST) SettingsRepository.SortMode.ALPHA
                else SettingsRepository.SortMode.PLAYLIST,
            )
        }
    }

    private val livePreviewAudio: StateFlow<Boolean> = settings.livePreviewAudio
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Settings → "Channel numbers": shows the provider number in the lists and the player, and enables
     *  typing one to tune. Off hides every number without touching the stored data. Eager so a playback
     *  path can read [StateFlow.value] synchronously when building its [tv.own.myowntv.player.MediaMeta]. */
    val showChannelNumbers: StateFlow<Boolean> = settings.directTune
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The "#123" the player shows under the channel name, in the top bar and on the channel card.
     *  Gating it here rather than at each call site means every playback path —
     *  is covered by the one check, and a null simply omits the line as it did before numbers existed. */
    private fun channelNumberLabel(channel: ChannelEntity): String? =
        channel.number?.takeIf { showChannelNumbers.value }?.let { "#$it" }

    private data class Ctx(
        val sourceIds: List<Long>,
        val sourceNames: Map<Long, String>,
    )

    // Observe the sources REACTIVELY so adding/removing a playlist refreshes Live TV
    // immediately (it used to be read once at startup, so a new playlist showed nothing until restart).
    // Full sources by id — lets the synchronous play() paths tell a source whose URL still has to
    // be resolved at play time from one whose final URL is already stored, without a DB round-trip.
    private var sourceById: Map<Long, tv.own.owntv.core.database.entity.SourceEntity> = emptyMap()

    private val ctx: StateFlow<Ctx> = activeProfileSources()
        .map { aps ->
            sourceById = aps.sources.associateBy { it.id }
            val liveIds = aps.liveSourceIds
            Ctx(
                liveIds,
                aps.sources.filter { it.id in liveIds }.associate { it.id to it.name },
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(emptyList(), emptyMap()))

    private val folderContextKeys: StateFlow<Map<Long, String>> = ctx
        .flatMapLatest { c ->
            categoryDao.observe().map { cats ->
                cats.associateBy({ it.id }, { CustomizeKeys.category(it) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Contexts that actually have manual-order rows: only those folders pay the
     *  unindexable content_order join-sort; everything else stays on the plain indexed query. */
    private val orderedContexts: StateFlow<Set<String>> = ctx
        .flatMapLatest {
            contentOrderDao.observeContextKeys().map { it.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()


    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _previewChannel = MutableStateFlow<ChannelEntity?>(null)
    val previewChannel: StateFlow<ChannelEntity?> = _previewChannel.asStateFlow()

    /**
     * The channel the preview pane was last asked to play — by a preview or a full-screen tune, whose
     * stream the pane takes back on return. Usually [previewChannel]; with "OK previews first" on in
     * Live TV the highlight moves on while this stays, so the pane keeps playing what OK chose.
     */
    private val _paneChannel = MutableStateFlow<ChannelEntity?>(null)
    val paneChannel: StateFlow<ChannelEntity?> = _paneChannel.asStateFlow()

    /**
     * The channel the mini player is playing while the user browses Live TV or the Guide behind it.
     * Focus still moves [_previewChannel] (the info pane follows the highlighted row), so the playing
     * channel is kept here and put back when the player is expanded: the HUD's guide card,
     * CH+/- and Multiview all read [_previewChannel] and must act on what is playing, not on
     * the row last highlighted. Null when no browse moved away from it.
     */
    @Volatile private var miniPlaying: ChannelEntity? = null
    @Volatile private var miniOpen = false

    /** The channel the player is on: during a mini-player browse, the one playing, not the one highlighted. */
    val playingChannel: ChannelEntity? get() = miniPlaying ?: _previewChannel.value

    /** The shell's player mode: [mini] while the mini player is up, [open] while any player is. */
    fun onPlayerMode(mini: Boolean, open: Boolean) {
        miniOpen = mini
        if (mini) return
        val playing = miniPlaying ?: return
        miniPlaying = null
        if (open && _previewChannel.value?.id != playing.id) _previewChannel.value = playing
    }

    /** Focus moved [_previewChannel] to [channel]; keep what the mini player is playing first. */
    private fun browseTo(channel: ChannelEntity) {
        if (miniOpen && miniPlaying == null) _previewChannel.value?.takeIf { it.id != channel.id }?.let { miniPlaying = it }
        _previewChannel.value = channel
    }

    /** [channel] is being tuned: it is what plays now, wherever the player is. */
    private fun tuned(channel: ChannelEntity) {
        miniPlaying = null
        _previewChannel.value = channel
        _paneChannel.value = channel
    }

    /** Every guide read this screen makes, and the now/next cache that used to live here — see
     *  [LiveEpgReader]. The shift it applies is passed in at each call, so this view model stays the
     *  single place that knows a customization changed. */

    /** Bumped when a channel's EPG mapping changes so [nowNext] reloads for the same focused channel. */
    private val epgRefresh = MutableStateFlow(0)

    /** Programme turnover is independent of LiveScreen's lifetime: the HUD and pane need it too. */
    private val epgMinuteTick = MutableStateFlow(0L)

    /** Now/next for the focused channel, fetched (debounced), together with the id it was read for.
     *  While the debounced read for a newly focused channel is pending, the pair still holds the
     *  previous channel's answer — every reader must check the id ([nowNextForChannel],
     *  [rowProgramme]) before putting a programme under a channel. */
    val keyedNowNext: StateFlow<Pair<String?, EpgNowNext?>> = combine(_previewChannel, epgRefresh, epgMinuteTick) { ch, tick, minute -> Triple(ch, tick, minute) }
        .debounce(350)
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second && a.third == b.third }
        .mapLatest { (ch, _, _) -> ch?.id to ch?.let { epgReader.nowNext(it, custom.value, epgOffset.value) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to null)

    /** Null until the debounced answer belongs to the channel in focus: after a zap the old
     *  channel's programme must not appear under the new one, in the list, the pane or the HUD. */
    val nowNext: StateFlow<EpgNowNext?> = combine(keyedNowNext, _previewChannel, epgMinuteTick) { keyed, ch, _ ->
        nowNextForChannel(keyed, ch?.id, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The channel-change banner reads the same guarded answer as the rows and the pane. */
    val zapGuide: StateFlow<EpgNowNext?> get() = nowNext

    /**
     * The focused channel's REAL primary category name — resolved from its membership's first
     * category, NOT from whatever rail item the user is currently browsing (Favorites / History /
     * All are browse contexts, not the channel's own categories). Null when the channel has no
     * category or it can't be resolved. Drives the category chip + genre-dot in the preview pane.
     */
    val previewCategoryName: StateFlow<String?> = _previewChannel
        .mapLatest { ch ->
            val id = ch?.let(tv.own.owntv.core.database.ChannelMembership::primaryCat) ?: return@mapLatest null
            delay(150) // a quick scroll cancels this before the lookup runs
            categoryDao.getById(id)?.name
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /** The user's customizations for Live TV, null until read: the item lists wait for them. */
    private val customOnceRead: StateFlow<SectionCustomizations?> =
        customize.visibleOnceRead(MediaType.LIVE)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The user's hide/rename/reorder customizations for Live TV. */
    private val custom: StateFlow<SectionCustomizations> = customOnceRead.filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /** Shared by every Live surface; constructed before the EPG-offset collector can clear it. */
    private val nowPlayingCache = NowPlayingCache(viewModelScope, MAX_NOW_PLAYING)

    /** Global guide shift (minutes); a per-channel override in [custom] wins over it. Changing it
     *  drops the now/next cache so the details pane reflects the new offset straight away. */
    // The first value is the stored one, not a change — and it can arrive synchronously during
    // construction. The cache above already exists, so a change can safely clear it.
    private val epgOffset: StateFlow<Int> = settings.epgOffsetMinutes
        .withIndex()
        .onEach { if (it.index > 0) { epgReader.clearCache(); epgRefresh.value++; clearNowPlaying() } }
        .map { it.value }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** The user's custom combined categories with live member counts — the "Move to…" dialog's list. */
    val moveTargets: StateFlow<List<MoveTarget>> = combine(ctx, custom) { c, cust -> c to cust }
        .flatMapLatest { (c, cust) ->
            if (cust.customCategories.isEmpty()) flowOf(emptyList())
            else customCategoryDao.lockAwareCountsByContexts(
                cust.customCategories.map { it.id }, cust.sealedCategoryIds,
            ).map { counts ->
                cust.customCategories.map { cc ->
                    MoveTarget(
                        id = cc.id,
                        displayName = cust.categoryNames[cc.id] ?: cc.name,
                        count = counts.firstOrNull { it.contextKey == cc.id }?.count ?: 0,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The stable key of a provider folder ([null] when the folder vanished) — the Move dialog's origin. */
    fun folderKey(id: Long): String? = folderContextKeys.value[id]

    /** Creates a custom category — the Move dialog's "＋ New category…" flow. */
    fun createCustomCategory(name: String) {
        viewModelScope.launch {
            customize.createCustomCategory(MediaType.LIVE, name)
        }
    }

    /**
     * Moves (or copies, [keepInOrigin]) one channel into a custom category. The item is
     * appended at the category's tail (maxPosition + 1). Without [keepInOrigin] the item leaves its
     * origin: a favorite row is deleted, a custom-category membership row is deleted, and a provider
     * folder is marked in movedFromOrigin — the pager chain then drops it from that folder while
     * keeping it in All / search / recent.
     */
    fun moveToCategory(itemKey: String, itemId: String, originKey: String, targetId: String, keepInOrigin: Boolean) {
        if (targetId == originKey) return
        viewModelScope.launch {
            customCategoryDao.appendItem(targetId, itemId)
            if (!keepInOrigin) {
                when {
                    originKey == ContentOrderEntity.FAV_CONTEXT -> userDataWriter.removeFavorite(itemId)
                    CustomizeKeys.isCustom(originKey) -> userDataWriter.removeCustomCategoryMember(originKey, itemId)
                    else -> customize.setItemMovedFromOrigin(MediaType.LIVE, itemKey, originKey, moved = true)
                }
            }
        }
    }

    /**
     * Category DB ids of the hidden categories. Hiding a category used to only drop its rail
     * folder — its channels still showed in "All Channels", search and recently-watched (so hiding the
     * adult groups left them all visible under ALL). Resolving the hidden category keys to ids here lets
     * those lists filter the channels out, so hiding a group hides its channels everywhere.
     */
    private val hiddenCategoryIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { c ->
            combine(categoryDao.observe(), custom) { cats, cust ->
                tv.own.owntv.core.content.AdultCategoryClassifier.hiddenCategoryIds(
                    cats,
                    cust.hiddenCategories,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** The locked provider folders not open this session: their channels leave every count. */
    private val sealedCategoryIds: Flow<Set<Long>> = custom.map { it.sealedCategoryIds }.distinctUntilChanged()

    /** Customizations + resolved hidden-category ids, bundled so the list pipeline takes one flow. */
    private data class CustState(val cust: SectionCustomizations, val hiddenCats: Set<Long>)
    private val custResolved: StateFlow<CustState?> = combine(customOnceRead, hiddenCategoryIds) { c, h -> c?.let { CustState(it, h) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The rail with the context it was built for, so the category restore can tell a rail complete for
     * these playlists from the one still on screen from before a switch. Built from the current edits,
     * read here rather than through [custom]: that one starts empty and a rail built from an empty one
     * lacks the custom categories and shows hidden ones.
     */
    private val railSnapshot: StateFlow<Pair<Ctx, List<LiveRailItem>>> = ctx
        .flatMapLatest { c ->
            combine(
                categoryDao.observe(),
                customize.visibleOnceRead(MediaType.LIVE).filterNotNull(),
                combine(sortMode, settings.liveBuiltIns) { s, b -> s to b },
            ) { cats, cust, (sort, builtIns) ->
                // The categories follow the sort chip too (A–Z by shown name, custom categories
                // included); a category the user moved keeps its place in either sort. Custom categories
                // ride the SAME customization keys, so renames/hides/reorders apply to them too.
                val folders = cats.railCategories(
                    cust,
                    alphabetical = sort == SettingsRepository.SortMode.ALPHA,
                )
                c to defaultRail.filter { builtIns.shows(it.key) } + folders.map { e ->
                    LiveRailItem(
                        key = e.categoryId?.let { LiveKey.Folder(it) } ?: LiveKey.Custom(e.customId!!),
                        title = e.displayName, locked = e.locked, sealed = e.sealed,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ctx.value to defaultRail)

    val railItems: StateFlow<List<LiveRailItem>> = railSnapshot
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultRail)

    val channels: Flow<PagingData<ChannelEntity>> = combine(
        _selected,
        ctx,
        _search.map { it.trim() }.debounce(300).distinctUntilChanged(),
        sortMode,
        custResolved.filterNotNull(),
    ) { key, c, query, sort, cs -> Args(key, c, query, sort, cs) }
        // Rebuild the pager when a folder gains/loses manual order: the fast-path plain
        // PagingSource doesn't observe content_order, so the switch must recreate it.
        .combine(orderedContexts) { args, _ -> args }
        // …and whenever the manual order actually changes: the Kotlin-sorted ManualOrderPagingSource
        // has no self-invalidation (Room's @Query source used to observe content_order itself), so a
        // reorder must recreate it. observeManualVersion re-emits on any channels/content_order/member write.
        .combine(channelDao.observeManualVersion()) { args, _ -> args }
        .flatMapLatest { (key, c, query, sort, cs) ->
            // Customizations are applied to each fresh PagingData inside the pager chain — a PagingData
            // that the UI already collected must never be re-transformed (Paging forbids re-collection,
            // which is why hiding a channel used to crash). A customization change re-creates the pager.
            Pager(PagingConfig(pageSize = 80, prefetchDistance = 40, initialLoadSize = 120, maxSize = 400)) {
                pagingSource(key, c, query, sort)
            }.flow.map { paging ->
                val cust = cs.cust
                val hiddenCats = cs.hiddenCats
                val movedFrom = cust.movedFromOrigin
                if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty() && hiddenCats.isEmpty() && movedFrom.isEmpty() && cust.sealedCategories.isEmpty()) paging
                else paging
                    .filter { ch ->
                        isChannelVisible(ch, cust, hiddenCats, key) &&
                            // Moved-out items leave ONLY their origin folder (they stay in All/search).
                            (movedFrom[CustomizeKeys.channel(ch)]?.let { origin ->
                                key !is LiveKey.Folder || origin != folderContextKeys.value[key.id]
                            } ?: true)
                    }
                    .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
            }
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode, val cs: CustState)

    /** Hide the focused channel from all lists (undo via Settings → Customize → Hidden channels). */
    fun hideChannel(channel: ChannelEntity) {
        if (_previewChannel.value?.id == channel.id) stopPreview()
        viewModelScope.launch {
            customize.setItemHidden(MediaType.LIVE, CustomizeKeys.channel(channel), channel.name, true)
        }
    }

    /** Rename the channel (blank restores the provider's name). */
    fun renameChannel(channel: ChannelEntity, newName: String?) {
        viewModelScope.launch {
            customize.renameItem(MediaType.LIVE, CustomizeKeys.channel(channel), newName)
        }
    }

    /** Hide a category by its rail key (undo via Settings → Customize). */
    fun hideCategory(key: LiveKey) {
        viewModelScope.launch {
            categoryEditor.hide(MediaType.LIVE, key)
        }
    }

    /** The content_order / movedFromOrigin context behind a rail key, or null for All / Favorites / History. */
    private fun categoryContextKey(key: LiveKey): String? = when (key) {
        is LiveKey.Folder -> folderContextKeys.value[key.id]
        is LiveKey.Custom -> key.id
        else -> null
    }

    /** What restoring [key]'s category to the playlist default would undo (empty when nothing). */
    suspend fun categoryRestorePlan(key: LiveKey): CategoryRestorePlan {
        val contextKey = categoryContextKey(key) ?: return CategoryRestorePlan()
        return planCategoryRestore(customize, contentOrderDao, MediaType.LIVE, contextKey, customCategoryDao) {
            when (key) {
                is LiveKey.Folder -> channelDao.snapshotByCategoryManual(key.id, contextKey, false, Int.MAX_VALUE)
                    .mapTo(HashSet()) { CustomizeKeys.channel(it) }
                else -> customCategoryDao.stableItemKeys(contextKey).toSet()
            }
        }
    }

    /** Back to the playlist's order and contents (see [restoreCategoryToDefault]). */
    fun restoreCategory(key: LiveKey) {
        val contextKey = categoryContextKey(key) ?: return
        viewModelScope.launch {
            restoreCategoryToDefault(customize, contentOrderDao, MediaType.LIVE, contextKey, categoryRestorePlan(key))
        }
    }

    fun enterCategoryMoveMode(key: LiveKey) {
        if (key !is LiveKey.Folder && key !is LiveKey.Custom) return
        viewModelScope.launch {
            val c = ctx.value
            _categoryMoveState.value = categoryEditor.beginMove(
                type = MediaType.LIVE,
                alphabetical = sortMode.value == SettingsRepository.SortMode.ALPHA,
                key = key,
            )
        }
    }

    fun moveCategory(from: Int, to: Int) {
        val s = _categoryMoveState.value ?: return
        if (from !in s.rows.indices || to !in s.rows.indices || from == to) return
        val rows = s.rows.toMutableList().apply { add(to, removeAt(from)) }
        _categoryMoveState.value = s.copy(rows = rows, activeIndex = to, targetKey = rows[to].key, carried = s.carried + s.rows[from].key)
    }

    fun commitCategoryMove() {
        val s = _categoryMoveState.value ?: return
        _categoryMoveState.value = null
        viewModelScope.launch {
            customize.saveCategoryMove(MediaType.LIVE, s.keys, s.carried)
        }
    }

    fun cancelCategoryMove() {
        _categoryMoveState.value = null
    }

    /** Shift this channel's guide by [minutes] (null → follow the global EPG offset). */
    fun setEpgShift(channel: ChannelEntity, minutes: Int?) {
        viewModelScope.launch {
            val key = CustomizeKeys.channel(channel)
            customize.setEpgShift(MediaType.LIVE, key, minutes)
            // The guide read takes the shift off `custom` — let the DataStore edit reach it before refreshing.
            kotlinx.coroutines.withTimeoutOrNull(1_000) { custom.first { it.epgShifts[key]?.toIntOrNull() == minutes } }
            epgReader.invalidate(channel.id) // the cached now/next was built on the old shift
            epgRefresh.value++
        }
    }

    /** The channel's own guide shift in minutes, or null when it follows the global offset. */
    fun currentEpgShift(channel: ChannelEntity): Int? =
        tv.own.owntv.core.epg.EpgShift.overrideFor(custom.value, channel)

    /** The global guide shift, shown as the per-channel dialog's "follow global" default. */
    fun globalEpgShift(): Int = epgOffset.value

    /** The playlist's name, for the channel menu's "category · playlist" line. */
    fun sourceNameOf(sourceId: Long): String? = sourceById[sourceId]?.name

    /** List view or Guide view: what Live TV shows, and so what it opens in next time. */
    val liveView: StateFlow<SettingsRepository.LiveView> = settings.liveView
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.LiveView.LIST)

    fun setLiveView(view: SettingsRepository.LiveView) {
        viewModelScope.launch { settings.setLiveView(view) }
    }

    /** Live TV layout: the categories as a sheet on Left (Stage), or a column always on screen. */
    val liveLayout: StateFlow<SettingsRepository.LiveLayout> = settings.liveLayout
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.LiveLayout.STAGE)

    /**
     * The channel count beside every category in the sheet or column: each fixed entry and custom
     * category by the same query as the header's count, and every provider folder from one grouped
     * query. Only collected while the categories are on screen.
     */
    val railCounts: StateFlow<Map<LiveKey, Int>> = combine(ctx, hiddenCategoryIds, railItems, sealedCategoryIds) { c, hidden, items, sealed ->
            val single = items.filter { it.key !is LiveKey.Folder }.map { item ->
                countFlow(item.key, c, hidden, sealed).map { mapOf(item.key to it) }
            }
            // A sealed folder shows no count: how many channels it holds is the lock's business.
            val folders = channelDao.observeCountsByCategory()
                .map { rows -> rows.filter { it.categoryId !in sealed }.associate { LiveKey.Folder(it.categoryId) as LiveKey to it.itemCount } }
            combine(single + folders) { parts -> parts.fold(emptyMap<LiveKey, Int>()) { acc, m -> acc + m } }.throttleLatest()
        }
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val count: StateFlow<Int> = combine(_selected, ctx, hiddenCategoryIds, sealedCategoryIds) { key, c, hidden, sealed -> countFlow(key, c, hidden, sealed) }
        .flatMapLatest { it.throttleLatest() } // Cap live COUNT re-runs during bulk sync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val favoriteIds: StateFlow<Set<String>> = favoriteDao.observeFavoriteIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val recentlyWatched: StateFlow<List<ChannelEntity>> = channelDao.recentlyWatched(20)
        .combine(custResolved.filterNotNull()) { list, cs ->
            list.filter {
                CustomizeKeys.channel(it) !in cs.cust.hiddenItems &&
                    !tv.own.owntv.core.database.ChannelMembership.allExcluded(it, cs.cust.sealedCategoryIds) &&
                    tv.own.owntv.core.database.ChannelMembership.visible(it, cs.hiddenCats)
            }
                .map { ch -> cs.cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Set while this view model is serving More -> Favourites or More -> History rather than the
     * browse section. Those screens are one folder each: the selection is theirs to fix, and it must
     * not be persisted as the remembered category.
     */
    private var lockedKey: LiveKey? = null

    /** What the browse section was showing before the pin, so [unlock] can put it back. */
    private var previousKey: LiveKey? = null

    fun lock(key: LiveKey) {
        if (lockedKey == null) previousKey = _selected.value
        lockedKey = key
        _selected.value = key
    }

    /**
     * Release the pin and put the browse section back where it was.
     *
     * **This is not optional bookkeeping.** On the television this view model is a single instance
     * shared between the browse section and the More screens, so a pin that outlived the screen that
     * took it froze the section's category rail — focusable, but every click a no-op. The screen that
     * locks therefore unlocks on dispose.
     */
    fun unlock() {
        val previous = previousKey ?: return
        lockedKey = null
        previousKey = null
        _selected.value = previous
    }

    /** The user picks [key]: it is shown and remembered (see [LiveCategoryMemory]). Ignored under a pin. */
    fun select(key: LiveKey) {
        if (lockedKey != null) return
        categoryMemory.picked(key)
        _selected.value = key
    }

    /**
     * Shows [key] for the app rather than the user — a startup choice, the playing channel's category:
     * not remembered, and it wins over a restore still waiting. Under a More-screen pin it becomes what
     * [unlock] goes back to.
     */
    fun show(key: LiveKey) {
        categoryMemory.placed()
        place(key)
    }

    private fun place(key: LiveKey) {
        if (lockedKey != null) previousKey = key else _selected.value = key
    }

    private val categoryMemory = LiveCategoryMemory(viewModelScope) { settings.setLastLiveCategory(it.serialize()) }

    /** The remembered category, or null while "Remember last category — Live TV" is off. */
    private suspend fun rememberedCategory(): String? =
        if (settings.rememberCategoryLive.first()) settings.lastLiveCategory.first() else null

    /** The first category list built for [c]'s playlists — not the one left from before a switch. */
    private suspend fun completeRail(c: Ctx): List<LiveRailItem> =
        railSnapshot.first { (built, _) -> built.sourceIds == c.sourceIds }.second

    /** "TV Guide follows Live TV" on: whether the TV Guide shares this screen's remembered category. */
    val guideFollowsLive: StateFlow<Boolean> = settings.guideFollowsLiveCategory
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The category the TV Guide opens on, or null to keep its own (see [LiveCategoryMemory.guideOpening]). */
    suspend fun guideOpeningKey(): LiveKey? {
        if (!settings.guideFollowsLiveCategory.first()) return null
        return LiveCategoryMemory.guideOpening(true, rememberedCategory(), completeRail(ctx.value))
    }

    init {
        // Re-read the focused programme on the minute boundary even when LiveScreen is not composed
        // (mini/audio playback keeps the HUD's guide card alive).
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                delay(60_000L - (now % 60_000L))
                epgMinuteTick.value = System.currentTimeMillis()
            }
        }
        // Bring the remembered category back at startup and after each playlist switch, as this
        // view model outlives them. Gated by "Remember last category — Live TV"
        // (Settings → Browsing & lists), on by default.
        viewModelScope.launch {
            ctx.distinctUntilChanged { a, b -> a.sourceIds == b.sourceIds }
                .collect { c ->
                    runCatching { categoryMemory.restore({ rememberedCategory() }, { completeRail(c) }, ::place) }
                        .onFailureReport()
                }
        }
        // A hidden All — at a cold start, from a remembered key, after a playlist switch or
        // when switched off in Settings — is never left selected: it moves to fallbackKey.
        viewModelScope.launch {
            runCatching { reconcileHiddenSelection(railItems, defaultRail, settings.liveBuiltIns, _selected) { lockedKey != null } }
                .onFailureReport()
        }
        // One source, so a folder can only disappear if the provider dropped it: fall back to All
        // instead of leaving the selection on a category the rail no longer lists.
        viewModelScope.launch {
            runCatching {
                val stale = _selected.value
                if (lockedKey == null && stale is LiveKey.Folder && categoryDao.getById(stale.id) == null) {
                    val next = fallbackAfterSwitch(railItems, defaultRail, stale)
                    if (_selected.value == stale) _selected.value = next
                }
            }.onFailureReport()
        }
        // Persist the last focused/interacted channel (debounced), and restore it once at startup so opening
        // Live TV lands focus back on it. Restore leaves the preview disarmed (no auto-preview on launch).
        // The restore is gated by the "Remember last item — Live TV" setting so users who want each category
        // to start at the top don't also get yanked to a saved channel on re-entry. The category restore
        // above is a separate toggle ("Remember last category — Live TV").
        viewModelScope.launch {
            // A browse behind the mini player is not a channel watched: the playing one stays saved.
            _previewChannel.drop(1).filterNotNull().map { it.id }.debounce(800).filter { miniPlaying == null }.distinctUntilChanged()
                .collect { id -> runCatching { settings.setLastLiveChannelId(id) }.onFailureReport() }
        }
        // The remembered channel is restored once at startup: it is disarmed for preview (no
        // auto-preview on launch) and left alone while the mini player plays.
        viewModelScope.launch {
            runCatching {
                if (!settings.rememberLastLive.first() || miniPlaying != null || _previewChannel.value != null) return@runCatching
                val saved = settings.lastLiveChannelId.first().takeIf { it.isNotEmpty() }
                    ?.let { channelDao.getById(it) }
                    ?.takeIf { !tv.own.owntv.core.database.ChannelMembership.allExcluded(it, customize.sealedCategoryIds(MediaType.LIVE).first()) }
                if (saved != null && _previewChannel.value == null) _previewChannel.value = saved
            }.onFailureReport()
        }
    }

    fun setSearchQuery(query: String) {
        _search.value = query
    }

    fun onChannelFocused(channel: ChannelEntity) {
        _previewArmed.value = true // a real user focus — the in-pane preview may now play
        browseTo(channel)
    }

    /** Search's "Go to channel": the list scrolls to [reveal] and focuses it, then calls [revealDone]. */
    private val _reveal = MutableStateFlow<tv.own.myowntv.features.search.Reveal?>(null)
    val reveal: StateFlow<tv.own.myowntv.features.search.Reveal?> = _reveal.asStateFlow()

    /** Open [channel] where it lives: its own category, or the landing list (All channels while it is
     *  shown) when that category is not in the rail or the channel is not in it. It becomes the
     *  preview, as if the user had walked there. With All hidden and the channel's own category hidden
     *  too, no list holds it: the list stays where it is and only the preview changes. The category is
     *  shown, not remembered: it was the channel that was chosen ([show]). */
    fun revealChannel(channel: ChannelEntity) {
        _search.value = ""
        _previewArmed.value = true
        browseTo(channel)
        viewModelScope.launch {
            val c = ctx.value
            val sort = settings.sortLive.first()
            val folder = channel.let(tv.own.owntv.core.database.ChannelMembership::primaryCat)?.let { LiveKey.Folder(it) }
            val inRail = folder != null &&
                kotlinx.coroutines.withTimeoutOrNull(2_000) { railItems.first { list -> list.any { it.key == folder } } } != null
            for (key in listOfNotNull(folder.takeIf { inRail }, fallbackKey(railItems.value)).distinct()) {
                // positionOf walks the whole source (up to tens of thousands of rows) in memory.
                val position = withContext(Dispatchers.IO) {
                    pagingSource(key, c, "", sort).positionOf(channel.id, { it.id })
                } ?: continue
                show(key)
                _reveal.value = tv.own.myowntv.features.search.Reveal(key, channel.id, position)
                return@launch
            }
        }
    }

    fun revealDone() { _reveal.value = null }

    /** The rail entry [channel] lives in: its own category, or the landing list when that category is
     *  not in the rail — never a hidden All, which would only be moved off again a moment later. */
    suspend fun railKeyOf(channel: ChannelEntity): LiveKey {
        val folder = channel.let(tv.own.owntv.core.database.ChannelMembership::primaryCat)?.let { LiveKey.Folder(it) } ?: return fallbackKey(railItems.value)
        val inRail = kotlinx.coroutines.withTimeoutOrNull(2_000) { railItems.first { list -> list.any { it.key == folder } } } != null
        return if (inRail) folder else fallbackKey(railItems.value)
    }

    // The in-pane preview only plays once the user has actually focused a channel — so restoring the last
    // focused channel on startup positions focus & the details pane WITHOUT auto-previewing on launch (#6).
    private val _previewArmed = MutableStateFlow(false)
    val previewArmed: StateFlow<Boolean> = _previewArmed.asStateFlow()


    /**
     * Live routing, the fallback ladder, the re-login link for an expired session and the preview pane's engine —
     * core's, the same code the phone runs. What stays here is the television's own: which channel is
     * on screen, the zap list, history and the HUD state.
     */
    private val live = tv.own.owntv.player.LiveTuneController(
        scope = viewModelScope,
        engine = previewEngine,
        host = tv.own.owntv.player.LiveTuneController.CoreHost(
            context = appContext,
            settings = settings,
            resolver = streamUrlResolver,
            label = ::channelNumberLabel,
            // The warm playlist map, so a preview on a focus step never waits on the database.
            sourceLookup = { id -> sourceById[id] },
        ),
    )

    // True while the in-pane preview is suppressed because the provider allows one stream at a time and
    // that stream is already in use (full-screen playback). The pane says so instead of sitting silently
    // dead — otherwise "no preview" looks like a broken channel.
    val previewBlockedSingleSession: StateFlow<Boolean> = live.previewBlockedSingleSession

    /** Critical pressure stopped every decoder; focus must not start another one for this process. */
    val previewBlockedByMemory: StateFlow<Boolean> = previewEngine.autoPreviewSuppressed

    /** In-pane preview playback (no history) — triggered by the UI after the focus settles. */
    fun playPreview(channel: ChannelEntity) {
        // Recorded even when it cannot play (a hidden folder, a one-stream panel): a second OK on it
        // must still open full screen.
        _paneChannel.value = channel
        if (channel.isPaid) return // no stream without the package; don't spend a request per focus step
        if (!tv.own.owntv.core.database.ChannelMembership.visible(channel, hiddenCategoryIds.value)) return
        // Core keeps the preview off a promoted full-screen stream, re-mutes on a re-focus instead of
        // reloading, mints a fresh play link, and holds back on a one-session panel while full screen
        // holds the stream.
        live.preview(channel, muted = !livePreviewAudio.value)
    }

    // --- Multiview: channels kept from the browse screen ------------------------------------------
    // The plan's second entry point: pick two to four channels from the Live list, then press play and
    // the grid opens already filled. Held here rather than in the shell because the context menu that
    // fills it and the player that empties it are two different screens.
    private val _multiviewSelection = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val multiviewSelection: StateFlow<List<ChannelEntity>> = _multiviewSelection.asStateFlow()

    /** Keep [channel] for the grid, up to [limit] tiles. Adding one twice does nothing. */
    fun addToMultiview(channel: ChannelEntity, limit: Int) {
        val current = _multiviewSelection.value
        if (current.any { it.id == channel.id } || current.size >= limit) return
        _multiviewSelection.value = current + channel
    }

    fun clearMultiviewSelection() {
        _multiviewSelection.value = emptyList()
    }

    /** The playlist a channel came from, so a caller can ask what it allows (Multiview's tile budget). */
    fun sourceOf(channel: ChannelEntity): SourceEntity? =
        sourceById[channel.sourceId] ?: tv.own.owntv.core.repository.BuiltInSource.current

    /**
     * Tune [channel] into a Multiview tile's own engine.
     *
     * Deliberately routed through here rather than built in the grid: which URL a channel actually
     * plays is not a property of the channel row. It is the playlist's pre-buffer override, its live
     * latency, its User-Agent, the channel's own headers and DRM, and — when the stored URL is a portal command — a `cmd` that
     * has to be resolved to a link per play. All of that already lives here, and a second copy in the
     * grid would drift the first time one of them changed.
     */
    fun tuneTile(
        engine: tv.own.owntv.player.LivePreviewEngine,
        channel: ChannelEntity,
        muted: Boolean,
        isCurrent: () -> Boolean,
    ): Job = viewModelScope.launch {
        live.playTile(engine, channel, muted, isCurrent)
    }

    // The ordered channel list the player zaps within (CH+/CH-, D-pad up/down) and shows in the
    // left-hand overlay. This is playback CONTEXT: the Favorites/History/All/folder/custom rail that
    // launched the channel, or a provider category explicitly selected in the in-player browser.
    // The channel's primary category remains metadata and is used only when a caller has no browse
    // context.
    //
    // Which list is armed, where CH± lands and which background rebuild may publish all live in
    // [LiveZapList]. What stays here is the querying: the loaders below, because the hide/rename rules
    // they apply are shared with the browse lists and belong beside them.
    private val zapList = LiveZapList(
        scope = viewModelScope,
        playingChannelId = { playingChannel?.id },
        loadForChannel = ::channelsAroundChannel,
        loadForCategory = ::channelsInCategory,
        loadWindowAround = ::buildZapList,
        categoryName = { id -> categoryDao.getById(id)?.name },
        sealedCategories = sealedCategoryIds,
    )
    val canZap: StateFlow<Boolean> = zapList.canZap
    val zapChannels: StateFlow<List<ChannelEntity>> = zapList.channels
    val zapListTitle: StateFlow<String?> = zapList.title
    val zapListKey: StateFlow<LiveKey?> = zapList.key

    // --- Category browser (second Left press shows all categories) ---
    /** The categories' search text. Here, not in the list, so it survives the player: the browse UI
     *  is removed while a channel plays full screen, and a search lost on return meant pressing Up
     *  through dozens of categories to reach the field again. */
    private val _categoryQuery = MutableStateFlow("")
    val categoryQuery: StateFlow<String> = _categoryQuery.asStateFlow()
    fun setCategoryQuery(query: String) { _categoryQuery.value = query }

    private val _showCategoryBrowser = MutableStateFlow(false)
    val showCategoryBrowser: StateFlow<Boolean> = _showCategoryBrowser.asStateFlow()

    /** Categories for the in-player category browser and the Multiview picker: the Live rail's own
     *  folders, so custom categories, renames, hides and the order all match it. */
    val browserCategories: StateFlow<List<Pair<LiveKey, String>>> = railItems
        .map { items -> items.filter { (it.key is LiveKey.Folder || it.key is LiveKey.Custom) && !it.sealed }.map { it.key to it.title.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showCategories() { _showCategoryBrowser.value = true }
    fun hideCategoryBrowser() { _showCategoryBrowser.value = false }

    // --- Full-screen channel browser (◀◀: categories beside a category's channels) ---
    private val peek = LiveCategoryPeek(viewModelScope)
    val peekChannels: StateFlow<List<ChannelEntity>> = peek.channels
    val peekKey: StateFlow<LiveKey?> = peek.key

    init {
        viewModelScope.launch { sealedCategoryIds.collect { peek.dropCategories(it) } }
    }

    /** ◀ from the in-player channel list: categories beside the list CH± zaps in, on its category. */
    fun openChannelBrowser() {
        peek.start(
            zapList.channels.value,
            zapList.key.value ?: playingChannel?.let(tv.own.owntv.core.database.ChannelMembership::primaryCat)?.let { LiveKey.Folder(it) },
        )
        _showCategoryBrowser.value = true
    }

    /** OK on a category in the channel browser: its channels on the right. Nothing tunes, CH± is untouched. */
    fun peekCategory(key: LiveKey) {
        peek.load(key) {
            when (key) {
                is LiveKey.Folder -> channelsInCategory(key.id)
                is LiveKey.Custom -> channelsInCustom(key)
                LiveKey.Favorites, LiveKey.History, LiveKey.All -> channelsInRail(key)
            }
        }
    }

    fun rememberPeekFocus(channelId: String) = peek.rememberFocus(channelId)
    fun peekEntryChannel(): String? = peek.entryChannel(playingChannel?.id)

    /** OK on a channel in the channel browser: CH± now steps through the list it was picked from, then it tunes. */
    fun watchFromPeek(channel: ChannelEntity) {
        val list = peek.channels.value
        if (list != zapList.channels.value) {
            val key = peek.key.value
            zapList.armFromBrowse(
                channels = list,
                title = railItems.value.firstOrNull { it.key == key }?.title,
                key = key,
                categoryId = (key as? LiveKey.Folder)?.id,
            )
        }
        _showCategoryBrowser.value = false
        ensurePlaying(channel)
    }

    /** Load channels for a category picked in the browser into the zap list. */
    fun loadChannelsForCategory(key: LiveKey) {
        val close = { _showCategoryBrowser.value = false }
        when (key) {
            is LiveKey.Folder -> zapList.armForCategory(key.id, close)
            is LiveKey.Custom -> {
                val title = browserCategories.value.firstOrNull { it.first == key }?.second.orEmpty()
                zapList.armForKey(key, title, { channelsInCustom(key) }, close)
            }
            LiveKey.Favorites, LiveKey.History, LiveKey.All ->
                zapList.armForKey(key, null, { channelsInRail(key) }, close)
        }
    }

    /** A built-in rail for the in-player category sheet, bounded like every zap list and with the hide /
     *  rename treatment the browsing lists get. */
    private suspend fun channelsInRail(key: LiveKey): List<ChannelEntity> {
        val c = ctx.value
        if (key == LiveKey.History) return historyChannels(ZAP_LIST_LIMIT).map { it.channel }
        val sources = c.sourceIds.ifEmpty { listOf(-1L) }
        val raw = withContext(Dispatchers.IO) {
            when (key) {
                LiveKey.Favorites -> channelDao.snapshotFavoritesManual(ContentOrderEntity.FAV_CONTEXT, ZAP_LIST_LIMIT)
                else -> channelDao.snapshotAll(ZAP_LIST_LIMIT)
            }
        }
        val cust = custom.value
        val hiddenCats = hiddenCategoryIds.value
        return raw
            .filter { CustomizeKeys.channel(it) !in cust.hiddenItems && tv.own.owntv.core.database.ChannelMembership.visible(it, hiddenCats) }
            .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    /** One custom category in its rail order, with the hide/rename treatment the Live list gives it. */
    private suspend fun channelsInCustom(key: LiveKey.Custom): List<ChannelEntity> {
        val c = ctx.value
        val cs = custResolved.value ?: return emptyList()
        return withContext(Dispatchers.IO) {
            customCategoryDao.snapshotChannels(key.id, sortMode.value == SettingsRepository.SortMode.ALPHA, ZAP_LIST_LIMIT)
        }
            .filter { isChannelVisible(it, cs.cust, cs.hiddenCats, key) }
            .map { ch -> cs.cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    /** One provider category, in its manual order — the in-player category browser's pick. */
    private suspend fun channelsInCategory(categoryId: Long): List<ChannelEntity> {
        if (customize.sealsCategory(categoryId)) return emptyList()
        val ctxKey = folderContextKeys.value[categoryId] ?: ""
        return channelDao.snapshotByCategoryManual(categoryId, ctxKey, sortMode.value == SettingsRepository.SortMode.ALPHA, ZAP_LIST_LIMIT)
    }

    /** [channel]'s own provider category, with the same hide/rename treatment the browsing lists get,
     *  so the in-player overlay matches what the user sees everywhere else. */
    private suspend fun channelsAroundChannel(channel: ChannelEntity): List<ChannelEntity> {
        val catId = tv.own.owntv.core.database.ChannelMembership.primaryCat(channel)
        val raw = withContext(Dispatchers.IO) {
            if (catId != null) {
                channelDao.snapshotByCategoryManual(catId, folderContextKeys.value[catId] ?: "", sortMode.value == SettingsRepository.SortMode.ALPHA, ZAP_LIST_LIMIT)
            } else {
                // No category on this row (an entry imported without one) — fall back to All Channels.
                channelDao.snapshotAll(ZAP_LIST_LIMIT)
            }
        }
        val cust = custom.value
        val hiddenCats = hiddenCategoryIds.value
        return raw
            .filter { CustomizeKeys.channel(it) !in cust.hiddenItems && tv.own.owntv.core.database.ChannelMembership.visible(it, hiddenCats) }
            .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    /** The recently-watched channels and when each was watched, for the right-hand in-player
     *  history overlay. */
    suspend fun historyChannels(limit: Int = HISTORY_LIST_LIMIT): List<tv.own.owntv.core.database.dao.ChannelWithWatchedAt> {
        val cust = custom.value
        val hiddenCats = hiddenCategoryIds.value
        return channelDao.recentlyWatchedWithTimestamp(limit).first()
            .filter { (ch) -> CustomizeKeys.channel(ch) !in cust.hiddenItems && tv.own.owntv.core.database.ChannelMembership.visible(ch, hiddenCats) }
            .map { e -> cust.itemNames[CustomizeKeys.channel(e.channel)]?.let { e.copy(channel = e.channel.copy(name = it)) } ?: e }
    }

    // Apply the "Preview audio" toggle to a preview that is ALREADY playing — it used to take effect only
    // on the next tune, so turning it off left the current channel audible until focus moved. Never while
    // promoted to full-screen ([LiveTuneController.liveOnExo]): that stream is meant to be heard.
    // Declared HERE, below previewEngine: viewModelScope is Main.immediate, so this collector
    // starts inline during construction and must not touch a property initialized further down the class.
    init {
        // Every category of the previewed channel was just locked: its picture and its info pane go.
        viewModelScope.launch {
            _previewChannel
                .whileAllSealed(sealedCategoryIds) { tv.own.owntv.core.database.ChannelMembership.catsOf(it).orEmpty() }
                .collect { if (_previewChannel.value?.id == it.id) stopPreview() }
        }
        viewModelScope.launch {
            livePreviewAudio.collect { on ->
                if (!live.liveOnExo.value && previewEngine.currentUrl != null) previewEngine.setMuted(!on)
            }
        }
        // On a 2 GB TV the preview pane decodes at most 720p — a second full-size
        // decoder re-tuning on every focus step is the heaviest background cost there. Lifted the moment
        // the channel goes full-screen, so the promoted stream switches up to its full variant.
        if (tv.own.owntv.core.player.PlayerBudget.of(appContext).lowSpec) {
            viewModelScope.launch {
                live.liveOnExo.collect { fullScreen -> previewEngine.setMaxVideoHeight(if (fullScreen) null else PREVIEW_MAX_HEIGHT) }
            }
        }
    }

    /** Back out of full-screen to the Live screen: the preview pane may re-take the engine
     *  (and re-apply the preview mute) on the next focus. Keeps the stream playing (no stop) — the
     *  controller stands its full-screen watchers down so [playPreview] works again. */
    fun onFullscreenExited() {
        deferredTune.cancel()
        live.detach() // the stream stays; its watchers and the give-up alarm stand down
        // With the in-pane preview enabled, the preview pane re-takes the engine on the next
        // focus and re-applies the preview mute — so we can leave it running here. But when live preview
        // is OFF, nothing ever re-takes it, and the engine would keep decoding the (unmuted) channel's
        // audio in the background after exit. Stop it so leaving fullscreen actually silences the stream.
        if (!livePreviewEnabled.value) live.stopExo()
    }

    /** The most-recently-watched live channel (for "resume last channel"): the newest watch-history
     *  row, sealed categories excluded. Null if there is none. */
    suspend fun lastWatchedLiveChannel(): ChannelEntity? {
        // At a cold start the lock store's first read costs as much as the history query (0.3–0.6 s each on
        // the TCL with a 55k-channel playlist), and the start-up choice holds the splash on this call: read
        // it alongside the query, so the seal check below finds it already loaded.
        return kotlinx.coroutines.coroutineScope {
            launch { customize.observeHasLocks().first() }
            channelDao.recentlyWatched(1).first().firstOrNull()
        }?.takeIf { !tv.own.owntv.core.database.ChannelMembership.allExcluded(it, customize.sealedCategoryIds(MediaType.LIVE).first()) }
    }

    /**
     * Resolves a launcher deep link to a channel: provider id + remote id first, then the channel
     * name, then the row id. The channel must still belong to one of the live playlists —
     * a launcher tile pointing at a deleted playlist resolves to nothing. Callers apply the
     * Customize policy on top (hidden channel/category, sealed folder).
     */
    suspend fun resolveLauncherChannel(link: tv.own.owntv.core.launcher.LauncherDeepLink.Live): ChannelEntity? {
        val channel = when {
            !link.remoteId.isNullOrBlank() -> channelDao.findByRemote(link.remoteId)
            !link.name.isNullOrBlank() -> channelDao.findByName(link.name)
            link.itemId != null && link.itemId.isNotBlank() -> channelDao.getById(link.itemId)
            else -> null
        } ?: return null
        return channel
    }

    /** Open a channel fullscreen, preserving the browse context it was launched from. The channel's
     *  provider category is metadata (used by [previewCategoryName]); it must not replace Favorites,
     *  History, All, a custom category, or the provider folder the user is actually browsing. */
    fun watchFullscreen(channel: ChannelEntity, list: List<ChannelEntity>): Boolean {
        deferredTune.cancel()
        if (rejectSubscription(channel)) return false
        // Opened with no browse list behind it — the programme-detail "Watch" button does exactly this. Without
        // a zap list, CH+/CH− and the channel-list button are dead for the rest of the session, so rebuild
        // the channel's own category the way the Guide/Search path does.
        if (list.none { it.id == channel.id }) {
            zapList.armFor(channel)
            ensurePlaying(channel)
            return true
        }
        val key = _selected.value
        zapList.armFromBrowse(
            channels = list,
            title = railItems.value.firstOrNull { it.key == key }?.title,
            // Built-in rails (Favorites / History / All) carry no title — their labels are UI strings.
            // Hand the key out so the overlay can name them properly instead of "All channels".
            key = key,
            categoryId = (key as? LiveKey.Folder)?.id,
        )
        ensurePlaying(channel)
        return true
    }

    /** Tune a channel picked outside the Live TV list — the Guide. Same as
     *  [ensurePlaying] except history is written straight away: this is a deliberate one-shot pick (you
     *  chose a channel, or "Watch channel" in a programme dialog), not the zap-through-a-category flow
     *  the history debounce exists to filter, and such a channel was not reliably landing in History.
     *
     *  Every live entry point funnels through here into [playChannel], so Prefer HLS, the
     *  fallback ladder, learned stream quirks, the per-playlist pre-buffer and the
     *  external-player toggle apply however the channel was found. */
    fun watchFromGuide(channel: ChannelEntity) {
        zapList.armFor(channel)
        ensurePlaying(channel)
        recordLiveHistory(channel, immediate = true)
    }

    /** Zap to the neighbouring channel ([delta] = +1 down / -1 up) — see [LiveZapList.next] for how
     *  the target is chosen. [ensurePlaying], reached through [zapTo], cancels any pending rebuild. */
    fun zap(delta: Int) {
        zapList.next(delta)?.let { zapTo(it) }
    }

    /** When the last CH+/- press moved the channel (elapsedRealtime); null before the first. */
    private var lastZapPressMs: Long? = null

    /**
     * CH+/- step: show the channel at once, open the stream once the user stops moving.
     *
     * Holding the channel key steps roughly every 170 ms, and every step used to open a stream —
     * thirteen opens in 3.6 s on a real remote. Providers answer that as abuse: a one-session panel
     * locks the account for ~2 minutes (HTTP 458, every channel refused until it clears), and a
     * portal API returns 429 for the duration of the burst. Neither is a playback problem, and no
     * fallback ladder can help, because the stream the user actually wants is refused too.
     *
     * So the *decision* stays instant — the channel changes on screen, history and the zap anchor move,
     * and the next press steps from here — while the network work waits [ZAP_TUNE_DELAY_MS] and is
     * cancelled by the next press. Ten channels passed at speed cost one stream open instead of ten.
     *
     * Deliberately NOT applied to deliberate picks ([ensurePlaying] from the channel list, Guide, Home
     * or a deep link): those are a single considered choice and must open immediately.
     *
     * An isolated press is one of those too: with no zap key for [ZAP_ISOLATED_PRESS_MS] it opens at once,
     * on single-connection panels as well (see [zapTuneDelayMs]). Only presses that follow it closely wait, so a held key or a burst still
     * costs at most two opens — the first step and the channel the user stops on.
     */
    private val deferredTune = DeferredLiveTune(viewModelScope)

    private fun zapTo(channel: ChannelEntity) {
        tuned(channel)
        val now = android.os.SystemClock.elapsedRealtime()
        val delayMs = zapTuneDelayMs(lastZapPressMs, now)
        // Logged only: single-session hosts follow the same rule (see ZAP_ISOLATED_PRESS_MS).
        val singleConnection = tv.own.owntv.player.LiveStreamQuirks.isSingleSession(channel.streamUrl)
        tv.own.owntv.player.LiveDiagnosticsLog.event(
            "zap: tune wait ${delayMs}ms — ${zapWaitReason(lastZapPressMs, now, singleConnection)}",
        )
        lastZapPressMs = now
        // Diagnostics only: the tune this press causes claims it and times its first frame from here.
        tv.own.owntv.player.ZapTiming.shared.markPress(now)
        val generation = deferredTune.cancel()
        if (delayMs <= 0L) {
            startTune(channel)
            return
        }
        deferredTune.launch(generation) {
            delay(delayMs)
            // The token is still current, so this is the final press. Hand over through the same
            // superseding path as every deliberate tune.
            if (!deferredTune.finish(generation)) return@launch
            startTune(channel)
        }
    }

    /**
     * Direct-tune: resolve a provider channel number to a channel and tune it.
     *
     * Single source, single lookup: the channel list is queried by number, duplicate numbers are
     * resolved via the zap-context tiebreaker, and hidden/sealed channels are excluded. (The app has
     * one built-in provider, so the old "fall back to another source" stage no longer applies.)
     *
     * What happens after resolution — immediate playback, the guarded background rebuild of the
     * bounded window, and the CH+/- anchor that covers the gap — is [LiveZapList.directTune].
     */
    suspend fun tuneByNumber(number: Int): tv.own.myowntv.player.DirectTuneResult {
        // Numeric entry is a deliberate navigation request: a CH+/- delay still waiting must not
        // replace the number the user is resolving.
        deferredTune.cancel()
        try {
            val currentChannel = playingChannel ?: return tv.own.myowntv.player.DirectTuneResult.NotFound(number)

            // Snapshot the zap list at lookup START so the resolver's zap-context tiebreaker is
            // stable for the duration of the IO query. After the lookup completes and context
            // validity is verified we re-read zapList: a previous background rebuild may have
            // published during the IO window, and the new tune must use the freshest view of the
            // list for anchor selection and the "already present, skip rebuild" check. Using the
            // stale snapshot there would lose the fallback anchor or incorrectly skip rebuilding.
            val snapshotZapList = zapList.channels.value

            // DB queries on IO; playback on Main (ExoPlayer requires the main thread).
            val resolved = withContext(Dispatchers.IO) {
                val activeHiddenCats = hiddenCategoryIds.value
                val currentCustom = custom.value
                val candidates = channelDao.findByNumber(number)
                    .filter { isChannelVisible(it, currentCustom, activeHiddenCats) }

                if (candidates.isEmpty()) return@withContext ChannelNumberLookupResult.NotFound

                val resolvedId = tv.own.myowntv.player.resolveDirectTuneCandidate(
                    candidates.map { it.id },
                    snapshotZapList.map { it.id }.toSet(),
                )
                val channel = resolvedId?.let { id -> candidates.first { it.id == id } }
                    ?: return@withContext ChannelNumberLookupResult.Ambiguous(candidates.size)
                val customName = currentCustom.itemNames[CustomizeKeys.channel(channel)]
                ChannelNumberLookupResult.Found(customName?.let { channel.copy(name = it) } ?: channel)
            }

            // Dispatch the lookup outcome.
            val tuned = when (val lookup = resolved) {
                is ChannelNumberLookupResult.Found -> lookup.channel
                is ChannelNumberLookupResult.Ambiguous ->
                    return tv.own.myowntv.player.DirectTuneResult.Ambiguous(number, lookup.matchCount)
                ChannelNumberLookupResult.NotFound ->
                    return tv.own.myowntv.player.DirectTuneResult.NotFound(number)
            }

            // Verify context hasn't changed during lookup. If the playing channel or active source
            // set moved, the resolved channel is stale — return Cancelled and do nothing.
            if (_previewChannel.value?.id != currentChannel.id) {
                return tv.own.myowntv.player.DirectTuneResult.Cancelled
            }

            // If the tuned channel is already playing, skip playback + rebuild to avoid a stream
            // restart. Still return Found so the HUD shows normal success feedback.
            if (tuned.id == currentChannel.id) {
                return tv.own.myowntv.player.DirectTuneResult.Found(
                    tv.own.myowntv.player.DirectTuneChannelInfo(
                        tuned.number, tuned.name, tuned.logoUrl, restarted = false,
                    ),
                )
            }

            // Playback starts IMMEDIATELY — the rebuild that follows it is the zap list's business.
            zapList.directTune(currentChannel.id, tuned) { live.launch { playChannel(it) }.join() }

            return tv.own.myowntv.player.DirectTuneResult.Found(
                tv.own.myowntv.player.DirectTuneChannelInfo(
                    tuned.number, tuned.name, tuned.logoUrl, restarted = true,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "tuneByNumber($number) failed", e)
            return tv.own.myowntv.player.DirectTuneResult.Failed(number)
        }
    }

    /** Leaf outcome of the IO database lookup inside [tuneByNumber]. */
    private sealed interface ChannelNumberLookupResult {
        data class Found(val channel: ChannelEntity) : ChannelNumberLookupResult
        data class Ambiguous(val matchCount: Int) : ChannelNumberLookupResult
        data object NotFound : ChannelNumberLookupResult
    }

    /** The bounded provider-order window around a channel, so CH+/- and the channel-list overlay work
     *  after a numeric tune jumped outside the original list (half before, half after), with
     *  hidden-channel/category filtering and custom names applied. Pure builder: it mutates nothing,
     *  so [LiveZapList] can discard a stale or cancelled result. */
    private suspend fun buildZapList(channel: ChannelEntity): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val cust = custom.value
        val hiddenCats = hiddenCategoryIds.value
        val half = ZAP_WINDOW_HALF
        val afterRaw: List<ChannelEntity>
        val beforeRaw: List<ChannelEntity>
        val categoryId = tv.own.owntv.core.database.ChannelMembership.primaryCat(channel)
        if (categoryId != null) {
            afterRaw = channelDao.channelsAfterCategory(categoryId, channel.sortOrder, channel.id, half)
            beforeRaw = channelDao.channelsBeforeCategory(categoryId, channel.sortOrder, channel.id, half)
        } else {
            afterRaw = channelDao.channelsAfterSource(channel.sortOrder, channel.id, half)
            beforeRaw = channelDao.channelsBeforeSource(channel.sortOrder, channel.id, half)
        }
        // beforeRaw is in reverse order; combine: before(reversed) + tuned + after
        val raw = beforeRaw.asReversed() + channel + afterRaw
        raw
            .filter { isChannelVisible(it, cust, hiddenCats) }
            .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    /** "External player" is on for Live TV — the screen must NOT open the fullscreen in-app player
     *  (mounting it spins up an engine even though the channel went to the external app). */
    val externalPlayerOn: StateFlow<Boolean> = settings.externalPlayerLive
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Hand [channel] to an external app (VLC, MX Player). Used by the Live TV long-press menu, and by
     *  [playChannel] when Live TV's external-player default is on. Some channels store a portal
     *  cmd rather than a URL, so it's resolved first — an external app can't mint one. */
    fun playExternal(channel: ChannelEntity) {
        deferredTune.cancel()
        if (rejectSubscription(channel)) return
        viewModelScope.launch {
            if (tv.own.owntv.core.database.ChannelMembership.allExcluded(channel, customize.sealedCategoryIds(MediaType.LIVE).first())) return@launch
            val source = tv.own.owntv.core.repository.BuiltInSource.current
            val url = if (streamUrlResolver.needsResolve(source)) {
                withContext(Dispatchers.IO) {
                    runCatching { streamUrlResolver.resolve(source, channel.streamUrl) }
                        .onFailure { Log.w(TAG, "play-link resolve failed channelId=${channel.id}", it) }
                        .getOrNull()
                } ?: return@launch
            } else {
                channel.streamUrl
            }
            externalPlayerLauncher.launch(
                url = url,
                title = channel.name,
                userAgent = source.userAgent,
                httpHeaders = SourceOverrides.headersWithReferer(channel.httpHeaders, source),
            )
            recordLiveHistory(channel, immediate = true)
        }
    }

    /** Go full-screen on [channel]. Cancels any pending direct-tune zap rebuild first, so normal
     *  navigation always wins over an in-flight rebuild. The live engine is ExoPlayer: promote the
     *  running preview if it's already this channel, else (re)start playback on it. A tune is never
     *  dropped just because the preview is still loading (clicking OK before the preview is ready
     *  used to hand the channel to another player and stick on a black screen for HLS). */
    fun ensurePlaying(channel: ChannelEntity) {
        // Even a rejected deliberate pick supersedes a deferred CH+/- tune.
        deferredTune.cancel()
        if (rejectSubscription(channel)) return
        // A deliberate pick, not a CH+/- step: an unclaimed zap press must not be timed against it.
        tv.own.owntv.player.ZapTiming.shared.clear()
        startTune(channel)
    }

    /**
     * A RuTube subscription channel cannot play without the package: `play/options` answers 244
     * `common_subscription`, and the tune would otherwise sit on a black screen until the 30 s
     * "no picture" watchdog fires. Say so at once and do not touch the engine.
     */
    private fun rejectSubscription(channel: ChannelEntity): Boolean {
        if (!channel.isPaid) return false
        android.widget.Toast.makeText(
            appContext,
            appContext.getString(tv.own.myowntv.R.string.live_subscription_required, channel.name),
            android.widget.Toast.LENGTH_LONG,
        ).show()
        return true
    }

    /** [ensurePlaying] without forgetting the zap press: the tune a CH+/- step opens, timed from that press. */
    private fun startTune(channel: ChannelEntity) {
        deferredTune.cancel()
        zapList.cancelPendingRebuild()
        // A deliberate pick supersedes a CH+/- step still waiting out its delay — otherwise the deferred
        // tune would land half a second later and drag the user off the channel they just chose.
        // Before the full-screen player is first drawn: a channel the engine is already previewing
        // opens on the live surface straight away, instead of waiting for the tune below to get there.
        live.expectPromotion(channel)
        // The controller's one tune job: a quicker second pick cancels the first wherever it has
        // got to, so two picks can no longer finish in the wrong order.
        live.launch { playChannel(channel) }
    }

    private suspend fun getSource(sourceId: Long): tv.own.owntv.core.database.entity.SourceEntity? =
        sourceById[sourceId] ?: tv.own.owntv.core.repository.BuiltInSource.current

    /** Internal playback: the canonical ExoPlayer / history side-effects for a
     *  channel. Direct-tune's background rebuild path calls this without cancelling the rebuild
     *  so the in-flight rebuild it owns isn't killed by its own play. */
    private suspend fun playChannel(channel: ChannelEntity) {
        if (rejectSubscription(channel)) return
        if (tv.own.owntv.core.database.ChannelMembership.allExcluded(channel, customize.sealedCategoryIds(MediaType.LIVE).first())) return
        // Live TV set to play externally: hand the channel over instead of tuning an in-app engine.
        // History is still recorded, so the channel shows up in History/Recently watched either way.
        // A protected channel stays in-app whatever this setting says: no standard intent extra
        // carries a licence URL, so the external player would open it and fail immediately.
        if (externalPlayerOn.value && channel.drmConfig == null) { playExternal(channel); return }
        tuned(channel)
        // Core routes it (DRM, a learned panel refusal, the playlist, the setting), arms the
        // ladder and starts the engine — the same code the phone runs.
        live.start(channel, getSource(channel.sourceId))
        recordLiveHistory(channel)
    }

    /**
     * Tune the channel watched before this one (the remote's "last channel" key). Re-read by id, so
     * a channel removed by a sync is skipped, and only from a playlist that is active;
     * [playChannel] applies the lock. It is a deliberate pick, so it opens at once.
     */
    /** [previousChannel]'s target when it belongs to the active playlists — the HUD button
     *  shows only while this is non-null. */
    val previousChannelTarget: StateFlow<ChannelEntity?> = combine(live.previousChannel, ctx) { p, c ->
        p?.takeIf { it.sourceId in c.sourceIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousChannel() {
        deferredTune.cancel()
        val previous = live.previousChannel.value ?: return
        viewModelScope.launch {
            val channel = withContext(Dispatchers.IO) { channelDao.getById(previous.id) } ?: return@launch
            if (channel.sourceId !in ctx.value.sourceIds) return@launch
            zapList.armFor(channel)
            ensurePlaying(custom.value.itemNames[CustomizeKeys.channel(channel)]?.let { channel.copy(name = it) } ?: channel)
        }
    }

    /** Open a channel from a caller-owned rail (for example Home favorites), preserving that rail as the
     *  playback browse/zap context instead of replacing it with provider-category metadata. A channel in
     *  a locked category, or one that is gone, does not open: false. */
    suspend fun ensurePlayingByIdAsync(channelId: String, zapChannels: List<ChannelEntity> = emptyList()): Boolean {
        deferredTune.cancel()
        val channel = channelDao.getById(channelId)
            ?.takeUnless { tv.own.owntv.core.database.ChannelMembership.allExcluded(it, customize.sealedCategoryIds(MediaType.LIVE).first()) }
            ?: return false
        if (rejectSubscription(channel)) return false
        if (zapChannels.isEmpty()) {
            // A single Home "continue watching" tile has no browse rail of its own.
            zapList.armFor(channel)
        } else {
            zapList.armFromBrowse(zapChannels, title = null, key = null, categoryId = null)
        }
        ensurePlaying(channel)
        return true
    }

    private var historyJob: Job? = null

    /**
     * Record [channel] in the watch history.
     *
     * Normally deferred by [HISTORY_DEBOUNCE_MS] and cancelled by the next tune, so zapping through a
     * category doesn't fill History with channels the user only passed over. [immediate] skips that
     * wait for entry points where the tune is unambiguously deliberate and no zapping follows — the
     * Guide's "Watch channel", and handing a channel to an external player (where the user leaves the
     * app immediately and the delayed write would have nothing to protect against anyway).
     */
    private fun recordLiveHistory(channel: ChannelEntity, immediate: Boolean = false) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            if (!immediate) delay(HISTORY_DEBOUNCE_MS)
            Log.d(TAG, "ensurePlaying history channelId=${channel.id}")
            runCatching {
                historyDao.record(WatchHistoryEntity(itemId = channel.id))
            }.onFailure { t ->
                Log.w(TAG, "ensurePlaying history record failed channelId=${channel.id}", t)
            }
        }
    }

    /** Jump back to the live edge: retune the channel on screen at real time. */
    fun goToLive() {
        _previewChannel.value?.let { ensurePlaying(it) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            if (favoriteIds.value.contains(channel.id)) {
                userDataWriter.removeFavorite(channel.id)
            } else {
                favoriteDao.add(FavoriteEntity(itemId = channel.id))
            }
        }
    }

    fun stopPreview() {
        deferredTune.cancel()
        live.stop() // both engines, every watcher and the give-up alarm, and the reconnect link
        _previewChannel.value = null
        _paneChannel.value = null
    }

    /**
     * The programme currently airing on each of [channels] (channel id → title), looked up in ONE batch
     * against the stored bulk guide — same query the Home "On Now" rail uses. This powers the small
     * "current programme" subtitle under each channel row in the Live list and the in-player channel
     * overlay. Only the stored guide is consulted (no per-channel short-EPG API calls): a channel with no
     * guide simply has no entry here, and the row shows no second line. Returns only channels that
     * actually have something airing right now.
     */
    suspend fun nowPlayingFor(channels: List<ChannelEntity>): Map<String, tv.own.owntv.core.parser.XtEpgEntry> =
        epgReader.nowProgrammesFor(channels, custom.value, epgOffset.value)

    /**
     * Current programme title per channel id, shared by the Live list and the in-player overlays.
     *
     * Bounded and least-recently-used: any screen may add to it, and none of them prunes on another's
     * behalf. An earlier version had each caller drop everything outside its own list, which made the
     * Live list and the channel-list overlay evict each other's work every time one opened.
     */
    /**
     * Channel id → the programme airing now.
     *
     * Channels that were asked about and have no guide are held internally as null, so they are
     * not asked again on every append; that sentinel is filtered out here, so such a row simply has
     * no entry and draws no second line.
     */
    val nowPlaying: StateFlow<Map<String, String>> = nowPlayingCache.programmes
        .map { found -> buildMap { found.forEach { (id, p) -> if (p != null) put(id, p.title) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** [nowPlaying] with the programme's start and stop, for the Stage rows' time left and progress. */
    val nowProgrammes: StateFlow<Map<String, tv.own.owntv.core.parser.XtEpgEntry>> = nowPlayingCache.programmes
        .map { found -> buildMap { found.forEach { (id, p) -> if (p != null) put(id, p) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Forget every resolved title — the guide data or the offset moved underneath them. */
    private fun clearNowPlaying() {
        nowPlayingCache.clear()
    }

    /**
     * Fill in "now playing" for [loaded], querying **only what is missing**.
     *
     * The Live screen used to key a producer on the whole paging snapshot, so every appended page
     * re-asked for every channel already on screen, and a 60-second timer then repeated that for the
     * full set. Scrolled deep into a large category that is a dozen chunked queries a minute to learn
     * almost nothing new. The phone has always done it this way; the television is adopting it.
     */
    fun ensureNowPlaying(loaded: List<ChannelEntity>) {
        if (loaded.isEmpty()) return
        val byId = loaded.associateBy { it.id }
        nowPlayingCache.load(byId.keys.toList(), force = false) { missing ->
            nowPlayingFor(missing.map { byId.getValue(it) })
        }
    }

    /**
     * Re-read [loaded] because the programmes have turned over.
     *
     * Called on the **minute boundary** by the screen, not 60 seconds after it opened: a programme
     * changes on the minute, so a timer started from mount shows rows changing up to 59 seconds late
     * and at different instants from one another.
     */
    fun refreshNowPlaying(loaded: List<ChannelEntity>) {
        if (loaded.isEmpty()) return
        val byId = loaded.associateBy { it.id }
        nowPlayingCache.load(byId.keys.toList(), force = true) { missing ->
            nowPlayingFor(missing.map { byId.getValue(it) })
        }
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, ChannelEntity> =
        livePagingSource(
            key = key,
            query = query,
            sort = sort,
            channelDao = channelDao,
            customCategoryDao = customCategoryDao,
            contextKey = { folderContextKeys.value[it] },
            hasManualOrder = { it in orderedContexts.value },
        )

    fun enterMoveMode(channel: ChannelEntity, key: LiveKey) {
        viewModelScope.launch {
            val contextKey = when (key) {
                is LiveKey.Folder -> folderContextKeys.value[key.id] ?: return@launch
                is LiveKey.Custom -> key.id
                LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                else -> return@launch
            }
            val alpha = sortMode.value == SettingsRepository.SortMode.ALPHA
            val items = when (key) {
                is LiveKey.Folder -> channelDao.snapshotByCategoryManual(key.id, contextKey, alpha, 5000)
                is LiveKey.Custom -> customCategoryDao.snapshotChannels(key.id, alpha, 5000)
                LiveKey.Favorites -> channelDao.snapshotFavoritesManual(contextKey, 5000)
                // History/All are computed views: no stored order to move within.
                LiveKey.History, LiveKey.All -> return@launch
            }
            val idx = items.indexOfFirst { it.id == channel.id }
            if (idx < 0) return@launch
            _moveState.value = ChannelMoveState(items, idx, contextKey)
        }
    }

    /** Takes the item being moved out and puts it back at [target]; the rest close up around it. */
    /** Takes the item at [from] out and puts it back at [to]; the rest close up around it. */
    fun move(from: Int, to: Int) {
        val s = _moveState.value ?: return
        if (from !in s.items.indices || to !in s.items.indices || from == to) return
        val list = s.items.toMutableList().apply { add(to, removeAt(from)) }
        _moveState.value = s.copy(items = list, activeIndex = to, carried = s.carried + s.items[from].id)
    }

    fun commitMove() {
        val s = _moveState.value ?: return
        _moveState.value = null
        viewModelScope.launch {
            contentOrderDao.saveMove(s.contextKey, s.items.map { it.id }, s.carried)
        }
    }

    fun cancelMove() {
        _moveState.value = null
    }

    /** Take [channel] out of the custom category [key] only. If it had been moved out of its provider
     *  folder into here, it goes back there — the same as deleting the whole category does. */
    fun removeFromCustomCategory(channel: ChannelEntity, key: LiveKey.Custom) {
        viewModelScope.launch {
            userDataWriter.removeCustomCategoryMember(key.id, channel.id)
            val itemKey = CustomizeKeys.channel(channel)
            custom.value.movedFromOrigin[itemKey]?.let { origin ->
                customize.setItemMovedFromOrigin(MediaType.LIVE, itemKey, origin, moved = false)
            }
        }
    }

    fun removeFromHistory(channelId: String) {
        viewModelScope.launch {
            userDataWriter.removeHistory(channelId)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>, sealedCats: Set<Long>): Flow<Int> =
        liveCountFlow(key, hiddenCats, channelDao, customCategoryDao, sealedCats)

    internal companion object {
        /** How long a channel must stay tuned before it counts as watched — see [recordLiveHistory]. */
        const val HISTORY_DEBOUNCE_MS = 5_000L
        /** The preview pane's video ceiling on a 2 GB TV. */
        const val PREVIEW_MAX_HEIGHT = 720
        const val TAG = "OwnTVHome"
        // In-player channel lists: one category is normally far smaller, but cap the uncategorized
        // → All Channels fallback so a huge playlist can't be pulled into memory on every tune.
        const val ZAP_LIST_LIMIT = 2_000

        // The CH+/- timing constants (ZAP_TUNE_DELAY_MS, ZAP_ISOLATED_PRESS_MS) live in ZapTuneDelay.kt.
        const val HISTORY_LIST_LIMIT = 30
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, icon = MyOwnTVIcon.FAVORITE),
            LiveRailItem(LiveKey.History, icon = MyOwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All),
        )

        /** Where Live TV lands when nothing else is chosen — core's rule, shared with the phone. */
        fun fallbackKey(rail: List<LiveRailItem>): LiveKey = liveLandingKey(rail, { it.key }, { it.sealed })
        const val ZAP_WINDOW_HALF = 50 // channels loaded on each side of the tuned channel for CH+/-

        /** Resolved "now playing" titles kept at once. Big enough for a large category plus the
         *  overlays that share the map, small enough that it cannot grow with the catalogue. */
        const val MAX_NOW_PLAYING = 2_000

    }
}
