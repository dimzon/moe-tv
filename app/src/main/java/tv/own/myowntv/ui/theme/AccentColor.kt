package tv.own.myowntv.ui.theme
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

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import tv.own.myowntv.R
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.roles

/**
 * The display label each [AccentColor] carries, and its tonal palette as Compose colours.
 *
 * The tonal values live in core (`AccentColor.roles(isDark)`) so the mobile app seeds its scheme
 * from the same table; only the label — a resource, and therefore per-app — is here. Neutrals
 * (background, surface containers, text, outline) are theme-only and live in [MyOwnTVColors].
 */
val AccentColor.labelRes: Int
    @StringRes get() = when (this) {
        AccentColor.TEAL -> R.string.settings_accent_teal
        AccentColor.BLUE -> R.string.settings_accent_blue
        AccentColor.VIOLET -> R.string.settings_accent_violet
        AccentColor.GREEN -> R.string.settings_accent_green
        AccentColor.AMBER -> R.string.settings_accent_amber
    }

fun AccentColor.primary(isDark: Boolean) = Color(roles(isDark).primary)
fun AccentColor.onPrimary(isDark: Boolean) = Color(roles(isDark).onPrimary)
fun AccentColor.primaryContainer(isDark: Boolean) = Color(roles(isDark).primaryContainer)
fun AccentColor.onPrimaryContainer(isDark: Boolean) = Color(roles(isDark).onPrimaryContainer)
