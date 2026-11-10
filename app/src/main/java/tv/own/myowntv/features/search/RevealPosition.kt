package tv.own.myowntv.features.search
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

import androidx.paging.PagingSource

/**
 * Search's "Go to": where [id] sits in a list's own query, read in large pages off the main thread so
 * the screen can scroll straight there instead of paging through the list on screen. This is the raw
 * position, before the list's hide/move filters: the row is at or above it, so scrolling there loads
 * the right part of the list and the screen then finds the row by id. Null when it is not in the list.
 */
suspend fun <T : Any> PagingSource<Int, T>.positionOf(id: Long, idOf: (T) -> Long, maxRows: Int = 60_000): Int? {
    var offset = 0
    while (offset < maxRows) {
        val page = load(PagingSource.LoadParams.Append(offset, REVEAL_PAGE, false)) as? PagingSource.LoadResult.Page ?: return null
        val i = page.data.indexOfFirst { idOf(it) == id }
        if (i >= 0) return offset + i
        if (page.data.isEmpty() || page.nextKey == null) return null
        offset += page.data.size
    }
    return null
}

private const val REVEAL_PAGE = 1000

/** A pending "Go to": the category to show, the item, and its raw position in that category's list. */
data class Reveal(val key: tv.own.owntv.core.live.LiveKey, val id: Long, val position: Int)
