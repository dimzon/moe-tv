package tv.own.owntv.core.categorylock

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import tv.own.owntv.core.database.dao.CustomCategoryCount
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.model.MediaType

/**
 * A count that leaves out the [sealed] categories: [plain] while none is sealed, [excluding] them
 * otherwise, so a browse state with no lock closed runs exactly the query it ran before locks existed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> lockAwareCount(
    sealed: Flow<Set<Long>>,
    plain: () -> Flow<T>,
    excluding: (sealedIds: List<Long>) -> Flow<T>,
): Flow<T> = sealed.flatMapLatest { ids -> if (ids.isEmpty()) plain() else excluding(ids.toList()) }

/** The Move-to dialog's member counts per custom category, without members filed under a [sealed] category. */
fun CustomCategoryDao.lockAwareCountsByContexts(
    type: MediaType,
    contextKeys: List<String>,
    sourceIds: List<Long>,
    sealed: Set<Long>,
): Flow<List<CustomCategoryCount>> =
    if (sealed.isEmpty()) observeCountsByContexts(type, contextKeys, sourceIds)
    else observeCountsByContextsExcluding(type, contextKeys, sourceIds, sealed.toList())
