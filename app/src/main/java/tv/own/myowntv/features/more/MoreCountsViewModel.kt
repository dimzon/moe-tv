package tv.own.myowntv.features.more
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.UserDataWriter
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.sealsItemIn
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.live.GuideReader
import tv.own.owntv.core.live.GuideSlot
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.local.LocalSyncManager
import tv.own.owntv.core.sync.local.PairedDevice
import tv.own.owntv.core.categorylock.lockAwareCount
import tv.own.owntv.core.categorylock.sealedCategoryIds

/** How many channels a list holds. The app is live TV only; films and shows are gone. */
data class TypeCounts(val live: Int = 0) {
    val total: Int get() = live
}

/** Whether this device is reachable for Local sync, and who it is paired with. */
data class SyncSnapshot(val listening: Boolean = false, val devices: List<PairedDevice> = emptyList())

/** More › Favourites: the starred channels in the user's own order, with what is on each one now. */
data class FavouriteLists(
    val live: List<ChannelEntity> = emptyList(),
    val onNow: Map<String, GuideSlot> = emptyMap(),
    val now: Long = 0L,
)

/** One More › History row: a channel last watched. */
data class HistoryEntry(
    val id: String,
    val title: String,
    val channel: ChannelEntity?,
    val watchedAt: Long,
)

data class HistoryLists(
    val live: List<HistoryEntry> = emptyList(),
)

/** A favourite being moved in More › Favourites: the type's whole list and where the moving one is. */
data class FavouriteMove(val type: MediaType, val ids: List<String>, val names: List<String>, val index: Int)

