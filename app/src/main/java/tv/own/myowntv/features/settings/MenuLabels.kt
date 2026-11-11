package tv.own.myowntv.features.settings
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

import tv.own.myowntv.R
import tv.own.owntv.core.settings.SettingsRepository.NavSize
import tv.own.owntv.core.settings.SettingsRepository.NavStyle

/**
 * Settings › Layout › Navigation › Menu labels: one plain choice laid over the rail's Size and Widen on
 * focus, which already decide whether the section names show. It stores nothing of its own, so a Size
 * or Widen set by hand reads back as the matching choice and both rows always agree.
 *
 * Docked: Off = Compact without Widen, When focused = Compact + Widen, Always = any wider Size.
 * Floating hides its rail while you browse, so names can never be on screen all the time: Off = Compact
 * (the capsule never opens), When focused = any wider Size (it opens at that size while it has focus).
 */
enum class MenuLabels(val labelRes: Int) {
    OFF(R.string.common_off),
    WHEN_FOCUSED(R.string.settings_nav_menu_labels_focused),
    ALWAYS(R.string.settings_nav_menu_labels_always),
    ;

    /** The rail Size and Widen on focus that give this choice. */
    data class Rail(val size: NavSize, val widen: NavSize?)

    companion object {
        /** The choices the style can show, in the order OK steps through them. */
        fun choicesFor(style: NavStyle): List<MenuLabels> =
            if (style == NavStyle.FLOATING) listOf(OFF, WHEN_FOCUSED) else entries

        /** What the rail shows now, read from the settings that drive it. */
        fun of(style: NavStyle, size: NavSize, widen: NavSize?): MenuLabels = when {
            style == NavStyle.FLOATING -> if (size == NavSize.COMPACT) OFF else WHEN_FOCUSED
            size != NavSize.COMPACT -> ALWAYS
            widen != null -> WHEN_FOCUSED
            else -> OFF
        }

        /**
         * The Size and Widen that give [choice], keeping the user's own widths: a Wide rail switched to
         * When focused widens to Wide, and back to Always opens at Wide again. Normal is the width when
         * none has been picked yet. [ALWAYS] on Floating, which [choicesFor] never offers, is When focused.
         */
        fun apply(choice: MenuLabels, style: NavStyle, size: NavSize, widen: NavSize?): Rail {
            val width = size.takeIf { it != NavSize.COMPACT } ?: widen ?: NavSize.NORMAL
            return when {
                choice == OFF -> Rail(NavSize.COMPACT, if (style == NavStyle.FLOATING) widen else null)
                style == NavStyle.FLOATING -> Rail(width, widen)
                choice == WHEN_FOCUSED -> Rail(NavSize.COMPACT, width)
                else -> Rail(width, widen)
            }
        }

        /** The choice OK steps to from [current]. */
        fun next(style: NavStyle, current: MenuLabels): MenuLabels {
            val choices = choicesFor(style)
            return choices[(choices.indexOf(current) + 1) % choices.size]
        }
    }
}
