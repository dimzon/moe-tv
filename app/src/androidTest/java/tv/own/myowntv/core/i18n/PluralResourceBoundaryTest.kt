package tv.own.owntv.core.i18n

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
        val zero = context.resources.getQuantityString(R.plurals.search_result_count, 0, 0)
        val one = context.resources.getQuantityString(R.plurals.search_result_count, 1, 1)
        val two = context.resources.getQuantityString(R.plurals.search_result_count, 2, 2)
        assertTrue(zero.contains("results"))
        assertTrue(one.contains("result"))
        assertTrue(two.contains("results"))
    }

    @Test
    fun compact_counts_keep_the_pill_short() {
        val context = localized("en-US")
        val compact = listOf(999, 1_000, 125_000, 1_000_000).map { compactCount(context, it) }
        // CompactDecimalFormat rounds 999 to 1K on some API 24 images; the contract is shortness,
        // not a particular suffix or rounding threshold.
        assertTrue(compact.all { it.isNotBlank() && it.length <= 4 })
    }

    @Test
    fun english_and_russian_quantity_requests_have_a_valid_fallback() {
        listOf("en-US", "ru").forEach { tag ->
            val context = localized(tag)
            listOf(0, 1, 2, 5, 21, 102).forEach { count ->
                val text = context.resources.getQuantityString(
                    R.plurals.settings_epg_sources_status_count_programmes,
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
