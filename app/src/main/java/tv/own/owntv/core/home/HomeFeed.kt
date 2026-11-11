package tv.own.owntv.core.home

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ChannelWithWatchedAt
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.live.GuideReader
import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository

/** The item behind the big card at the top of Home — the last thing worth carrying on with. */
sealed interface HeroItem {
    val streamUrl: String
    val seekToMs: Long
    val positionMs: Long
    val durationMs: Long
    val lastEngagementAt: Long

    /** Playlist this item came from — used to give the preview the same source-wide User-Agent the
     *  player uses. */
    val sourceId: Long

    /** This item's own `Key: Value` request headers (M3U), or null. Same reason as [sourceId]. */
    val httpHeaders: String?

    data class LiveHero(
        val channel: ChannelEntity,
        val watchedAt: Long,
    ) : HeroItem {
        override val sourceId: Long = channel.sourceId
        override val httpHeaders: String? = channel.httpHeaders
        override val streamUrl: String = channel.streamUrl
        override val seekToMs: Long = 0L
        override val positionMs: Long = 0L
        override val durationMs: Long = 0L
        override val lastEngagementAt: Long = watchedAt
    }
}

/** Identity of a hero item across reloads — what an artwork cache is keyed by. */
val HeroItem.homeKey: String
    get() = when (this) {
        is HeroItem.LiveHero -> "live:${channel.id}"
    }

/** The guide behind a live rail in "On now" mode: the channels, and their programmes in the window. */
@Immutable
data class GuideSliceState(
    val channels: List<ChannelEntity> = emptyList(),
    val programmes: Map<Long, List<EpgProgrammeEntity>> = emptyMap(),
    val windowStart: Long = 0L,
    val windowEnd: Long = 0L,
    val now: Long = 0L,
) {
    val hasContent: Boolean
        get() = channels.isNotEmpty()
}

/** Everything Home shows, at one moment. */
@Immutable
data class HomeFeed(
    val heroItems: List<HeroItem> = emptyList(),
    val recentLive: List<ChannelEntity> = emptyList(),
    val favoriteLive: List<ChannelEntity> = emptyList(),
    val config: HomeConfig = HomeConfig(),
    val recentGuide: GuideSliceState = GuideSliceState(),
    val favoriteGuide: GuideSliceState = GuideSliceState(),
)

/**
 * Building Home's rails.
 *
 * These are dependent reads over watch history, the channel list, the customizations and the guide,
 * and the *rules* between them are the app's, not any one screen's: which playlists count, what the
 * user hid, and which channels are eligible to be the hero. A television and a phone lay Home out
 * completely differently and must still agree, item for item, on what is in it.
 */
