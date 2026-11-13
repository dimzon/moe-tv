package tv.own.owntv.core.content

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.sealsItemIn
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ChannelSearchResult
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.EpgDedupe
import tv.own.owntv.core.epg.EpgShift
import tv.own.owntv.core.live.epgKeyOf
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/** Combined results of a global query (each list bounded). Live-only. */
@Immutable
data class SearchResults(
    val channels: List<ChannelSearchResult> = emptyList(),
    /** Programmes on now or later today whose title matches, on now first. Only from a typed query. */
    val programmes: List<ProgrammeSearchResult> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && programmes.isEmpty()

    /** How many results are of [kind] — the count each filter button shows. */
    fun count(kind: SearchKind): Int = when (kind) {
        SearchKind.LIVE -> channels.size
        SearchKind.TV_SHOWS -> programmes.size
    }

    /** Only the kinds in [kinds]. None picked is no filter at all: everything. */
    fun only(kinds: Set<SearchKind>): SearchResults = if (kinds.isEmpty()) this else SearchResults(
        channels = if (SearchKind.LIVE in kinds) channels else emptyList(),
        programmes = if (SearchKind.TV_SHOWS in kinds) programmes else emptyList(),
    )
}

/** The search filter's buttons, each switched on and off on its own. TV shows are guide programmes. */
enum class SearchKind { LIVE, TV_SHOWS }

/** A programme found by its title, with the channel showing it; times are on the user's clock. */
@Immutable
data class ProgrammeSearchResult(
    val programme: EpgProgrammeEntity,
    val channel: ChannelEntity,
)

/** The curated lists offered when nothing has been typed yet. */
enum class SearchIntent {
    CONTINUE,
    CHANNELS,
}

/**
 * Searching the user's channels and guide programmes at once.
 *
 * The queries themselves are the DAOs'; what lives here is everything that decides *which* of their
 * rows the user may see — the FTS expression, the hidden items, the hidden categories, and the
 * renames a user made in Customize. Two apps searching the same database have to agree on all of
 * them, so it is written once rather than in each app's search screen.
 */
