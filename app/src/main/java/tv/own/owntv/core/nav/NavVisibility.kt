package tv.own.owntv.core.nav

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Which nav destinations to show, for whichever app is asking.
 *
 * - **STATIC** (the default): every browse item minus the set the user hid.
 * - **DYNAMIC**: derived from the active playlist's content — see [MainSection.dynamicVisible].
 *   When the source picker is on "All playlists" (`defaultSourceId <= 0`) the counts are unioned
 *   across the user's sources, so a VOD-only playlist still hides Live in the merged view.
 *   Per-section Off flags drop a section even when cached rows for it remain.
 *
 * The counts come from the reactive `countAll` DAO flows, which Room re-emits on every write, so the
 * nav updates itself right after a sync with no probe call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavVisibility(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val channelDao: ChannelDao,
) {

    /** What the nav shows right now, honouring the user's mode and hidden set. */
    fun visibleSections(): Flow<Set<MainSection>> = settings.navMenuMode
        .flatMapLatest { mode ->
            combine(dynamicCaps(), settings.navMenuHidden) { contentBased, hidden ->
                when (mode) {
                    SettingsRepository.NavMenuMode.STATIC -> MainSection.staticVisible(hidden)
                    SettingsRepository.NavMenuMode.DYNAMIC -> contentBased
                }
            }
        }
        .distinctUntilChanged()

    /** The set DYNAMIC mode *would* show. Also what the settings screen's read-only rows report, so
     *  the two can never disagree about what DYNAMIC means. */
    fun dynamicCaps(): Flow<Set<MainSection>> =
        sourceRepository.observeSources().flatMapLatest { sources ->
            settings.defaultSourceId.flatMapLatest { defaultId -> capsFlow(sources, defaultId) }
        }

    private fun capsFlow(sources: List<SourceEntity>, defaultId: Long): Flow<Set<MainSection>> {
        val scoped = if (defaultId > 0) sources.filter { it.id == defaultId } else sources
        if (scoped.isEmpty()) return flowOf(MainSection.dynamicVisible(hasLive = false))
        val liveIds = scoped.filter { it.syncLive }.map { it.id }
        // Empty id lists would make countAll misbehave — use a sentinel that matches nothing.
        val empty = listOf(-1L)
        return channelDao.countAll(liveIds.ifEmpty { empty }).map { channels ->
            MainSection.dynamicVisible(hasLive = liveIds.isNotEmpty() && channels > 0)
        }
    }
}
