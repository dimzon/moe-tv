package tv.own.myowntv.features.home
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRulesTest {

    // --- Keep watching stills: TMDB backdrop → provider backdrop → poster → channel logo ---

    @Test
    fun tmdbBackdropWins() = assertEquals(HomeStill.Picture("tmdb"), homeStill("tmdb", "prov", "poster", null))

    @Test
    fun providerBackdropWhenNoTmdb() = assertEquals(HomeStill.Picture("prov"), homeStill(null, "prov", "poster", null))

    @Test
    fun posterIsCroppedWhenNoBackdrop() = assertEquals(HomeStill.Picture("poster"), homeStill(" ", null, "poster", null))

    @Test
    fun channelShowsItsLogo() = assertEquals(HomeStill.Logo("logo"), homeStill(null, null, null, "logo"))

    @Test
    fun nothingAtAll() = assertEquals(HomeStill.None, homeStill(null, "", null, ""))

    // --- the Trending pager ---

    @Test
    fun rightGoesToNext() = assertEquals(3, trendingPagerTarget(2, 6, 1))

    @Test
    fun rightOnLastWraps() = assertEquals(0, trendingPagerTarget(5, 6, 1))

    @Test
    fun leftGoesToPrevious() = assertEquals(1, trendingPagerTarget(2, 6, -1))

    @Test
    fun leftOnFirstIsNotThePagers() = assertNull(trendingPagerTarget(0, 6, -1))

    @Test
    fun singleTitleNeverPages() = assertNull(trendingPagerTarget(0, 1, 1))
}
