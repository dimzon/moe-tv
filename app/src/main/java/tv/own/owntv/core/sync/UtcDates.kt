package tv.own.owntv.core.sync

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * UTC date helpers over epoch days, standing in for `java.time.LocalDate` (API 26+) on
 * Android 7 (API 24/25, no desugaring). An epoch day is the count of whole days since the
 * epoch — `System.currentTimeMillis() / 86_400_000` IS today's date in UTC, and guide day N
 * is `today + N`.
 */
internal object UtcDates {
    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * One UTC [GregorianCalendar] per thread, borrowed by [iso] and [RutubeParser.parseIsoMillis]
     * instead of a fresh allocation on every parse (the brief feed is re-parsed on every auto-refresh,
     * and each calendar allocates its field array and resolves the zone). A Calendar is mutable and
     * not thread-safe, so the singleton is per-thread; every borrow starts from [Calendar.clear], so
     * no state travels between calls — a parse that threw mid-way leaves nothing behind either.
     * Non-lenient: Feb 31 and friends must fail, not roll over.
     */
    private val calendarPool = object : ThreadLocal<GregorianCalendar>() {
        override fun initialValue(): GregorianCalendar =
            GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { isLenient = false }
    }

    /** The current thread's calendar, freshly cleared and ready for [Calendar.set]. */
    fun borrowCalendar(): Calendar = calendarPool.get()!!.apply { clear() }

    /** Today's UTC epoch day. */
    fun today(): Long = System.currentTimeMillis() / MILLIS_PER_DAY

    /** "yyyy-MM-dd" for a UTC epoch day — the shape pangolin's `programDate` parameter wants. */
    fun iso(epochDay: Long): String {
        val cal = borrowCalendar()
        cal.timeInMillis = epochDay * MILLIS_PER_DAY
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }
}
