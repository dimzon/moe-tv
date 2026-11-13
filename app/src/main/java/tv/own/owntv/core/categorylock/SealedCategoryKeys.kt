package tv.own.owntv.core.categorylock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.model.MediaType

private val LOCKABLE_SECTIONS = listOf(MediaType.LIVE)

/**
 * The sealed category keys of Live TV, Movies and Series. A new value each time a lock closes or
 * opens (a relock on leaving the app, a PIN entry, a lock edit), so a screen that read its content
 * once, like Home, knows to read it again.
 */
fun CustomizationStore.sealedCategoryKeys(): Flow<List<Set<String>>> =
    combine(LOCKABLE_SECTIONS.map { type -> observeVisible(type).map { it.sealedCategories } }) { it.toList() }
        .distinctUntilChanged()

/** The sealed category ids of [type]'s section; a new value only when they change. */
fun CustomizationStore.sealedCategoryIds(type: MediaType): Flow<Set<Long>> =
    observeVisible(type).map { it.sealedCategoryIds }.distinctUntilChanged()

/** True while there is a lock, in any section, that this session has not opened. */
fun CustomizationStore.anyLockSealed(): Flow<Boolean> =
    sealedCategoryKeys().map { sections -> sections.any { it.isNotEmpty() } }.distinctUntilChanged()

/**
 * This flow's latest value again each time [locks] changes, for a reader that checks the locks
 * itself: what it last let through, or hid, is looked at again when a lock closes or opens.
 */
fun <T> Flow<T>.recheckedOn(locks: Flow<*>): Flow<T> = combine(this, locks) { value, _ -> value }
