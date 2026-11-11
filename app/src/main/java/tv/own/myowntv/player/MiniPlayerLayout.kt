package tv.own.myowntv.player
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

import androidx.annotation.StringRes
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import tv.own.myowntv.R
import tv.own.owntv.core.player.MiniPlayerPosition

/** Where on screen each docking spot puts the mini-player window. */
val MiniPlayerPosition.alignment: Alignment
    get() = when (this) {
        MiniPlayerPosition.TOP_LEFT -> AbsoluteAlignment.TopLeft
        MiniPlayerPosition.TOP_CENTER -> Alignment.TopCenter
        MiniPlayerPosition.TOP_RIGHT -> AbsoluteAlignment.TopRight
        MiniPlayerPosition.BOTTOM_LEFT -> AbsoluteAlignment.BottomLeft
        MiniPlayerPosition.BOTTOM_CENTER -> Alignment.BottomCenter
        MiniPlayerPosition.BOTTOM_RIGHT -> AbsoluteAlignment.BottomRight
    }

/** The user-facing label for each docking spot. */
val MiniPlayerPosition.labelRes: Int
    @StringRes get() = when (this) {
        MiniPlayerPosition.TOP_LEFT -> R.string.player_mini_top_left
        MiniPlayerPosition.TOP_CENTER -> R.string.player_mini_top_center
        MiniPlayerPosition.TOP_RIGHT -> R.string.player_mini_top_right
        MiniPlayerPosition.BOTTOM_LEFT -> R.string.player_mini_bottom_left
        MiniPlayerPosition.BOTTOM_CENTER -> R.string.player_mini_bottom_center
        MiniPlayerPosition.BOTTOM_RIGHT -> R.string.player_mini_bottom_right
    }