class SearchReader(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val customize: CustomizationStore,
    private val epgDao: EpgDao,
    private val settings: SettingsRepository,
) {

    /**
     * Results for a typed query, already filtered and renamed.
     *
     * [sources] is the set of active playlists, so a section switched Off never surfaces.
     * Returns nothing at all when there are no sources — there is nothing to search.
     */
    suspend fun search(
        sources: ActiveProfileSources,
        query: String,
        limit: Int = MATCH_LIMIT,
    ): SearchResults {
        if (!sources.hasAny) return SearchResults()
        val fts = ftsQuery(query)
        val custLive = customize.observeVisible(MediaType.LIVE).first()
        val hiddenLiveCats = hiddenCategoryIds(sources.liveSourceIds, MediaType.LIVE, custLive)
        return SearchResults(
            channels = if (sources.liveSourceIds.isEmpty()) emptyList() else
                (
                    if (fts != null) channelDao.searchListDetailedFts(fts, limit)
                    else channelDao.searchListDetailed(query, limit)
                    )
                    .filter {
                        CustomizeKeys.channel(it.channel) !in custLive.hiddenItems &&
                            tv.own.owntv.core.database.ChannelMembership.visible(it.channel, hiddenLiveCats)
                    }
                    .map { row ->
                        custLive.itemNames[CustomizeKeys.channel(row.channel)]
                            ?.let { row.copy(channel = row.channel.copy(name = it)) } ?: row
                    },
            programmes = if (sources.liveSourceIds.isEmpty()) emptyList() else
                programmes(query.trim(), sources.liveSourceIds, custLive, hiddenLiveCats),
        )
    }

    /**
     * Programmes whose title contains [query], from now until [PROGRAMME_HORIZON_MS] ahead, each on a
     * channel the user may see. Only the stored (XMLTV) guide is searched — the provider's own
     * short EPG is fetched per channel and is never in the table.
     *
     * A channel reads its guide by its manual match first and its own id second (as the Guide does), so
     * both are looked up. One feed carried by two playlists is one programme: it is listed once.
     */
    private suspend fun programmes(
        query: String,
        liveSourceIds: List<Long>,
        cust: SectionCustomizations,
        hiddenCats: Set<Long>,
    ): List<ProgrammeSearchResult> {
        val now = System.currentTimeMillis()
        // The stored window is widened by [PROGRAMME_SHIFT_SLACK_MS] on both sides, so a shifted channel
        // still finds its rows; the exact window is applied after the shift, on the user's clock. The
        // slack stays small: rows that ended before it would use up [PROGRAMME_ROWS] and push out what
        // is on now.
        val rows = epgDao.searchTitles(
            query,
            now - PROGRAMME_SHIFT_SLACK_MS,
            now + PROGRAMME_HORIZON_MS + PROGRAMME_SHIFT_SLACK_MS,
            PROGRAMME_ROWS,
        )
        if (rows.isEmpty()) return emptyList()
        val byKey = rows.groupBy { it.epgChannelId }
        val keys = byKey.keys.toList()
        val candidates = keys.chunked(KEY_CHUNK).flatMap { channelDao.byEpgKeys(it) }
        val globalShift = settings.epgOffsetMinutes.first()
        return candidates
            .distinctBy { it.id }
            .filter { CustomizeKeys.channel(it) !in cust.hiddenItems && tv.own.owntv.core.database.ChannelMembership.visible(it, hiddenCats) }
            .sortedWith(compareBy({ liveSourceIds.indexOf(it.sourceId) }, { it.sortOrder }))
            .flatMap { ch ->
                val key = epgKeyOf(ch) ?: return@flatMap emptyList()
                val shift = EpgShift.minutesFor(cust, ch, globalShift)
                val named = cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch
                EpgShift.apply(EpgDedupe.collapse(byKey[key].orEmpty()), shift)
                    .filter { it.stopMs > now && it.startMs < now + PROGRAMME_HORIZON_MS }
                    .map { key to ProgrammeSearchResult(it, named) }
            }
            .distinctBy { (key, found) -> Triple(key, found.programme.startMs, found.programme.title) }
            .map { it.second }
            .sortedWith(compareBy({ it.programme.startMs > now }, { it.programme.startMs }))
            .take(PROGRAMME_LIMIT)
    }

    /** One of the curated lists shown before anything is typed. Bounded, and already source-filtered. */
    suspend fun curated(
        sources: ActiveProfileSources,
        intent: SearchIntent,
        limit: Int = LIMIT,
    ): SearchResults {
        if (!sources.hasAny) return SearchResults()
        // Curated lists never dropped user-hidden categories; only a locked one's items are kept out.
        val live = customize.observeVisible(MediaType.LIVE).first()
        return when (intent) {
            SearchIntent.CONTINUE -> SearchResults(
                channels = channelDao.recentlyWatched(limit).first()
                    .filter { it.sourceId in sources.liveSourceIds && tv.own.owntv.core.database.ChannelMembership.allExcluded(it, live.sealedCategoryIds) }
                    .map { ChannelSearchResult(it, null) },
            )
            SearchIntent.CHANNELS -> SearchResults(
                channels = channelDao.favoritesListAlpha().first()
                    .filter { it.sourceId in sources.liveSourceIds && tv.own.owntv.core.database.ChannelMembership.allExcluded(it, live.sealedCategoryIds) }
                    .take(limit)
                    .map { ChannelSearchResult(it, null) },
            )
        }
    }

    /** DB ids of the user's hidden categories for [type] (so hidden groups drop out of search too). */
    private suspend fun hiddenCategoryIds(
        sourceIds: List<Long>,
        type: MediaType,
        cust: SectionCustomizations,
    ): Set<Long> {
        if (cust.hiddenCategories.isEmpty()) return emptySet()
        return AdultCategoryClassifier.hiddenCategoryIds(
            categoryDao.observe().first(),
            cust.hiddenCategories,
        )
    }

    companion object {
        /** How many rows of each kind a curated list returns. */
        const val LIMIT = 40

        /**
         * How many rows of each kind a typed search returns. High enough that a title search lists
         * every match; the cap is only for a short query that matches most of a huge catalogue.
         */
        const val MATCH_LIMIT = 500

        /** How far ahead "On TV" looks, and how many programmes it lists. */
        private const val PROGRAMME_HORIZON_MS = 12 * 60 * 60_000L
        private const val PROGRAMME_LIMIT = 20

        /** The largest guide shift "On TV" still allows for when reading stored times. */
        private const val PROGRAMME_SHIFT_SLACK_MS = 2 * 60 * 60_000L

        /** Raw rows read before mapping to channels; one programme can be on many guide channels. */
        private const val PROGRAMME_ROWS = 300

        /** Keys per IN (…) lookup, inside SQLite's variable limit. */
        private const val KEY_CHUNK = 400

        /**
         * A sanitized FTS4 MATCH expression: the text is split at every character that is not a letter
         * or digit and each word becomes a prefix term ("harry pot" → "harry* pot*", implicit AND). The
         * index splits names at punctuation too, so "Spider-M" must be "Spider* M*" — stripping the
         * hyphen instead gave "SpiderM*", which no indexed word starts with.
         * Null when nothing tokenizable remains (symbols-only input) — the caller then falls back to
         * the substring LIKE queries. Prefix terms match word starts rather than mid-word substrings,
         * which is the accepted trade-off for an index-served search over a huge catalogue per keystroke.
         */
        fun ftsQuery(query: String): String? {
            val tokens = query.split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}

/** True when there is at least one playlist a search could look in. */
private val ActiveProfileSources.hasAny: Boolean
    get() = liveSourceIds.isNotEmpty()
