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

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSearchTest {

    @Test
    fun `the microphone shows only when a recogniser answers`() {
        assertTrue(VoiceSearch.isAvailable { true })
        assertFalse(VoiceSearch.isAvailable { false })
    }

    @Test
    fun `a package manager that throws hides the microphone instead of crashing`() {
        assertFalse(VoiceSearch.isAvailable { throw SecurityException("blocked by the OEM build") })
        assertFalse(VoiceSearch.isAvailable { throw IllegalStateException("package manager has died") })
    }

    @Test
    fun `the first guess becomes the query, tidied`() {
        assertEquals("world news tonight", VoiceSearch.spokenQuery(Activity.RESULT_OK, listOf("  world   news\ttonight ", "world news to night")))
    }

    @Test
    fun `blank and missing guesses are skipped`() {
        assertEquals("cartoons", VoiceSearch.spokenQuery(Activity.RESULT_OK, listOf(null, "   ", "cartoons")))
        assertNull(VoiceSearch.spokenQuery(Activity.RESULT_OK, listOf(" ", null)))
        assertNull(VoiceSearch.spokenQuery(Activity.RESULT_OK, emptyList()))
        assertNull(VoiceSearch.spokenQuery(Activity.RESULT_OK, null))
    }

    @Test
    fun `backing out of the recogniser leaves the query alone`() {
        assertNull(VoiceSearch.spokenQuery(Activity.RESULT_CANCELED, listOf("news")))
        assertNull(VoiceSearch.spokenQuery(RecognizerErrorCode, listOf("news")))
    }

    private companion object {
        /** RecognizerIntent.RESULT_NO_MATCH: a recogniser's own "heard nothing" code. */
        const val RecognizerErrorCode = 4
    }
}
