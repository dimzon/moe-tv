package tv.own.myowntv.features.settings
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

class LinkedSubtitleDisplayTitleTest {

    @Test
    fun `new raw episode title supplies locale-neutral formatting parts`() {
        assertEquals(
            EpisodeDisplayTitleParts(baseTitle = "Breaking Bad", season = 1, episode = 3),
            episodeDisplayTitleParts("SERIES", "Breaking Bad", "episode:tmdb:123:S1E3"),
        )
    }

    @Test
    fun `matching legacy suffix is stripped before localized formatting`() {
        assertEquals(
            EpisodeDisplayTitleParts(baseTitle = "Breaking Bad", season = 1, episode = 3),
            episodeDisplayTitleParts("SERIES", "Breaking Bad · S1E3", "episode:tmdb:123:S1E3"),
        )
    }

    @Test
    fun `mismatched suffix remains part of the provider title`() {
        assertEquals(
            EpisodeDisplayTitleParts(baseTitle = "Breaking Bad · S2E5", season = 1, episode = 3),
            episodeDisplayTitleParts("SERIES", "Breaking Bad · S2E5", "episode:tmdb:123:S1E3"),
        )
    }

    @Test
    fun `movie and malformed episode keys need no episode formatting`() {
        assertNull(episodeDisplayTitleParts("MOVIE", "Inception", "movie:tmdb:456"))
        assertNull(episodeDisplayTitleParts("SERIES", "Breaking Bad", "episode:tmdb:123"))
    }
}
