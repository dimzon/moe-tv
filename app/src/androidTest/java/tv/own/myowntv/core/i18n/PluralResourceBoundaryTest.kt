package tv.own.owntv.core.i18n
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
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.compactCount
import java.util.Locale

/** Exercises quantity selection at the Android resource boundary, including non-English CLDR tags. */
class PluralResourceBoundaryTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun english_zero_one_two_use_plural_resources() {
        val context = localized("en-US")
        val zero = context.resources.getQuantityString(R.plurals.content_count_movies, 0, "Library", 0)
        val one = context.resources.getQuantityString(R.plurals.content_count_movies, 1, "Library", 1)
        val two = context.resources.getQuantityString(R.plurals.content_count_movies, 2, "Library", 2)
        assertTrue(zero.contains("movies"))
        assertTrue(one.contains("movie)"))
        assertTrue(two.contains("movies"))
    }

    @Test
    fun compact_counts_keep_the_pill_short() {
        val context = localized("en-US")
        assertTrue(compactCount(context, 999).contains("999"))
        assertTrue(compactCount(context, 1_000).contains("K"))
        assertTrue(compactCount(context, 125_000).contains("K"))
        assertTrue(compactCount(context, 1_000_000).contains("M"))
    }

    @Test
    fun arabic_polish_and_russian_quantity_requests_have_a_valid_fallback() {
        listOf("ar", "pl", "ru").forEach { tag ->
            val context = localized(tag)
            listOf(0, 1, 2, 5, 21, 102).forEach { count ->
                val text = context.resources.getQuantityString(
                    R.plurals.settings_epg_sources_catchup,
                    count,
                    count,
                )
                assertTrue("$tag/$count must render a non-empty plural", text.isNotBlank())
            }
        }
    }

    private fun localized(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }
}
