package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** The epoch-day helpers standing in for `java.time.LocalDate` on Android 7 (API 24/25). */
class UtcDatesTest {

    @Test
    fun isoFormatsKnownAnchors() {
        // 2026-11-14T00:00:00Z = 1_794_614_400 s = epoch day 20_771.
        assertEquals("2026-11-14", UtcDates.iso(20_771))
        assertEquals("2026-11-15", UtcDates.iso(20_772))
        assertEquals("2026-01-01", UtcDates.iso(20_454))
        assertEquals("1970-01-01", UtcDates.iso(0))
    }

    @Test
    fun isoCrossesLeapDay() {
        // 2024-02-29T00:00:00Z = 1_709_164_800 s = epoch day 19_782.
        assertEquals("2024-02-29", UtcDates.iso(19_782))
    }

    @Test
    fun todayMatchesTheEpochDayDefinition() {
        assertEquals(System.currentTimeMillis() / 86_400_000L, UtcDates.today())
    }
}
