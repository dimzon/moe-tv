package tv.own.owntv.core.tv

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.own.myowntv.R
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.railCategories
import tv.own.owntv.core.database.ManualOrdering
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.TvProviderProgramDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.TvProviderProgramEntity
import tv.own.owntv.core.i18n.AppLocale
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.isChannelVisible
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Mirrors live channels into the Android TV launcher.
 *
 * Two kinds of rows are published, both through `androidx.tvprovider`:
 * - one row of cards the user explicitly pinned from the channel menu ([TvProviderSurface.PINNED_LIVE]);
 * - one row per TV category ([TvProviderSurface.CATEGORY_ROW]) holding a card for every channel in that
 *   category ([TvProviderSurface.CATEGORY_PROGRAM]) — the same shape SmartRuTube uses for its categories.
 *
 * Every card's intent is a `moetv://play/live` deep link, so a launcher click opens the app straight into
 * that channel. The local [TvProviderProgramDao] table is both the pin list and the bookkeeping of the
 * platform ids, so a row can be refreshed or removed later. The publisher is a leaf: every platform
 * call is guarded with [runCatching], and a TV without a writable launcher provider simply shows no
 * cards. `androidx.tvprovider` is minSdk 21 and its `requestChannelBrowsable` no-ops below API 26.
 */
