package tv.own.owntv.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SupportedLocales regression tests (docs/internationalization.md 0b/4c).
 *
 * The generated catalogue is pure Kotlin data — no Android framework dependencies — so it is fully
 * testable on the JVM. These tests pin the catalogue's structural invariants so a stale or
 * hand-edited generation is caught here, not at picker-runtime.
 */
class SupportedLocalesTest {

    @Test
    fun `catalogue has source and ex-USSR backlog`() {
        // English is the source; Russian is the only shipped translation. Ukrainian, Estonian,
        // Latvian and Lithuanian stay catalogue-only until a translation reaches the readiness
        // threshold and a maintainer promotes them.
        assertEquals(6, SupportedLocales.all.size)
        assertEquals(6, SupportedLocales.all.count { it.tier == 1 || it.tier == 2 })
        assertEquals(2, SupportedLocales.all.count { it.tier == 1 })
    }

    @Test
    fun `catalogue-only locales are unshipped invisible and zero coverage`() {
        // Their translations live only on Weblate's `translations` branch, never in this repo.
        SupportedLocales.all.filter { it.tier == 2 }.forEach {
            assertFalse("${it.id} must not be packaged", it.packaged)
            assertFalse("${it.id} must not be picker-visible", it.pickerVisible)
            assertEquals("${it.id} must start at 0%", 0, it.coverage)
        }
    }

    @Test
    fun `translation readiness threshold has an exact 74 75 boundary`() {
        assertEquals(75, SupportedLocales.TRANSLATION_READINESS_THRESHOLD_PERCENT)
        assertFalse(SupportedLocales.isTranslationReady(74))
        assertTrue(SupportedLocales.isTranslationReady(75))
    }

    @Test
    fun `established Tier 1 locales are packaged and picker-visible`() {
        val tier1 = SupportedLocales.all.filter { it.tier == 1 }
        tier1.forEach {
            assertTrue("${it.id} should be packaged", it.packaged)
            assertTrue("${it.id} should be picker-visible", it.pickerVisible)
        }
    }

    @Test
    fun `ids are unique`() {
        val ids = SupportedLocales.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `language tags are unique`() {
        val tags = SupportedLocales.all.map { it.languageTag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun `resource qualifiers are unique`() {
        val quals = SupportedLocales.all.map { it.resourceQualifier }
        assertEquals(quals.size, quals.toSet().size)
    }

    @Test
    fun `canonicalTag accepts only catalogue tags and canonicalizes case`() {
        assertEquals("ru", SupportedLocales.canonicalTag(" RU "))
        assertEquals("uk", SupportedLocales.canonicalTag("UK"))
        assertEquals("et", SupportedLocales.canonicalTag("ET"))
        assertEquals("en-US", SupportedLocales.canonicalTag("en-us"))
        assertEquals("", SupportedLocales.canonicalTag("  "))
        assertNull(SupportedLocales.canonicalTag("de"))
        assertNull(SupportedLocales.canonicalTag("und"))
        assertNull(SupportedLocales.canonicalTag("de-DE"))
        assertNull(SupportedLocales.canonicalTag("not-a-locale"))
    }

    @Test
    fun `scriptForTag returns the catalogue script for known tags`() {
        assertEquals("Latn", SupportedLocales.scriptForTag("en-US"))
        assertEquals("Cyrl", SupportedLocales.scriptForTag("ru"))
        assertEquals("Cyrl", SupportedLocales.scriptForTag("uk"))
        assertEquals("Latn", SupportedLocales.scriptForTag("et"))
        assertEquals("Latn", SupportedLocales.scriptForTag("lv"))
        assertEquals("Latn", SupportedLocales.scriptForTag("lt"))
    }

    @Test
    fun `scriptForTag returns null for unknown tags`() {
        // Tags not in the catalogue return null — the caller (LocalizedContent) then falls back to
        // ICU likely-subtags. A non-null return for an unknown tag would be a bug.
        assertNull(SupportedLocales.scriptForTag("de"))
        assertNull(SupportedLocales.scriptForTag("de-DE"))
        assertNull(SupportedLocales.scriptForTag("unknown"))
        assertNull(SupportedLocales.scriptForTag(""))
    }

    @Test
    fun `no remaining locale is RTL`() {
        // Every shipped or catalogued locale is Latin or Cyrillic; Arabic, Persian and Hebrew were
        // removed with the non-ex-USSR translations.
        assertFalse(SupportedLocales.all.any { it.rtl })
        assertFalse(SupportedLocales.isRtl("ru"))
        assertFalse(SupportedLocales.isRtl("ar"))
        assertFalse(SupportedLocales.isRtl("unknown"))
    }

    @Test
    fun `pickerRows excludes non-packaged and non-visible locales`() {
        val rows = SupportedLocales.pickerRows
        assertEquals(2, rows.size)
        assertEquals(listOf("en-US", "ru"), rows.map { it.id })
        assertFalse(rows.any { it.tier == 2 })
        rows.forEach {
            assertTrue("${it.id} must be packaged", it.packaged)
            assertTrue("${it.id} must be pickerVisible", it.pickerVisible)
        }
    }

    @Test
    fun `system default tag is the empty string`() {
        assertEquals("", SupportedLocales.SYSTEM_DEFAULT_TAG)
    }

    @Test
    fun `source language coverage is 100`() {
        val en = SupportedLocales.all.first { it.id == "en-US" }
        assertEquals(100, en.coverage)
    }

    @Test
    fun `100 percent visible community locale hides coverage badge`() {
        val complete = SupportedLocales.all.first { it.id == "ru" }.copy(coverage = 100)
        assertNull(SupportedLocales.coverageBadgePercent(complete))
    }

    @Test
    fun `partial visible community locale shows badge while source and hidden locales do not`() {
        val visible = SupportedLocales.all.first { it.id == "ru" }.copy(coverage = 99)
        val source = SupportedLocales.all.first { it.id == "en-US" }.copy(coverage = 99)
        val hidden = visible.copy(pickerVisible = false)
        val regionalOverride = visible.copy(tier = 0, pickerVisible = true)
        assertEquals(99, SupportedLocales.coverageBadgePercent(visible))
        assertNull(SupportedLocales.coverageBadgePercent(source))
        assertNull(SupportedLocales.coverageBadgePercent(hidden))
        assertNull(SupportedLocales.coverageBadgePercent(regionalOverride))
    }

    @Test
    fun `locale coverage stays within percentage bounds`() {
        SupportedLocales.all.forEach { locale ->
            assertTrue("${locale.id} coverage is out of range", locale.coverage in 0..100)
        }
    }

    @Test
    fun `every entry has non-blank required fields`() {
        SupportedLocales.all.forEach { e ->
            assertTrue("id is blank", e.id.isNotBlank())
            assertTrue("languageTag is blank", e.languageTag.isNotBlank())
            assertTrue("resourceQualifier is blank", e.resourceQualifier.isNotBlank())
            assertTrue("weblateCode is blank", e.weblateCode.isNotBlank())
            assertTrue("englishName is blank", e.englishName.isNotBlank())
            assertTrue("endonym is blank", e.endonym.isNotBlank())
            assertTrue("script is blank", e.script.isNotBlank())
        }
    }
}
