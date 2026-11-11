@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.myowntv.features.customize
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.customize.BulkRenameSession
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.SpanSelector
import tv.own.owntv.core.customize.moveBlock
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.placePinned
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/** One category row in the Customize screen (hidden rows stay visible here, marked, for unhiding).
 *  [categoryId] is null for a user's custom combined category — there is no provider
 *  row behind it. [providerName] is null when only one source is in scope. */
data class CustomizeCatRow(
    val key: String,
    val categoryId: Long?,
    val originalName: String,
    val displayName: String,
    val hidden: Boolean,
    val renamed: Boolean,
    val providerName: String? = null,
)

enum class CustomizeVisibilityFilter {
    ALL,
    VISIBLE,
    HIDDEN;

    fun accepts(hidden: Boolean): Boolean = when (this) {
        ALL -> true
        VISIBLE -> !hidden
        HIDDEN -> hidden
    }
}

/**
 * Drives Settings → Customize: hide / rename / reorder of the Live TV categories and the
 * unhide list for hidden channels. Movies and Series customization is gone with VOD; everything here
 * works on the channel section only. All edits live in [CustomizationStore] (DataStore), so they
 * survive re-syncs and never touch the DB schema.
 */
class CustomizeViewModel(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val customCategoryDao: CustomCategoryDao,
    private val contentOrderDao: ContentOrderDao,
    private val customize: CustomizationStore,
) : ViewModel() {

    /** Moves serialize so a fast second press snapshots the result of the first, never the stale
     *  pre-move order. */
    private val moveMutex = Mutex()

    private data class Ctx(val sources: List<tv.own.owntv.core.database.entity.SourceEntity>) {
        fun sourceIdsFor(type: MediaType): List<Long> = ActiveProfileSources(sources).sourceIdsFor(type)
    }

    // Observe the sources reactively so a catalog change refreshes the customize lists
    // immediately. Goes through activeProfileSources (like the Browse screens). Per-section Off
    // flags also hide that source's categories for the selected section.
    private val ctx: StateFlow<Ctx> = activeProfileSources(sourceDao)
        .map { aps -> Ctx(aps.sources) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(emptyList()))

    /** The category lock PIN ([pin] null when none is set); [loaded]=false while DataStore is still read. */
    data class PinLock(val loaded: Boolean = false, val pin: String? = null)

    val pinLock: StateFlow<PinLock> = settings.customizePin()
        .map { PinLock(loaded = true, pin = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PinLock())

    /** Set (or with null: remove) the category lock PIN. Removing it also drops every
     *  category lock: without the PIN nothing could open them again. */
    fun setPin(pin: String?) {
        viewModelScope.launch {
            if (pin == null) customize.clearCategoryLocks()
            settings.setCustomizePin(pin)
        }
    }

    /** The one section this screen customizes: Live TV channels. */
    private val _section = MediaType.LIVE

    private val _visibilityFilter = MutableStateFlow(CustomizeVisibilityFilter.ALL)
    val visibilityFilter: StateFlow<CustomizeVisibilityFilter> = _visibilityFilter.asStateFlow()

    fun setVisibilityFilter(filter: CustomizeVisibilityFilter) {
        _visibilityFilter.value = filter
        span.cancel()
    }

    /** The channel categories, in their customized order, including hidden ones. */
    val rows: StateFlow<List<CustomizeCatRow>> = combine(ctx, _visibilityFilter) { c, filter ->
        c to filter
    }.flatMapLatest { (c, filter) ->
            val sourceIds = c.sourceIdsFor(_section)
            combine(
                categoryDao.observe(sourceIds, _section),
                customize.observe(_section),
            ) { cats, cust ->
                // Built-in folders + the user's custom combined categories in ONE list, so
                // hide/rename/reorder apply uniformly (their keys share the CustomizeKeys
                // namespace). Custom rows carry categoryId = null.
                val entries = cust.customCategories.map { cc ->
                        CustomizeCatRow(
                            key = cc.id,
                            categoryId = null,
                            originalName = cc.name,
                            displayName = cust.categoryNames[cc.id] ?: cc.name,
                            hidden = cc.id in cust.hiddenCategories,
                            renamed = cc.id in cust.categoryNames,
                            providerName = "Custom",
                        )
                    } + cats.map { cat ->
                        val key = CustomizeKeys.category(cat)
                        CustomizeCatRow(
                            key = key,
                            categoryId = cat.id,
                            originalName = cat.name,
                            displayName = cust.categoryNames[key] ?: cat.name,
                            hidden = key in cust.hiddenCategories,
                            renamed = key in cust.categoryNames,
                            providerName = null,
                        )
                    }
                    // A moved category keeps its position; the rest stay in playlist order around it.
                    val (pinned, rest) = entries.partition { it.key in cust.categoryPositions }
                    placePinned(rest, pinned.map { it to cust.categoryPositions.getValue(it.key) })
                        .filter { filter.accepts(it.hidden) }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Span selection (shared machinery, see SpanSelector.kt) ---

    private val span = SpanSelector(
        getRows = { rows.value },
        getKey = { it.key },
        scope = viewModelScope,
    )
    val rangeAnchorKey: StateFlow<String?> = span.anchorKey
    val rangeMode: StateFlow<SpanSelector.Mode> = span.mode
    val rangeEndKey: StateFlow<String?> = span.endKey

    /** Hidden channels (key → label) so they can be unhidden from here. */
    val hiddenChannels: StateFlow<Map<String, String>> = customize.observe(_section)
        .map { it.hiddenItems }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Whether a category this provider adds on a future resync should be hidden automatically. */
    val hideNewCategories: StateFlow<Boolean> = settings.hideNewCategoriesDefault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setHideNewCategories(hidden: Boolean) {
        viewModelScope.launch { settings.setHideNewCategoriesDefault(hidden) }
    }

    // --- Sort (reuses SettingsRepository.SortMode — the same value the Live TV browse uses) ---

    /** Current sort mode for the channels list, for the Sort pill in the header strip. */
    val currentSort: StateFlow<SettingsRepository.SortMode> = settings.sortLive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.SortMode.PLAYLIST)

    fun setSort(mode: SettingsRepository.SortMode) {
        viewModelScope.launch { settings.setSortLive(mode) }
    }

    // --- Items screen navigation ---

    private val _selectedCategory = MutableStateFlow<CustomizeCatRow?>(null)
    val selectedCategory: StateFlow<CustomizeCatRow?> = _selectedCategory.asStateFlow()

    fun openItems(row: CustomizeCatRow) { _selectedCategory.value = row }
    fun closeItems() { _selectedCategory.value = null }

    /** Data the items ViewModel needs to page the selected category. [categoryId] is null for a
     *  custom combined category — the items screen then pages the membership join. */
    data class ItemsCtx(val categoryId: Long?, val mediaType: MediaType, val sourceIds: List<Long>)

    fun ctxForItems(): ItemsCtx {
        val c = ctx.value
        return ItemsCtx(
            categoryId = _selectedCategory.value?.categoryId,
            mediaType = _section,
            sourceIds = c.sourceIdsFor(_section),
        )
    }

    // --- custom combined categories ---

    /** Creates a custom category for the selected section — the "＋ New category" pill. */
    fun createCustomCategory(name: String) {
        viewModelScope.launch {
            customize.createCustomCategory(_section, name)
        }
    }

    /**
     * Deletes a custom combined category. Room rows are cleared FIRST (membership +
     * manual order inside it), then the DataStore definition — so the rail never shows a half-dead
     * category. The provider items themselves are never touched.
     */
    fun deleteCustomCategory(row: CustomizeCatRow) {
        if (!CustomizeKeys.isCustom(row.key)) return
        viewModelScope.launch {
            val type = _section
            // Capture stable member keys first so deleting a destination restores any provider
            // origins those items left when they were moved here.
            val formerMemberKeys = customCategoryDao.stableItemKeys(row.key).toSet()
            // 1) Room: drop the category's membership + its content_order rows (the category's own
            //    rail-order rows ride the same contextKey as the browse screens' reorder).
            customCategoryDao.clearContext(type, row.key)
            contentOrderDao.clearContext(type, row.key)
            // 2) DataStore: remove the definition + any hide/rename pins on it.
            customize.deleteCustomCategory(type, row.key, formerMemberKeys)
        }
    }

    /** The source [restoreSectionDefaults] will reset — the confirmation names it. With the
     *  single built-in source that is its own name; null while nothing is loaded. */
    val restorePlaylistName: StateFlow<String?> = ctx
        .map { c -> c.sources.singleOrNull()?.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Puts the playlists this section is showing (the top-right choice) back to what they provide: their hidden categories
     * and items show again, their renames and category order are cleared, items moved out of their
     * folders return and their manual item orders are dropped. Other playlists, custom categories
     * (and what is in them), Favorites and the guide matches are left alone.
     *
     * Keys are "<sourceId>:<id>", so a playlist's entries are the ones whose prefix is one of the
     * selected source ids; "custom:…" keys never match.
     */
    fun restoreSectionDefaults() {
        viewModelScope.launch {
            val type = _section
            val ids = ctx.value.sourceIdsFor(type).toSet()
            fun mine(key: String) = key.substringBefore(':').toLongOrNull() in ids
            contentOrderDao.observeContextKeys(type).first()
                .filter { it != ContentOrderEntity.FAV_CONTEXT && mine(it) }
                .forEach { contentOrderDao.clearContext(type, it) }
            customize.update(type) {
                it.copy(
                    hiddenCategories = it.hiddenCategories.filterNot(::mine).toSet(),
                    hiddenItems = it.hiddenItems.filterKeys { k -> !mine(k) },
                    categoryNames = it.categoryNames.filterKeys { k -> !mine(k) },
                    itemNames = it.itemNames.filterKeys { k -> !mine(k) },
                    categoryPositions = it.categoryPositions.filterKeys { k -> !mine(k) },
                    movedFromOrigin = it.movedFromOrigin.filterKeys { k -> !mine(k) },
                )
            }
        }
    }

    fun setCategoryHidden(row: CustomizeCatRow, hidden: Boolean) {
        viewModelScope.launch {
            customize.setCategoryHidden(_section, row.key, hidden)
        }
    }

    /** Blank name restores the provider's original. */
    fun renameCategory(row: CustomizeCatRow, name: String?) {
        viewModelScope.launch {
            customize.renameCategory(_section, row.key, name)
        }
    }

    /** Moves a category one step up/down and persists the full resulting order. */
    fun move(row: CustomizeCatRow, up: Boolean) =
        moveSingle(row, if (up) MoveKind.UP else MoveKind.DOWN)

    /** Jumps a category straight to the top or bottom of the list and persists the new order. */
    fun moveToEdge(row: CustomizeCatRow, top: Boolean) =
        moveSingle(row, if (top) MoveKind.TOP else MoveKind.BOTTOM)

    private fun moveSingle(row: CustomizeCatRow, kind: MoveKind) {
        viewModelScope.launch {
            moveMutex.withLock {
                val current = rows.value
                val index = current.indexOfFirst { it.key == row.key }
                if (index < 0) return@withLock
                val reordered = moveBlock(current, index, index, kind) ?: return@withLock
                customize.saveCategoryMove(_section, reordered.map { it.key }, setOf(row.key))
            }
        }
    }



    fun unhideChannel(key: String) {
        viewModelScope.launch {
            customize.setItemHidden(_section, key, "", false)
        }
    }

    // --- range (span) select: delegated to SpanSelector; persistence stays here. ---

    fun beginRange(row: CustomizeCatRow) = span.beginRange(row.key)
    fun beginMoveRange(row: CustomizeCatRow) = span.beginMoveRange(row.key)
    fun cancelRange() = span.cancel()
    fun keysInRange(endRow: CustomizeCatRow): List<String>? = span.keysInRange(endRow)

    fun applyRange(endRow: CustomizeCatRow, hidden: Boolean) {
        val keys = span.keysInRange(endRow) ?: return
        viewModelScope.launch {
            customize.setCategoriesHidden(_section, keys, hidden)
        }
        span.cancel()
    }

    fun moveRange(endRow: CustomizeCatRow, kind: MoveKind) {
        val anchorKey = span.anchorKey.value ?: return
        val endKey = span.endKey.value ?: endRow.key
        viewModelScope.launch {
            moveMutex.withLock {
                val current = rows.value
                val anchorIndex = current.indexOfFirst { it.key == anchorKey }
                val endIndex = current.indexOfFirst { it.key == endKey }
                if (anchorIndex < 0 || endIndex < 0) return@withLock
                span.setEndKey(endKey)
                val lo = minOf(anchorIndex, endIndex)
                val hi = maxOf(anchorIndex, endIndex)
                val reordered = moveBlock(current, lo, hi, kind) ?: return@withLock
                customize.saveCategoryMove(_section, reordered.map { it.key }, current.subList(lo, hi + 1).map { it.key })
            }
        }
    }

    val rangeSelectedKeys: StateFlow<Set<String>> = span.selectedKeys

    // --- bulk rename ---

    /**
     * One-shot bulk-rename flow for the current section's category rows. Every accepted rename lands
     * in ONE [CustomizationStore] write; restore clears only the selected keys' entries.
     */
    val bulk = BulkRenameSession(
        scope = viewModelScope,
        persist = { renames ->
            customize.applyBulkRenames(_section, renames)
        },
        restore = { keys ->
            customize.clearCategoryNames(_section, keys)
        },
        existingNames = { selectedKeys ->
            customize.observe(_section).first().categoryNames
                .filterKeys { it !in selectedKeys }
                .values.toSet() + rows.value.filter { it.key !in selectedKeys }.map { it.originalName }
        },
    )

    fun beginRenameRange(row: CustomizeCatRow) = span.beginRenameRange(row.key)

    /**
     * Ends the RENAME span at [endRow] and opens the bulk flow over the spanned rows' ORIGINAL
     * names. Returns null when no span is active — the caller then opens the single-row rename.
     */
    fun finishRenameRange(endRow: CustomizeCatRow): List<String>? {
        val keys = span.finishRenameRange(endRow.key) ?: return null
        val byKey = rows.value.associateBy { it.key }
        bulk.start(keys.mapNotNull { byKey[it]?.let { row -> row.key to row.originalName } })
        return keys
    }

    /**
     * Bulk-renames every category the list shows (the section and the visibility filter in force) —
     * the toolbar's Bulk rename / ✨ Auto cleanup, which used to exist for items only; categories had
     * just the long-press span. Works on the ORIGINAL names, like the span.
     */
    fun bulkRenameAll(autocleanup: Boolean) {
        val entries = rows.value.map { it.key to it.originalName }
        if (entries.isEmpty()) return
        bulk.start(entries)
        if (autocleanup) bulk.autoCleanup()
    }
}
