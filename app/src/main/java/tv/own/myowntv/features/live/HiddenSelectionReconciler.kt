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
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.core.live.LiveBuiltIns
import tv.own.owntv.core.live.LiveKey

/**
 * A hidden All — at a cold start, from a remembered key, after a playlist switch, or
 * switched off in Settings — must move to [LiveViewModel.fallbackKey]. Lives outside [LiveViewModel]
 * so that move can be tested without the view model's Android, Room and player dependencies. The
 * identity filter skips the `stateIn` placeholder, so a rail not loaded yet cannot pick Favorites
 * over the first folder.
 */
internal suspend fun reconcileHiddenSelection(
    rail: Flow<List<LiveRailItem>>,
    placeholder: List<LiveRailItem>,
    builtIns: Flow<LiveBuiltIns>,
    selected: MutableStateFlow<LiveKey>,
    isLocked: () -> Boolean,
) = combine(rail.filter { it !== placeholder }, builtIns, selected) { items, shown, current ->
    if (shown.shows(current)) null else LiveViewModel.fallbackKey(items)
}.collect { next ->
    if (!isLocked() && next != null) selected.value = next
}

/**
 * After a playlist or profile switch drops the selected folder [stale], wait for a loaded rail (not
 * [placeholder]) that no longer lists it and land on its [LiveViewModel.fallbackKey], so a hidden All
 * never picks a folder from the old playlist's rail. All when no such rail arrives within [timeoutMs].
 */
internal suspend fun fallbackAfterSwitch(rail: Flow<List<LiveRailItem>>, placeholder: List<LiveRailItem>, stale: LiveKey, timeoutMs: Long = 5_000): LiveKey =
    withTimeoutOrNull(timeoutMs) { rail.first { r -> r !== placeholder && r.none { it.key == stale } } }?.let(LiveViewModel::fallbackKey) ?: LiveKey.All
