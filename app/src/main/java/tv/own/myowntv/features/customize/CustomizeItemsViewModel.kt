@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.myowntv.features.customize

import tv.own.owntv.core.util.launchGuarded
import tv.own.owntv.core.util.onFailureReport
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.asItemSnapshotListFlow
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.customize.BulkRenameSession
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.SpanSelector
import tv.own.owntv.core.customize.moveBlock
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/** One item row in the Customize items screen. */
data class CustomizeItemRow(
    val key: String,
    val itemId: String,
    val originalName: String,
    val displayName: String,
    val hidden: Boolean,
    val renamed: Boolean,
)

/**
 * Drives the category-items screen (reached from Customize by pressing OK on a category name).
 * Paged over the category's channels, with hide/show, rename and reorder support. Channels only:
 * the movie and series catalogues are gone with VOD.
 */
class CustomizeItemsViewModel(
    private val settings: SettingsRepository,
    private val channelDao: ChannelDao,
    private val contentOrderDao: ContentOrderDao,
    private val customCategoryDao: CustomCategoryDao,
    private val customize: CustomizationStore,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
) : ViewModel() {

    private data class Ctx(val sources: List<tv.own.owntv.core.database.entity.SourceEntity>) {
        fun sourceIdsFor(type: MediaType): List<Long> = ActiveProfileSources(sources).sourceIdsFor(type)
    }

    private val ctx: StateFlow<Ctx> = activeProfileSources()
        .map { aps -> Ctx(aps.sources) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(emptyList()))

    /** The category whose items are shown. Set via [open], cleared via [close]. [categoryId] is null
     *  for a user's custom combined category — its items live in the membership table. */
    data class CatInfo(
        val categoryId: Long?,
        val contextKey: String,
        val mediaType: MediaType,
    ) {
        /** True when this is a user-created combined category, keyed "custom:<uuid>". */
        val isCustom: Boolean get() = CustomizeKeys.isCustom(contextKey)
    }

    private val _catInfo = MutableStateFlow<CatInfo?>(null)
    val catInfo: StateFlow<CatInfo?> = _catInfo.asStateFlow()

    private val _visibilityFilter = MutableStateFlow(CustomizeVisibilityFilter.ALL)
    val visibilityFilter: StateFlow<CustomizeVisibilityFilter> = _visibilityFilter.asStateFlow()

    fun open(info: CatInfo) {
        span.cancel() // a stale span from a previous category must never carry over
        _visibilityFilter.value = CustomizeVisibilityFilter.ALL
        _catInfo.value = info
    }

    fun close() {
        span.cancel()
        _catInfo.value = null
    }

    /** Whether this category has manual-order rows (fast-path switch, same as LiveViewModel). */
    private val hasOrder: StateFlow<Boolean> = combine(ctx, _catInfo) { c, ci -> c to ci }
        .flatMapLatest { (c, ci) ->
            if (ci == null) flowOf(false)
            else contentOrderDao.observeContextKeys()
                .map { keys -> ci.contextKey in keys }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Customizations for the current section (drives hidden state + display names of items). */
    private val customizeForItems: StateFlow<tv.own.owntv.core.customize.SectionCustomizations> =
        combine(ctx, _catInfo) { c, ci -> c to ci }
            .flatMapLatest { (c, ci) ->
                if (ci == null) flowOf(tv.own.owntv.core.customize.SectionCustomizations())
                else customize.observe(ci.mediaType)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.customize.SectionCustomizations())

    /** Category display name (from customizations, or the provider name stored on the category). */
    val categoryName: StateFlow<String> = combine(ctx, _catInfo, customizeForItems) { c, ci, cust ->
        if (ci == null) ""
        else cust.categoryNames[ci.contextKey] ?: "" // will be set from outside (the row's displayName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Paged items in this category, with customizations applied (display names, hidden state). */
    private val allItems: Flow<PagingData<CustomizeItemRow>> = combine(_catInfo, hasOrder, customizeForItems) { ci, ordered, cust ->
        Triple(ci, ordered, cust)
        // Rebuild when the manual order changes: the Kotlin-sorted ManualOrderPagingSource has no
        // self-invalidation (Room's @Query source used to observe content_order itself).
    }.combine(channelDao.observeManualVersion()) { t, _ -> t }
    .flatMapLatest { (ci, ordered, cust) ->
        if (ci == null) flowOf(PagingData.empty())
        else {
            Pager(PagingConfig(pageSize = 60)) { pagingSource(ci.categoryId, ci, ordered) }
                .flow
                .map { pagingData ->
                    pagingData.map { entity ->
                        mapToRow(entity, cust)
                    }
                }
        }
    }.cachedIn(viewModelScope)

    val items: Flow<PagingData<CustomizeItemRow>> = combine(allItems, _visibilityFilter) { pagingData, filter ->
        pagingData.filter { filter.accepts(it.hidden) }
    }.cachedIn(viewModelScope)

    fun setVisibilityFilter(filter: CustomizeVisibilityFilter) {
        _visibilityFilter.value = filter
        span.cancel()
    }

    /** Currently loaded rows (the paged list) — the span machinery computes anchors/ends against
     *  it. The anchor and end of any span are always rows the user pressed on, hence loaded. */
    private val loadedRows: StateFlow<List<CustomizeItemRow>> = items
        .asItemSnapshotListFlow()
        .map { it.items }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Span selection (shared machinery, see SpanSelector.kt) ---

    private val span = SpanSelector(
        getRows = { loadedRows.value },
        getKey = { it.key },
        scope = viewModelScope,
    )
    val rangeAnchorKey: StateFlow<String?> = span.anchorKey
    val rangeMode: StateFlow<SpanSelector.Mode> = span.mode
    val rangeEndKey: StateFlow<String?> = span.endKey
    val rangeSelectedKeys: StateFlow<Set<String>> = span.selectedKeys

    fun beginRange(row: CustomizeItemRow) = span.beginRange(row.key)
    fun beginMoveRange(row: CustomizeItemRow) = span.beginMoveRange(row.key)
    fun cancelRange() = span.cancel()
    fun keysInRange(endRow: CustomizeItemRow): List<String>? = span.keysInRange(endRow)

    /** Hide/show every item in the span in ONE atomic customization edit. */
    fun applyRange(endRow: CustomizeItemRow, hidden: Boolean) {
        val keys = span.keysInRange(endRow) ?: return
        val ci = _catInfo.value ?: return
        viewModelScope.launchGuarded {
            val labels = loadedRows.value.filter { it.key in keys }.associate { it.key to it.originalName }
            customize.setItemsHidden(ci.mediaType, labels, hidden)
        }
        span.cancel()
    }

    // --- Moves (persist to content_order via saveMove — the browse Move-mode pattern) ---

    /** Moves serialize so a fast second press snapshots the result of the first, never the stale
     *  pre-move order. */
    private val moveMutex = Mutex()

    /** Snapshot of the whole category in current display order: (itemId, stable key) pairs.
     *  Custom categories snapshot the membership join (same rail-order
     *  query as the browse screens). Channels only. */
    private suspend fun orderedSnapshot(ci: CatInfo): List<Pair<String, String>> {
        if (ci.isCustom) {
            return customCategoryDao.snapshotChannels(ci.contextKey, false, SNAPSHOT_LIMIT)
                .map { it.id to CustomizeKeys.channel(it) }
        }
        return channelDao.snapshotByCategoryManual(ci.categoryId!!, ci.contextKey, false, SNAPSHOT_LIMIT)
            .map { it.id to CustomizeKeys.channel(it) }
    }

    /** Saves a move for this context: the moved items keep their place (the Move commit point). */
    private suspend fun runMove(ci: CatInfo, block: (List<Pair<String, String>>) -> Pair<List<String>, Set<String>>?) {
        moveMutex.withLock {
            val snapshot = orderedSnapshot(ci)
            val (reordered, carried) = block(snapshot) ?: return
            contentOrderDao.saveMove(ci.contextKey, reordered, carried)
        }
    }

    /** Moves a single item one step up/down and persists the resulting order. */
    fun move(row: CustomizeItemRow, up: Boolean) {
        val ci = _catInfo.value ?: return
        viewModelScope.launchGuarded {
            runMove(ci) { snapshot ->
                val index = snapshot.indexOfFirst { it.second == row.key }
                if (index < 0) return@runMove null
                moveBlock(snapshot, index, index, if (up) MoveKind.UP else MoveKind.DOWN)?.map { it.first }
                    ?.let { it to setOf(snapshot[index].first) }
            }
        }
    }

    /** Jumps a single item straight to the top or bottom and persists the resulting order. */
    fun moveToEdge(row: CustomizeItemRow, top: Boolean) {
        val ci = _catInfo.value ?: return
        viewModelScope.launchGuarded {
            runMove(ci) { snapshot ->
                val index = snapshot.indexOfFirst { it.second == row.key }
                if (index < 0) return@runMove null
                moveBlock(snapshot, index, index, if (top) MoveKind.TOP else MoveKind.BOTTOM)?.map { it.first }
                    ?.let { it to setOf(snapshot[index].first) }
            }
        }
    }

    /** Moves the whole span (anchor..end) as one block. Called from any row's arrows while a move
     *  span is active; the block stays selected for repeated moves. */
    fun moveRange(endRow: CustomizeItemRow, kind: MoveKind) {
        val ci = _catInfo.value ?: return
        val anchorKey = span.anchorKey.value ?: return
        val endKey = span.endKey.value ?: endRow.key
        viewModelScope.launchGuarded {
            runMove(ci) { snapshot ->
                val anchorIndex = snapshot.indexOfFirst { it.second == anchorKey }
                val endIndex = snapshot.indexOfFirst { it.second == endKey }
                if (anchorIndex < 0 || endIndex < 0) return@runMove null
                span.setEndKey(endKey)
                val lo = minOf(anchorIndex, endIndex)
                val hi = maxOf(anchorIndex, endIndex)
                moveBlock(snapshot, lo, hi, kind)?.map { it.first }
                    ?.let { it to snapshot.subList(lo, hi + 1).mapTo(HashSet()) { row -> row.first } }
            }
        }
    }

    private companion object {
        /** Same bound the browse screens' Move mode uses. */
        const val SNAPSHOT_LIMIT = 5000
    }

    /** Returns the Room PagingSource for this channel category, with or without manual-order join.
     *  Custom categories always take the membership join — it LEFT JOINs content_order itself,
     *  so [ordered] doesn't matter there. Channels only. */
    private fun pagingSource(categoryId: Long?, info: CatInfo, ordered: Boolean): PagingSource<Int, *> =
        if (info.isCustom) {
            tv.own.owntv.core.database.ManualOrderPagingSource(
                { customCategoryDao.baseChannelsManual(info.contextKey) }, alpha = false,
            )
        } else if (ordered) {
            tv.own.owntv.core.database.ManualOrderPagingSource(
                { channelDao.baseByCategoryManual(categoryId!!, info.contextKey) }, alpha = false,
            )
        } else {
            channelDao.pagingByCategory(categoryId!!)
        }

    /** Maps one channel entity to [CustomizeItemRow] with customizations applied. */
    private fun mapToRow(
        entity: Any,
        cust: tv.own.owntv.core.customize.SectionCustomizations,
    ): CustomizeItemRow {
        val ch = entity as tv.own.owntv.core.database.entity.ChannelEntity
        val key = CustomizeKeys.channel(ch)
        return CustomizeItemRow(
            key = key,
            itemId = ch.id,
            originalName = ch.name,
            displayName = cust.itemNames[key] ?: ch.name,
            hidden = key in cust.hiddenItems,
            renamed = key in cust.itemNames,
        )
    }

    // --- Mutations ---

    fun setItemHidden(row: CustomizeItemRow, hidden: Boolean) {
        val ci = _catInfo.value ?: return
        viewModelScope.launchGuarded {
            customize.setItemHidden(ci.mediaType, row.key, row.originalName, hidden)
        }
    }

    /** Rename an item (a Live TV channel). */
    fun renameItem(row: CustomizeItemRow, name: String?) {
        val ci = _catInfo.value ?: return
        viewModelScope.launchGuarded {
            customize.renameItem(ci.mediaType, row.key, name)
        }
    }

    // --- bulk rename ---

    /**
     * One-shot bulk-rename flow for the current category's items. Every accepted rename lands in ONE
     * [CustomizationStore] write; restore clears only the selected keys' entries.
     */
    val bulk = BulkRenameSession(
        scope = viewModelScope,
        persist = { renames ->
            val ci = _catInfo.value
            if (ci != null) customize.applyBulkRenames(ci.mediaType, renames)
        },
        restore = { keys ->
            val ci = _catInfo.value
            if (ci != null) customize.clearItemNames(ci.mediaType, keys)
        },
        existingNames = { selectedKeys ->
            val ci = _catInfo.value
            if (ci == null) emptySet<String>()
            else customize.observe(ci.mediaType).first().itemNames
                .filterKeys { it !in selectedKeys }
                .values.toSet() + loadedRows.value.filter { it.key !in selectedKeys }.map { it.originalName }
        },
    )

    fun beginRenameRange(row: CustomizeItemRow) = span.beginRenameRange(row.key)

    /**
     * Ends the RENAME span at [endRow] and opens the bulk flow over the spanned rows' ORIGINAL
     * names. Returns null when no span is active — the caller then opens the single-row rename.
     */
    fun finishRenameRange(endRow: CustomizeItemRow): List<String>? {
        val keys = span.finishRenameRange(endRow.key) ?: return null
        val byKey = loadedRows.value.associateBy { it.key }
        bulk.start(keys.mapNotNull { byKey[it]?.let { row -> row.key to row.originalName } })
        return keys
    }

    // --- Move to… ---

    /** The user's custom categories (with member counts) minus THIS category — the "Move to…"
     *  dialog's list. A category can't contain itself. */
    val moveTargets: StateFlow<List<MoveTarget>> = combine(ctx, _catInfo, customizeForItems) { c, ci, cust ->
        Triple(c, ci, cust)
    }.flatMapLatest { (c, ci, cust) ->
        if (ci == null || cust.customCategories.isEmpty()) flowOf(emptyList())
        else {
            val targets = cust.customCategories.map { it.id }.filterNot { it == ci.contextKey }
            if (targets.isEmpty()) flowOf(emptyList())
            else customCategoryDao.observeCountsByContexts(targets)
                .map { counts ->
                    targets.map { id ->
                        MoveTarget(
                            id = id,
                            displayName = cust.categoryNames[id] ?: cust.customCategories.first { it.id == id }.name,
                            count = counts.firstOrNull { it.contextKey == id }?.count ?: 0,
                        )
                    }
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates a custom category — the Move dialog's "＋ New category…" flow. Returns its id so
     *  the dialog can select the new target only after the write has actually landed. */
    suspend fun createCustomCategory(name: String): String? {
        val ci = _catInfo.value ?: return null
        val created = runCatching { customize.createCustomCategory(ci.mediaType, name) }
        created.onFailureReport { }
        return created.getOrNull()?.id
    }

    /**
     * Moves (or copies, [keepInOrigin]) [row] into a custom category. The origin is the
     * category this screen is showing: a provider folder marks the item in movedFromOrigin so its
     * browse pager drops it, a custom category deletes its membership row, and Favorites deletes the
     * favorite row. The item always stays in All/search/recent.
     */
    /** Take [row] out of the custom category this screen shows. If it had been moved out of its
     *  provider folder into here, it goes back there — the same as deleting the whole category does. */
    fun removeFromCategory(row: CustomizeItemRow) {
        val ci = _catInfo.value?.takeIf { it.isCustom } ?: return
        viewModelScope.launchGuarded {
            userDataWriter.removeCustomCategoryMember(ci.contextKey, row.itemId)
            customize.observe(ci.mediaType).first().movedFromOrigin[row.key]?.let { origin ->
                customize.setItemMovedFromOrigin(ci.mediaType, row.key, origin, moved = false)
            }
        }
    }

    fun moveTo(row: CustomizeItemRow, targetId: String, keepInOrigin: Boolean) {
        val ci = _catInfo.value ?: return
        if (targetId == ci.contextKey) return
        viewModelScope.launchGuarded {
            customCategoryDao.appendItem(targetId, row.itemId)
            if (!keepInOrigin) {
                when {
                    // Custom origin → drop the membership row (the item leaves THIS category).
                    // Through the writer, so the removal is recorded and a sync cannot bring it back.
                    CustomizeKeys.isCustom(ci.contextKey) ->
                        userDataWriter.removeCustomCategoryMember(ci.contextKey, row.itemId)
                    // Provider-folder origin → mark it moved-out; the browse pager then drops the
                    // item from that folder while keeping it in All/search/recent. (Favorites can't
                    // be an origin here — this screen is only opened from Customize's category rows.)
                    else -> customize.setItemMovedFromOrigin(ci.mediaType, row.key, ci.contextKey, moved = true)
                }
            }
        }
    }
}
