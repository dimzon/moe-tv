package tv.own.owntv.core.database

import androidx.paging.PagingSource
import androidx.paging.PagingState
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * A [PagingSource] for the manual (Move) browse lists, whose order [ManualOrdering] computes in
 * Kotlin. Room's generated PagingSource cannot express that ordering without a window function, and
 * the system SQLite has none below 3.25 — so the ordering lives here instead.
 *
 * The whole folder is small (a rail's worth of channels), so each load sorts it in memory and pages
 * from that. Invalidation is the caller's job: the browse Pager is rebuilt from a flow that observes
 * the tables the order reads ([tv.own.owntv.core.database.dao.ChannelDao.observeManualVersion]), so a
 * reorder recreates this source with fresh data — exactly what Room's @Query PagingSource did on its
 * own table invalidation.
 */
class ManualOrderPagingSource(
    private val load: suspend () -> List<ManualOrderRow>,
    private val alpha: Boolean,
) : PagingSource<Int, ChannelEntity>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChannelEntity> {
        val all = ManualOrdering.order(load(), alpha)
        val start = (params.key ?: 0).coerceIn(0, all.size)
        val end = (start + params.loadSize).coerceIn(0, all.size)
        return LoadResult.Page(
            data = all.subList(start, end),
            prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
            nextKey = if (end >= all.size) null else end,
        )
    }

    override fun getRefreshKey(state: PagingState<Int, ChannelEntity>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
