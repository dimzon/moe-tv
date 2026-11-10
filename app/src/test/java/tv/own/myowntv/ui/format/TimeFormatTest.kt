package tv.own.myowntv.ui.format
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
import tv.own.owntv.core.profile.*
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TimeFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val instant = Date(1_704_110_400_000L) // 2024-01-01 12:00:00 UTC

    @Test
    fun `12-hour and 24-hour patterns produce different hour cycles`() {
        val twelveHour = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = utc }.format(instant)
        val twentyFourHour = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = utc }.format(instant)

        assertTrue(twelveHour.contains("PM"))
        assertEquals("12:00", twentyFourHour)
        assertEquals("dMMMhm", combinedDateTimeSkeleton("dMMM", is24Hour = false))
        assertEquals("dMMMHm", combinedDateTimeSkeleton("dMMM", is24Hour = true))
    }

    @Test
    fun `a time already in the date skeleton is replaced, never doubled`() {
        // "dMMMjm" + "Hm" was "dMMMjmHm": Android rejects two hour fields and the Error log and Backup
        // screens crashed on every 24-hour device.
        assertEquals("dMMMHm", combinedDateTimeSkeleton("dMMMjm", is24Hour = true))
        assertEquals("dMMMyyyyhm", combinedDateTimeSkeleton("dMMMyyyyjm", is24Hour = false))
        assertEquals("EEEdMMMHm", combinedDateTimeSkeleton("EEEdMMMahmss", is24Hour = true))
    }

    @Test
    fun `locale-specific date patterns keep their date ordering`() {
        val us = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = utc }.format(instant)
        val german = SimpleDateFormat("d. MMM yyyy", Locale.GERMANY).apply { timeZone = utc }.format(instant)

        assertTrue(us.startsWith("Jan"))
        assertTrue(german.startsWith("1."))
    }

    @Test
    fun `locale is part of the remembered formatter key`() {
        val us = DateFormatterKey(Locale.US, "EEEdMMM", utc.id)
        val german = DateFormatterKey(Locale.GERMANY, "EEEdMMM", utc.id)

        assertNotEquals(us, german)
    }
}
