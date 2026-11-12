package tv.own.owntv.core.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Keeps [ChannelMembership.snapshot] current by observing `channel_category`. A single app-lifetime
 * observation feeds every in-memory browse / favourites / history / zap / EPG filter, which read the
 * static map via [ChannelMembership] rather than the removed `channels.categoryId`.
 */
class ChannelMembershipStore(dao: ChannelCategoryDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val map: StateFlow<Map<String, Set<Long>>> = dao.observeAll()
        .map { ChannelMembership.mapOf(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        ChannelMembership.snapshot = map.value
        scope.launch { map.collect { ChannelMembership.snapshot = it } }
    }
}
