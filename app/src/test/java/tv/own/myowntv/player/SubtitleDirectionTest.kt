package tv.own.myowntv.player
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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The direction the app-drawn subtitle overlay lays a cue out in. One direction for the whole
 * cue, so a Hebrew line that starts with a dash, a number or a Latin word still reads right to left.
 */
class SubtitleDirectionTest {

    @Test
    fun `hebrew and arabic cues are right to left on every line`() {
        assertTrue(isRtlSubtitle("-שלום, מה שלומך?\n-אני בסדר, תודה."))
        assertTrue(isRtlSubtitle("יש לי בבית\n3 ילדים קטנים."))
        assertTrue(isRtlSubtitle("זה היה מקלע\nM60 של הצבא."))
        assertTrue(isRtlSubtitle("Netflix מציגה\nסרט חדש היום."))
        assertTrue(isRtlSubtitle("إذا السيّد قال هذا،\nفهذا صحيح."))
    }

    @Test
    fun `latin cues and cues with no letters stay left to right`() {
        assertFalse(isRtlSubtitle("Only you can decide\nwhat's best for you, Jesse."))
        assertFalse(isRtlSubtitle("I saw it on שלום TV yesterday."))
        assertFalse(isRtlSubtitle("♪ ... ♪"))
    }
}
