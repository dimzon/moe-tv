package tv.own.owntv.core.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherDeepLinkTest {

    @Test
    fun favoritesRowOpensTheFavoritesRail() {
        val link = LauncherDeepLink.OpenFavoritesSection
        assertEquals("moetv://open/favorites", link.toUri().toString())
        assertEquals(link, LauncherDeepLink.parse(link.toUri()))
    }

    @Test
    fun channelCardStillRoundTripsItsStableId() {
        val link = LauncherDeepLink.Live(remoteId = "abc123")
        assertEquals(link, LauncherDeepLink.parse(link.toUri()))
    }

    @Test
    fun unrelatedLinksAreRejected() {
        assertNull(LauncherDeepLink.parse(android.net.Uri.parse("https://example.com/open/live")))
        assertNull(LauncherDeepLink.parse(android.net.Uri.parse("moetv://open/unknown")))
    }
}
