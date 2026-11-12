package tv.own.owntv.core.categorylock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The item this flow holds, each time it belongs to one of [sealedCategoryIds]: a title or channel
 * still on screen after the user locked its category, or after a relock closed it again. Items with
 * no category are never sealed.
 */
fun <T : Any> Flow<T?>.whileSealed(sealedCategoryIds: Flow<Set<Long>>, categoryOf: (T) -> Long?): Flow<T> =
    combine(this, sealedCategoryIds) { item, sealed -> item?.takeIf { categoryOf(it)?.let(sealed::contains) == true } }
        .filterNotNull()

/**
 * [whileSealed] for items whose membership is many-to-many (live channels): an item is sealed only
 * when every one of its categories is, and one holding no category is never sealed — the same rule
 * [tv.own.owntv.core.database.ChannelMembership.allExcluded] applies.
 */
fun <T : Any> Flow<T?>.whileAllSealed(sealedCategoryIds: Flow<Set<Long>>, categoriesOf: (T) -> Set<Long>): Flow<T> =
    combine(this, sealedCategoryIds) { item, sealed ->
        val cats = item?.let(categoriesOf).orEmpty()
        item?.takeIf { cats.isNotEmpty() && cats.all(sealed::contains) }
    }
        .filterNotNull()

/**
 * Empties [selection] while it holds an item of a sealed category, so a VOD hero, details card or show
 * page stops showing a locked title. A selection that moved on in the meantime is kept.
 */
fun <T : Any> CoroutineScope.clearWhenSealed(
    selection: MutableStateFlow<T?>,
    sealedCategoryIds: Flow<Set<Long>>,
    categoryOf: (T) -> Long?,
): Job = launch { selection.whileSealed(sealedCategoryIds, categoryOf).collect { selection.compareAndSet(it, null) } }