// The tvprovider builders' `Builder` supertypes are marked @RestrictTo even though they are the
// documented public API for launcher rows. Same suppression the pre-live-only publisher carried.
@SuppressLint("RestrictedApi")
class TvHomePublisher(
    private val context: Context,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val contentOrderDao: tv.own.owntv.core.database.dao.ContentOrderDao,
    private val customCategoryDao: tv.own.owntv.core.database.dao.CustomCategoryDao,
    private val providerDao: TvProviderProgramDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val localeStore: LocaleStore,
) {
    private val channelHelper = PreviewChannelHelper(context)
    private val resolver get() = context.contentResolver
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pinnedIds = MutableStateFlow<Set<String>>(emptySet())
    /** Remote ids of the channels currently pinned, for the long-press menu label. */
    val pinnedIds: StateFlow<Set<String>> = _pinnedIds.asStateFlow()

    init {
        scope.launch { runCatching { reload() } }
    }

    companion object {
        private const val TAG = "TvHomePublisher"
        /** groupId of the bookkeeping row for the app-owned pinned row. */
        private const val CHANNEL_GROUP_ID = 0L
        private const val CHANNEL_STABLE_KEY = "pinned-live"
        private const val MAX_PINNED = 30
    }

    private suspend fun enabled(): Boolean =
        CoreBuildInfo.tvHome && settings.androidTvHomeEnabled.first()

    private fun logD(message: String) { Log.d(TAG, message) }
    private fun logW(message: String, tr: Throwable? = null) {
        if (tr == null) Log.w(TAG, message) else Log.w(TAG, message, tr)
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        _pinnedIds.value = providerDao.getForSurface(TvProviderSurface.PINNED_LIVE)
            .filter { it.groupId != CHANNEL_GROUP_ID }
            .mapTo(LinkedHashSet()) { it.targetItemId }
    }

    suspend fun isPinned(remoteId: String): Boolean =
        providerDao.find(TvProviderSurface.PINNED_LIVE, stableGroupId(remoteId)) != null

    /** Pin or unpin [channel]; a pin also asks the launcher to make the row browsable once. */
    suspend fun toggle(channel: ChannelEntity) = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext
        val groupId = stableGroupId(channel.id)
        mutex.withLock {
            val existing = providerDao.find(TvProviderSurface.PINNED_LIVE, groupId)
            if (existing == null) {
                val current = providerDao.getForSurface(TvProviderSurface.PINNED_LIVE)
                    .filter { it.groupId != CHANNEL_GROUP_ID }
                if (current.size < MAX_PINNED) {
                    providerDao.upsert(
                        TvProviderProgramEntity(
                            surface = TvProviderSurface.PINNED_LIVE,
                            groupId = groupId,
                            targetItemId = channel.id,
                        ),
                    )
                    syncLocked(requestBrowsable = true)
                } else {
                    logW("toggle pin skipped: already at $MAX_PINNED channels")
                }
            } else {
                deletePlatformRow(existing)
                providerDao.delete(TvProviderSurface.PINNED_LIVE, groupId)
                syncLocked(requestBrowsable = false)
            }
        }
        reload()
    }

    /** Reconcile all launcher rows with the pin list, categories, custom names and the catalog. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext
        mutex.withLock { syncLocked(requestBrowsable = false) }
        reload()
    }

    /** Remove every card and app-owned row (Settings → Android TV home off). */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            listOf(
                TvProviderSurface.PINNED_LIVE,
                TvProviderSurface.CATEGORY_ROW,
                TvProviderSurface.CATEGORY_PROGRAM,
            ).forEach { surface ->
                providerDao.getForSurface(surface).forEach { row ->
                    deletePlatformRow(row)
                    providerDao.delete(surface, row.groupId)
                }
            }
        }
        reload()
    }

    /**
     * Everything the launcher rows must mirror from the browse lists, resolved once per sync: the
     * sort chip (A–Z / playlist), the visible-once customizations (locks folded in), the resolved
     * hidden-category ids, and the folder contexts that carry a manual order.
     */
    private data class ListState(
        val alpha: Boolean,
        val cust: tv.own.owntv.core.customize.SectionCustomizations,
        val hiddenCats: Set<Long>,
        val manualContexts: Set<String>,
    )

    private suspend fun listState(): ListState {
        val cust = customize.observeVisible(MediaType.LIVE).first()
        return ListState(
            alpha = settings.sortLive.first() == SettingsRepository.SortMode.ALPHA,
            cust = cust,
            hiddenCats = tv.own.owntv.core.content.AdultCategoryClassifier
                .hiddenCategoryIds(categoryDao.observe().first(), cust.hiddenCategories),
            manualContexts = contentOrderDao.observeContextKeys().first().toSet(),
        )
    }

    private suspend fun syncLocked(requestBrowsable: Boolean) {
        val state = listState()
        syncPinnedLocked(requestBrowsable, state)
        syncCategoriesLocked(state)
    }

    // --- PINNED -------------------------------------------------------------------

    private suspend fun syncPinnedLocked(requestBrowsable: Boolean, state: ListState) {
        val rows = providerDao.getForSurface(TvProviderSurface.PINNED_LIVE)
        val existingChannelRow = rows.firstOrNull { it.groupId == CHANNEL_GROUP_ID }
        if (rows.none { it.groupId != CHANNEL_GROUP_ID }) {
            existingChannelRow?.let {
                deletePlatformRow(it)
                providerDao.delete(TvProviderSurface.PINNED_LIVE, CHANNEL_GROUP_ID)
            }
            return
        }
        val res = renderContext()
        val channelRow = ensureChannelRow(
            surface = TvProviderSurface.PINNED_LIVE,
            groupId = CHANNEL_GROUP_ID,
            existing = existingChannelRow,
            displayName = res.getString(R.string.launcher_pinned_live_title),
            description = res.getString(R.string.launcher_pinned_live_description),
            appLink = LauncherDeepLink.OpenLiveSection.toUri(),
            internalId = "owntv:pinned-live:$CHANNEL_STABLE_KEY",
            requestBrowsable = requestBrowsable,
        )
        val channelId = channelRow?.providerProgramId
        if (channelId == null) {
            logW("syncLocked: no launcher channel available")
            return
        }

        val byId = rows.filter { it.groupId != CHANNEL_GROUP_ID }
            .mapNotNull { row -> channelDao.findByRemote(row.targetItemId)?.let { row to it } }
        // The pinned row mirrors the All list: the current sort chip, and only channels that would
        // survive there (hidden channel, hidden/locked provider folder).
        val ordered = byId
            .filter { (row, channel) ->
                isChannelVisible(channel, state.cust, state.hiddenCats, LiveKey.All)
            }
            .let { list ->
                if (state.alpha) list.sortedWith(compareBy({ it.second.name.lowercase() }))
                else list.sortedWith(compareBy({ it.second.sortOrder }, { it.second.name.lowercase() }))
            }
        val desiredKeys = ordered.mapTo(HashSet()) { it.first.groupId }
        for (row in rows) {
            if (row.groupId == CHANNEL_GROUP_ID) continue
            if (row.groupId !in desiredKeys) {
                deletePlatformRow(row)
                providerDao.delete(TvProviderSurface.PINNED_LIVE, row.groupId)
            }
        }
        ordered.forEachIndexed { index, (row, channel) ->
            publishProgram(
                surface = TvProviderSurface.PINNED_LIVE,
                channelRow = channelRow,
                programGroupId = stableGroupId(channel.id),
                channel = channel,
                row = row,
                label = state.cust.displayName(channel),
                description = channel.name.takeIf { it != state.cust.displayName(channel) }.orEmpty(),
                weight = ordered.size - index,
            )
        }
        logD("syncLocked channelId=$channelId pinned=${ordered.size}")
    }

    // --- CATEGORIES (SmartRuTube-style) -------------------------------------------

    /**
     * Rows mirror the Live rail exactly: the same [railCategories] order (renames, hides, manual
     * moves, A–Z chip), custom categories included, and per row the same channel order and visibility
     * the browse list applies ([isChannelVisible], manual order, moved-out items). Sealed categories
     * are skipped: their channels are behind the PIN, and launcher cards would leak them.
     */
    private suspend fun syncCategoriesLocked(state: ListState) {
        val res = renderContext()
        val categories = runCatching { categoryDao.observe().first() }.getOrDefault(emptyList())
        val rail = runCatching { categories.railCategories(state.cust, alphabetical = state.alpha) }
            .getOrDefault(emptyList())
        val desiredRows = HashSet<Long>()
        val desiredPrograms = HashSet<Long>()

        for (entry in rail) {
            // A PIN-sealed category hides its channels from every list this session; publishing
            // them here would leak what the lock protects. Locked-but-open rows are fine.
            if (entry.sealed) continue
            val listKey: LiveKey
            val channels: List<ChannelEntity> = when {
                entry.categoryId != null -> {
                    listKey = LiveKey.Folder(entry.categoryId)
                    val contextKey = entry.key
                    val base = if (contextKey in state.manualContexts) {
                        ManualOrdering.order(channelDao.baseByCategoryManual(entry.categoryId, contextKey), state.alpha)
                    } else if (state.alpha) channelDao.channelsInCategoryAlpha(entry.categoryId)
                    else channelDao.channelsInCategory(entry.categoryId)
                    base.filter { ch ->
                        isChannelVisible(ch, state.cust, state.hiddenCats, listKey) &&
                            (state.cust.movedFromOrigin[CustomizeKeys.channel(ch)]
                                ?.let { it != contextKey } ?: true)
                    }
                }
                entry.customId != null -> {
                    listKey = LiveKey.Custom(entry.customId)
                    ManualOrdering.order(customCategoryDao.baseChannelsManual(entry.customId), state.alpha)
                        .filter { ch -> isChannelVisible(ch, state.cust, state.hiddenCats, listKey) }
                }
                else -> continue
            }
            if (channels.isEmpty()) continue

            val rowGroup = stableGroupId("catrow:${entry.key}")
            val row = ensureChannelRow(
                surface = TvProviderSurface.CATEGORY_ROW,
                groupId = rowGroup,
                existing = providerDao.find(TvProviderSurface.CATEGORY_ROW, rowGroup),
                displayName = entry.displayName,
                description = res.getString(R.string.launcher_category_description),
                appLink = LauncherDeepLink.OpenLiveSection.toUri(),
                internalId = "owntv:category:${entry.key}",
                requestBrowsable = true,
            ) ?: continue
            desiredRows.add(rowGroup)

            channels.forEachIndexed { index, channel ->
                val programGroup = stableGroupId("catprog:${entry.key}:${channel.id}")
                desiredPrograms.add(programGroup)
                val programRow = providerDao.find(TvProviderSurface.CATEGORY_PROGRAM, programGroup)
                    ?: TvProviderProgramEntity(
                        surface = TvProviderSurface.CATEGORY_PROGRAM,
                        groupId = programGroup,
                        targetItemId = channel.id,
                    )
                publishProgram(
                    surface = TvProviderSurface.CATEGORY_PROGRAM,
                    channelRow = row,
                    programGroupId = programGroup,
                    channel = channel,
                    row = programRow,
                    label = state.cust.displayName(channel),
                    description = channel.name.takeIf { it != state.cust.displayName(channel) }.orEmpty(),
                    weight = channels.size - index,
                )
            }
        }

        for (row in providerDao.getForSurface(TvProviderSurface.CATEGORY_ROW)) {
            if (row.groupId !in desiredRows) {
                deletePlatformRow(row)
                providerDao.delete(TvProviderSurface.CATEGORY_ROW, row.groupId)
            }
        }
        for (row in providerDao.getForSurface(TvProviderSurface.CATEGORY_PROGRAM)) {
            if (row.groupId !in desiredPrograms) {
                deletePlatformRow(row)
                providerDao.delete(TvProviderSurface.CATEGORY_PROGRAM, row.groupId)
            }
        }
        logD("syncCategories categories=${desiredRows.size} cards=${desiredPrograms.size}")
    }

    // --- shared platform helpers ---------------------------------------------------

    private suspend fun ensureChannelRow(
        surface: TvProviderSurface,
        groupId: Long,
        existing: TvProviderProgramEntity?,
        displayName: String,
        description: String,
        appLink: Uri,
        internalId: String,
        requestBrowsable: Boolean,
    ): TvProviderProgramEntity? {
        // Built once: an already published category row is refreshed with the same values (including
        // the input id and TYPE_PREVIEW the launcher needs), not merely left as it was.
        val categoryValues = if (surface == TvProviderSurface.CATEGORY_ROW) {
            Channel.Builder()
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName(displayName)
                .setDescription(description)
                .setInputId(ComponentName(context, tv.own.myowntv.MainActivity::class.java).flattenToString())
                .setAppLinkIntentUri(appLink)
                .setInternalProviderId(internalId)
                .build()
                .toContentValues()
        } else null
        // Same idea for the pinned row: its title/description/logo must follow the current locale and
        // branding, so an existing row is updated rather than left with whatever it was first given.
        val previewChannel = if (surface != TvProviderSurface.CATEGORY_ROW) {
            PreviewChannel.Builder()
                .setDisplayName(displayName)
                .setDescription(description)
                .setAppLinkIntentUri(appLink)
                .setInternalProviderId(internalId)
                .setLogo("android.resource://${context.packageName}/${AppIconSwitcher.applied(context).banner}".toUri())
                .build()
        } else null
        if (existing?.providerProgramId != null) {
            val present = if (surface == TvProviderSurface.CATEGORY_ROW) {
                runCatching {
                    resolver.query(TvContractCompat.buildChannelUri(existing.providerProgramId), null, null, null, null)
                        ?.use { true } ?: false
                }.getOrDefault(false)
            } else {
                runCatching { channelHelper.getPreviewChannel(existing.providerProgramId) }
                    .getOrElse { logW("ensureChannel read failed id=${existing.providerProgramId}", it); null } != null
            }
            if (present) {
                if (categoryValues != null) {
                    runCatching {
                        resolver.update(TvContractCompat.buildChannelUri(existing.providerProgramId), categoryValues, null, null)
                    }
                }
                if (previewChannel != null) {
                    runCatching { channelHelper.updatePreviewChannel(existing.providerProgramId, previewChannel) }
                }
                // The launcher hides rows that are not browsable. The compat request is idempotent
                // and no-ops below API 26.
                if (requestBrowsable) requestBrowsable(existing.providerProgramId)
                return existing
            }
            logW("ensureChannel: platform row ${existing.providerProgramId} is gone")
        }

        val channelId = if (surface == TvProviderSurface.CATEGORY_ROW) {
            // SmartRuTube publishes categories as real Channels (TYPE_PREVIEW + input id), not as
            // PreviewChannels; that is the shape the TV launcher treats as a browsable row.
            runCatching {
                val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, categoryValues!!)
                if (uri == null) -1L else ContentUris.parseId(uri)
            }.getOrElse { logW("ensureChannel publish failed", it); -1L }
        } else {
            val ownCount = runCatching { channelHelper.getAllChannels().count { it.packageName == context.packageName } }
                .getOrDefault(0)
            runCatching {
                if (ownCount == 0) channelHelper.publishDefaultChannel(previewChannel!!)
                else channelHelper.publishChannel(previewChannel!!)
            }.getOrElse { logW("ensureChannel publish failed", it); -1L }
        }
        if (channelId <= 0L) return null

        val row = (existing ?: TvProviderProgramEntity(
            surface = surface,
            groupId = groupId,
            targetItemId = channelId.toString(),
        )).copy(
            surface = surface,
            groupId = groupId,
            targetItemId = channelId.toString(),
            providerProgramId = channelId,
            lastPublishedAt = System.currentTimeMillis(),
        )
        providerDao.upsert(row)
        if (requestBrowsable) requestBrowsable(channelId)
        logD("ensureChannel published id=$channelId browsableRequest=$requestBrowsable")
        return row
    }

    private suspend fun publishProgram(
        surface: TvProviderSurface,
        channelRow: TvProviderProgramEntity,
        programGroupId: Long,
        channel: ChannelEntity,
        row: TvProviderProgramEntity,
        label: String,
        description: String,
        weight: Int,
    ) {
        val program = PreviewProgram.Builder()
            .setChannelId(channelRow.providerProgramId!!)
            .setType(TvContractCompat.PreviewProgramColumns.TYPE_CHANNEL)
            .setTitle(label)
            .setDescription(description)
            .setInternalProviderId("owntv:${surface.name.lowercase()}:${hex(programGroupId)}")
            .setIntent(
                Intent(Intent.ACTION_VIEW, LauncherDeepLink.Live(remoteId = channel.id).toUri())
                    .setPackage(context.packageName),
            )
            .setWeight(weight)
            .apply {
                safeLogoUri(channel.logoUrl)?.let {
                    setPosterArtUri(it)
                    setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
                }
            }
            .build()

        val programId = row.providerProgramId
        if (programId != null) {
            val updated = runCatching {
                resolver.update(TvContractCompat.buildPreviewProgramUri(programId), program.toContentValues(), null, null)
            }
                .onFailure { logW("publishProgram update failed ${channel.id}", it) }
                .getOrDefault(0)
            if (updated > 0) {
                providerDao.upsert(row.copy(targetItemId = channel.id, lastPublishedAt = System.currentTimeMillis()))
                return
            }
        }
        val uri = runCatching { resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues()) }
            .getOrElse { logW("publishProgram insert failed ${channel.id}", it); null }
        if (uri == null) {
            logW("publishProgram insert returned null ${channel.id}")
            return
        }
        val newId = ContentUris.parseId(uri)
        providerDao.upsert(
            row.copy(
                groupId = programGroupId,
                targetItemId = channel.id,
                providerProgramId = newId,
                lastPublishedAt = System.currentTimeMillis(),
            ),
        )
        logD("publishProgram channel=${channel.id} providerId=$newId weight=$weight")
    }

    private suspend fun deletePlatformRow(row: TvProviderProgramEntity) {
        val id = row.providerProgramId ?: return
        runCatching {
            when {
                row.surface == TvProviderSurface.CATEGORY_ROW ->
                    resolver.delete(TvContractCompat.buildChannelUri(id), null, null)
                row.surface == TvProviderSurface.PINNED_LIVE && row.groupId == CHANNEL_GROUP_ID ->
                    channelHelper.deletePreviewChannel(id)
                else -> channelHelper.deletePreviewProgram(id)
            }
        }.onFailure { logW("deletePlatformRow failed id=$id", it) }
    }

    /** The compat library no-ops below API 26; calling it is safe on Android 6. */
    private fun requestBrowsable(channelId: Long) {
        runCatching { TvContractCompat.requestChannelBrowsable(context, channelId) }
            .onFailure { logW("requestChannelBrowsable failed", it) }
    }

    /** The launcher renders OwnTV-authored copy; resolve it with the current locale at publish time. */
    private fun renderContext(): Context = AppLocale.wrap(context, localeStore.currentTag.value)

    private fun safeLogoUri(raw: String?): Uri? {
        val value = raw?.trim().orEmpty()
        return if (value.startsWith("http://") || value.startsWith("https://")) value.toUri() else null
    }

    private fun stableGroupId(key: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(digest, 0, 8).getLong()
    }

    private fun hex(value: Long): String = java.lang.Long.toHexString(value)
}
