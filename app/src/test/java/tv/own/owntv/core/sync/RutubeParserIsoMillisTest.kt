package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hand-rolled ISO-8601 parser standing in for `java.time` on Android 7 (API 24/25). The
 * real producer is the brief schedule API: `2026-11-14T16:55:00+03:00`, Moscow offset, no
 * fractional seconds. The anchors below match the pangolin epoch-seconds for the same
 * programme ("Кто хочет стать миллионером?" on 2026-11-14: 1794664500), so the two feed
 * shapes are provably parsed to the same instant.
 */
class RutubeParserIsoMillisTest {

    @Test
    fun parsesTheRealBriefFormat() {
        // 16:55 MSK = 13:55 UTC = the pangolin "start":"1794664500".
        assertEquals(1_794_664_500_000L, RutubeParser.parseIsoMillis("2026-11-14T16:55:00+03:00"))
    }

    @Test
    fun parsesZuluAndNegativeOffsets() {
        assertEquals(1_794_664_500_000L, RutubeParser.parseIsoMillis("2026-11-14T13:55:00Z"))
        assertEquals(1_794_664_500_000L, RutubeParser.parseIsoMillis("2026-11-14T13:55:00z"))
        assertEquals(1_794_693_300_000L, RutubeParser.parseIsoMillis("2026-11-14T16:55:00-05:00"))
    }

    @Test
    fun dropsTheFractionalPart() {
        assertEquals(1_794_664_500_000L, RutubeParser.parseIsoMillis("2026-11-14T16:55:00.123+03:00"))
        assertEquals(1_794_664_500_000L, RutubeParser.parseIsoMillis("2026-11-14T13:55:00.123456789Z"))
    }

    @Test
    fun crossesMidnightLikeTheBriefFeedDoes() {
        // "Умнее всех" runs 22:50 MSK → 00:20 MSK next day; both sides must land on UTC days apart.
        assertEquals(1_794_685_800_000L, RutubeParser.parseIsoMillis("2026-11-14T22:50:00+03:00"))
        assertEquals(1_794_691_200_000L, RutubeParser.parseIsoMillis("2026-11-15T00:20:00+03:00"))
    }

    @Test
    fun rejectsMissingOrMalformedZone() {
        assertNull(RutubeParser.parseIsoMillis("2026-11-14T16:55:00"))
        assertNull(RutubeParser.parseIsoMillis("2026-11-14T16:55:00+0300")) // no colon
        assertNull(RutubeParser.parseIsoMillis("2026-11-14T16:55:00+03")) // short offset
        assertNull(RutubeParser.parseIsoMillis("2026-11-14T16:55:00X"))
    }

    @Test
    fun rejectsInvalidDatesAndTimes() {
        assertNull(RutubeParser.parseIsoMillis("2026-02-31T10:00:00+03:00")) // Feb 31
        assertNull(RutubeParser.parseIsoMillis("2026-13-01T10:00:00+03:00")) // month 13
        assertNull(RutubeParser.parseIsoMillis("2026-11-14T25:00:00+03:00")) // hour 25
        assertNull(RutubeParser.parseIsoMillis("2026-11-14T16:60:00+03:00")) // minute 60
    }

    @Test
    fun rejectsGarbageAndShortInput() {
        assertNull(RutubeParser.parseIsoMillis(""))
        assertNull(RutubeParser.parseIsoMillis("2026-11-14"))
        assertNull(RutubeParser.parseIsoMillis("not-a-date"))
        assertNull(RutubeParser.parseIsoMillis("2026-11-14T16:55:0+03:00"))
        assertNull(RutubeParser.parseIsoMillis("9999-aa-14T16:55:00+03:00"))
    }
}
