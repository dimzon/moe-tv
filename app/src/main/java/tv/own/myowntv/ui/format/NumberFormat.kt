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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat

@Composable
fun rememberLocalizedIntegerFormatter(grouping: Boolean = true): (Int) -> String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale, grouping) {
        NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = grouping }
    }
    return remember(formatter) { { value -> formatter.format(value) } }
}

@Composable
fun localizedInteger(value: Int, grouping: Boolean = true): String =
    rememberLocalizedIntegerFormatter(grouping)(value)

@Composable
fun rememberLocalizedDecimalFormatter(
    minimumFractionDigits: Int = 0,
    maximumFractionDigits: Int = 2,
): (Double) -> String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale, minimumFractionDigits, maximumFractionDigits) {
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            this.minimumFractionDigits = minimumFractionDigits
            this.maximumFractionDigits = maximumFractionDigits
        }
    }
    return remember(formatter) { { value -> formatter.format(value) } }
}

@Composable
fun localizedDecimal(
    value: Double,
    minimumFractionDigits: Int = 0,
    maximumFractionDigits: Int = 2,
): String = rememberLocalizedDecimalFormatter(minimumFractionDigits, maximumFractionDigits)(value)
