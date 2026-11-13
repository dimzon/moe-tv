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
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
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

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class DateFormatterKey(
    val locale: Locale,
    val skeleton: String,
    val timeZoneId: String,
)

@Composable
fun rememberSystemTimeFormatter(): (Long) -> String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val formatter = remember(context, configuration) {
        DateFormat.getTimeFormat(context)
    }
    return remember(formatter) {
        val date = Date(0L)
        val format: (Long) -> String = { ms ->
            date.time = ms
            formatter.format(date)
        }
        format
    }
}

fun formatSystemTime(context: Context, ms: Long): String {
    return DateFormat.getTimeFormat(context).format(Date(ms))
}

@Composable
fun rememberBestDateFormatter(
    skeleton: String,
): (Long) -> String {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val timeZone = TimeZone.getDefault()
    val key = DateFormatterKey(locale, skeleton, timeZone.id)
    val formatter = remember(key) {
        bestDateFormatter(locale, skeleton, timeZone)
    }
    return remember(formatter) { { ms -> formatter.format(Date(ms)) } }
}

/** Year, abbreviated month, day — the shortest form that still identifies a specific broadcast. */
private const val AIR_DATE_SKELETON = "yMMMd"

fun formatBestDate(
    context: Context,
    skeleton: String,
    ms: Long,
): String {
    val locale = context.resources.configuration.locales[0]
    return bestDateFormatter(locale, skeleton, TimeZone.getDefault()).format(Date(ms))
}

fun formatBestDateTime(
    context: Context,
    dateSkeleton: String,
    ms: Long,
): String {
    val skeleton = combinedDateTimeSkeleton(dateSkeleton, DateFormat.is24HourFormat(context))
    return formatBestDate(context, skeleton, ms)
}

/** The date part of [dateSkeleton] plus the time in the device's own hour cycle. Any time fields the
 *  caller left in are dropped first: two hour fields ("jm" + "Hm") make getBestDateTimePattern throw. */
internal fun combinedDateTimeSkeleton(dateSkeleton: String, is24Hour: Boolean): String =
    dateSkeleton.filterNot { it in TIME_FIELDS } + if (is24Hour) "Hm" else "hm"

/** Skeleton letters for hour, minute, second and day period. */
private const val TIME_FIELDS = "hHkKjJCmsabB"

private fun bestDateFormatter(
    locale: Locale,
    skeleton: String,
    timeZone: TimeZone,
): SimpleDateFormat {
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }
}