class HomeFeedReader(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val customize: CustomizationStore,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val guide: GuideReader,
) {
    suspend fun load(): HomeFeed = withContext(Dispatchers.IO) {
        coroutineScope {
            val configAsync = async { settings.homeConfig().first() }
            // syncLive scope only (no active-playlist filter any more): Home rails never show Off sections.
            val liveIdsAsync = async { activeSourceIds(sourceDao, MediaType.LIVE).toSet() }
            val allIdsAsync = async { sourceDao.allSourceIds().toSet() }
            val favouritesAsync = async { channelDao.favoritesListAlpha().first() }

            val config = configAsync.await()
            val liveIds = liveIdsAsync.await()

            // Hidden items / hidden categories never surface on Home either.
            val hidden = hiddenState(allIdsAsync.await().toList())
            val recentLiveAsync = async {
                channelDao.recentlyWatchedWithTimestampFiltered(liveIds.toList(), RECENT_LIVE_ROW_LIMIT).first()
            }
            val liveWithTs = recentLiveAsync.await().filterNot { isChannelHidden(it.channel, hidden) }
            val live = liveWithTs.map { it.channel }
            val favLive = favouritesAsync.await()
                .filter { c -> c.sourceId in liveIds }
                .filterNot { isChannelHidden(it, hidden) }
            val heroItems = buildHeroItems(liveWithTs, config)
            // The two guide slices read different channel sets and never depend on each other.
            val recentGuideAsync = async {
                if (HomeRow.RECENT_CHANNELS in config.visibleOrder && config.recentLiveMode == HomeLiveRowMode.ON_NOW) {
                    liveGuide(liveIds, live)
                } else {
                    GuideSliceState()
                }
            }
            val favoriteGuideAsync = async {
                if (HomeRow.FAVORITE_CHANNELS in config.visibleOrder && config.favoriteLiveMode == HomeLiveRowMode.ON_NOW) {
                    liveGuide(liveIds, favLive)
                } else {
                    GuideSliceState()
                }
            }

            // Customize renames go on last: every key and guide lookup above ran on the provider's name,
            // which is what a channel without a provider id is keyed by.
            val names = Names(hidden.live)
            HomeFeed(
                heroItems = heroItems.map { names.of(it) },
                recentLive = live.map { names.of(it) },
                favoriteLive = favLive.map { names.of(it) },
                config = config,
                recentGuide = recentGuideAsync.await().let { it.copy(channels = it.channels.map { ch -> names.of(ch) }) },
                favoriteGuide = favoriteGuideAsync.await().let { it.copy(channels = it.channels.map { ch -> names.of(ch) }) },
            )
        }
    }

    /** The user's hide customizations for live channels, with category keys resolved to ids. */
    private data class HiddenState(
        val live: SectionCustomizations,
        val liveCats: Set<Long>,
    ) {
        val isEmpty: Boolean
            get() = live.hiddenItems.isEmpty() && liveCats.isEmpty()
    }

    private suspend fun hiddenState(sourceIds: List<Long>): HiddenState {
        val live = customize.observeVisible(MediaType.LIVE).first()
        val liveCats = if (live.hiddenCategories.isEmpty() || sourceIds.isEmpty()) {
            emptySet()
        } else {
            AdultCategoryClassifier.hiddenCategoryIds(
                categoryDao.observe(sourceIds, MediaType.LIVE).first(),
                live.hiddenCategories,
            )
        }
        return HiddenState(live = live, liveCats = liveCats)
    }

    /** The names the user gave items in Customize, put onto what Home draws. */
    private inner class Names(
        val live: SectionCustomizations,
    ) {
        fun of(ch: ChannelEntity) = ch.copy(name = live.displayName(ch))

        fun of(hero: HeroItem): HeroItem = when (hero) {
            is HeroItem.LiveHero -> hero.copy(channel = of(hero.channel))
        }
    }

    private fun isChannelHidden(ch: ChannelEntity, h: HiddenState): Boolean =
        CustomizeKeys.channel(ch) in h.live.hiddenItems || (ch.categoryId != null && ch.categoryId in h.liveCats)

    private suspend fun buildHeroItems(
        liveChannels: List<ChannelWithWatchedAt>,
        config: HomeConfig,
    ): List<HeroItem> {
        if (!config.heroIncludeLive) return emptyList()
        return liveChannels
            .sortedByDescending { it.watchedAt }
            .take(MAX_HERO_ITEMS)
            .map { watched -> HeroItem.LiveHero(watched.channel, watched.watchedAt) }
    }

    /** A live rail in "On now" mode: six hours of guide for the channels it is about to draw. */
    private suspend fun liveGuide(
        activeIds: Set<Long>,
        channels: List<ChannelEntity>,
    ): GuideSliceState {
        val now = System.currentTimeMillis()
        val windowStart = now / HALF_HOUR_MS * HALF_HOUR_MS
        val windowEnd = windowStart + SLICE_WINDOW_MS
        if (channels.isEmpty()) return GuideSliceState(now = now, windowStart = windowStart, windowEnd = windowEnd)

        return GuideSliceState(
            channels = channels,
            programmes = guide.slice(
                channels = channels,
                cust = customize.observe(MediaType.LIVE).first(),
                globalShiftMinutes = settings.epgOffsetMinutes.first(),
                from = windowStart,
                to = windowEnd,
            ),
            windowStart = windowStart,
            windowEnd = windowEnd,
            now = now,
        )
    }
}

private const val MAX_HERO_ITEMS = 10
private const val SLICE_WINDOW_MS = 360 * 60_000L
private const val HALF_HOUR_MS = 30 * 60_000L
private const val RECENT_LIVE_ROW_LIMIT = 20