/**
 * More's data: the sheet's counts, and the Favourites and History pages.
 *
 * Deliberately its own view model and plain DAO reads, never a browse view model: on the TV
 * `koinViewModel()` scopes to the Activity, so borrowing another screen's would share its state.
 * Writes go through the same [UserDataWriter] and order table the browse screens use.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoreCountsViewModel(
    private val channelDao: ChannelDao,
    private val contentOrderDao: ContentOrderDao,
    private val userDataWriter: UserDataWriter,
    private val guide: GuideReader,
    private val customize: CustomizationStore,
    private val settings: SettingsRepository,
    sourceDao: SourceDao,
    localSync: LocalSyncManager,
) : ViewModel() {

    /** Local sync, read-only: the page's own view model owns hosting (see LocalSyncScreen). */
    val sync: StateFlow<SyncSnapshot> = combine(localSync.hostState, localSync.pairedDevices) { host, devices ->
        SyncSnapshot(listening = host is CompanionServerState.Listening, devices = devices)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSnapshot())

    /** The last successful backup, or `null` if none has been taken on this device. */
    val lastBackup: StateFlow<SettingsRepository.LastBackup?> = settings.lastBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** When the app last checked GitHub for a new version, or null. */
    val lastUpdateCheckAt: StateFlow<Long?> = settings.lastUpdateCheckAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(sourceDao)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveProfileSources(emptyList()))

    val favorites: StateFlow<TypeCounts> = counts(favorites = true)
    val history: StateFlow<TypeCounts> = counts(favorites = false)

    /** Bumped after a reorder, which changes no count, so the lists are read again. */
    private val reload = MutableStateFlow(0)

    /** Each type's browse view; items in a category a lock still keeps shut stay off both pages. */
    private val visible: Flow<Map<MediaType, SectionCustomizations>> =
        combine(LISTED.map { t -> customize.observeVisible(t).map { t to it } }) { it.toMap() }

    /** Rebuilt whenever the favourite counts change, so starring or removing shows at once. */
    val favouriteLists: StateFlow<FavouriteLists> = combine(ctx, favorites, reload, visible) { c, _, _, v -> c to v }
        .flatMapLatest { (c, v) -> flow { emit(loadFavourites(c, Sealed(v))) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavouriteLists())

    val historyLists: StateFlow<HistoryLists> = combine(ctx, history, visible) { c, _, v -> c to v }
        .flatMapLatest { (c, v) -> flow { emit(loadHistory(c, Sealed(v))) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryLists())

    private class Sealed(private val visible: Map<MediaType, SectionCustomizations>) {
        fun holds(type: MediaType, categoryId: Long?) = visible[type]?.sealsItemIn(categoryId) == true
    }

    private val _move = MutableStateFlow<FavouriteMove?>(null)
    val move: StateFlow<FavouriteMove?> = _move.asStateFlow()

    private fun ids(c: ActiveProfileSources, type: MediaType) = c.sourceIdsFor(type).ifEmpty { listOf(-1L) }

    /** A channel is sealed only when every one of its categories is (M:N). Null/empty membership → not sealed. */
    private fun sealedAllCats(sealed: Sealed, type: MediaType, channel: tv.own.owntv.core.database.entity.ChannelEntity): Boolean {
        val cats = tv.own.owntv.core.database.ChannelMembership.catsOf(channel) ?: return false
        return cats.isNotEmpty() && cats.all { sealed.holds(type, it) }
    }

    private suspend fun loadFavourites(c: ActiveProfileSources, sealed: Sealed): FavouriteLists {
        val key = ContentOrderEntity.FAV_CONTEXT
        val live = channelDao.snapshotFavoritesManual(key, LIST_LIMIT)
            .filterNot { sealedAllCats(sealed, MediaType.LIVE, it) }
        val now = System.currentTimeMillis()
        val onNow = if (live.isEmpty()) emptyMap() else guide.onNow(
            channels = live,
            cust = customize.observe(MediaType.LIVE).first(),
            globalShiftMinutes = settings.epgOffsetMinutes.first(),
            atMs = now,
            // A real window: an empty one (now..now) finds no programme at all.
            lookAheadMs = 60 * 60_000L,
        )
        return FavouriteLists(live = live, onNow = onNow, now = now)
    }

    private suspend fun loadHistory(c: ActiveProfileSources, sealed: Sealed): HistoryLists {
        val live = channelDao.recentlyWatchedWithTimestamp(LIST_LIMIT).first()
            .filterNot { sealedAllCats(sealed, MediaType.LIVE, it.channel) }.map {
            HistoryEntry(it.channel.id, it.channel.name, it.channel, it.watchedAt)
        }
        return HistoryLists(live)
    }

    fun removeFavourite(type: MediaType, id: String) {
        viewModelScope.launch { userDataWriter.removeFavorite(type, id) }
    }

    /** The same removal the Live TV list's "Remove from History" does. */
    fun removeHistory(entry: HistoryEntry) {
        viewModelScope.launch { userDataWriter.removeHistory(MediaType.LIVE, entry.id) }
    }

    /** Move starts on the favourite channels' whole list, in its current order. */
    fun startMove(type: MediaType, id: String) {
        val lists = favouriteLists.value
        val pairs = when (type) {
            MediaType.LIVE -> lists.live.map { it.id to it.name }
            else -> return // Favourite films and shows are gone; only channels move.
        }
        val index = pairs.indexOfFirst { it.first == id }.takeIf { it >= 0 } ?: return
        _move.value = FavouriteMove(type, pairs.map { it.first }, pairs.map { it.second }, index)
    }

    fun move(from: Int, to: Int) {
        val m = _move.value ?: return
        if (from !in m.ids.indices || to !in m.ids.indices || from == to) return
        fun <T> List<T>.moved() = toMutableList().apply { add(to, removeAt(from)) }
        _move.value = m.copy(ids = m.ids.moved(), names = m.names.moved(), index = to)
    }

    fun commitMove() {
        val m = _move.value ?: return
        _move.value = null
        val key = ContentOrderEntity.FAV_CONTEXT
        viewModelScope.launch {
            contentOrderDao.replaceContext(
                type = m.type, contextKey = key,
                rows = m.ids.mapIndexed { i, id -> ContentOrderEntity(mediaType = m.type, contextKey = key, itemId = id, position = i) },
            )
            reload.value++
        }
    }

    fun cancelMove() { _move.value = null }

    private fun counts(favorites: Boolean): StateFlow<TypeCounts> = ctx
        .flatMapLatest { c ->
            // What a locked category holds leaves the counts too, so they agree with the lists.
            fun count(type: MediaType, all: () -> Flow<Int>, excluding: (List<Long>) -> Flow<Int>) =
                lockAwareCount(customize.sealedCategoryIds(type), { all() }) { excluding(it) }
            (
                if (favorites) count(MediaType.LIVE, channelDao::countFavorites, channelDao::countFavoritesExcluding)
                else count(MediaType.LIVE, channelDao::countHistory, channelDao::countHistoryExcluding)
            ).map { TypeCounts(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TypeCounts())

    private companion object {
        /** Enough for any real list, bounded so a runaway history cannot load the whole catalogue. */
        const val LIST_LIMIT = 500

        val LISTED = listOf(MediaType.LIVE)
    }
}
