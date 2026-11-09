package tv.own.myowntv.features.live
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*
import tv.own.owntv.player.ffmpeg.*

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.LiveKey

/**
 * The channel browser's right-hand list. Loads are released by hand on an unconfined scope, so the
 * order in which they finish is chosen by the test, not by timing.
 */
class LiveCategoryPeekTest {

    private fun channel(id: Long, category: Long = 10) = ChannelEntity(
        id = id, sourceId = 1, categoryId = category, name = "Ch $id",
        streamUrl = "http://x/$id.ts", remoteId = "$id",
    )

    private val zap = listOf(channel(1), channel(2))
    private val news = LiveKey.Folder(20)
    private val sport = LiveKey.Folder(30)
    private fun peek() = LiveCategoryPeek(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `opens on the CH± list`() {
        val p = peek()
        p.start(zap, LiveKey.Folder(10))
        assertEquals(zap, p.channels.value)
        assertEquals(LiveKey.Folder(10), p.key.value)
    }

    @Test
    fun `a category shows once loaded`() {
        val p = peek()
        p.start(zap, null)
        val gate = CompletableDeferred<List<ChannelEntity>>()
        p.load(news) { gate.await() }
        assertEquals(zap, p.channels.value) // nothing changes before the load lands
        gate.complete(listOf(channel(5, 20)))
        assertEquals(listOf(5L), p.channels.value.map { it.id })
        assertEquals(news, p.key.value)
    }

    @Test
    fun `a newer pick wins even when the older load lands last`() {
        val p = peek()
        p.start(zap, null)
        val slow = CompletableDeferred<List<ChannelEntity>>()
        // An older load that ignores cancellation, as a database read already under way would.
        p.load(news) { withContext(NonCancellable) { slow.await() } }
        p.load(sport) { listOf(channel(7, 30)) }
        slow.complete(listOf(channel(5, 20)))
        assertEquals(listOf(7L), p.channels.value.map { it.id })
        assertEquals(sport, p.key.value)
    }

    @Test
    fun `an empty category leaves the list as it was`() {
        val p = peek()
        p.start(zap, null)
        p.load(news) { emptyList() }
        assertEquals(zap, p.channels.value)
        assertEquals(null, p.key.value)
    }

    @Test
    fun `each category remembers its focused channel, else the playing one`() {
        val p = peek()
        p.start(zap, null)
        assertEquals(2L, p.entryChannel(playingId = 2))
        p.rememberFocus(1)
        p.load(news) { listOf(channel(5, 20), channel(6, 20)) }
        assertEquals(2L, p.entryChannel(playingId = 2))
        p.rememberFocus(6)
        p.start(zap, null)
        assertEquals(1L, p.entryChannel(playingId = 2))
        p.load(news) { listOf(channel(5, 20), channel(6, 20)) }
        assertEquals(6L, p.entryChannel(playingId = 2))
    }

    @Test
    fun `a shown folder locked while open goes back to the CH± list`() {
        val p = peek()
        p.start(zap, LiveKey.Folder(10))
        p.load(news) { listOf(channel(5, 20)) }
        p.dropCategories(setOf(20))
        assertEquals(zap, p.channels.value)
        assertEquals(LiveKey.Folder(10), p.key.value)
    }

    @Test
    fun `a mixed list loses only the locked folder's channels`() {
        val p = peek()
        p.start(zap, null)
        p.load(LiveKey.Favorites) { listOf(channel(5, 20), channel(6, 30)) }
        p.dropCategories(setOf(20))
        assertEquals(listOf(6L), p.channels.value.map { it.id })
        assertEquals(LiveKey.Favorites, p.key.value)
    }
}
