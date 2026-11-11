package tv.own.myowntv.features.shell.components
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
import tv.own.myowntv.features.settings.VideoGroup

/**
 * The eleven Settings groups, in the order of More › Settings' cards — a card's index is the
 * group it opens. [video] marks the five groups whose rows are the Video player's.
 */
internal enum class SettingsGroup(val titleRes: Int, val video: VideoGroup? = null) {
    QUICK(R.string.settings_group_quick),
    SOURCES(R.string.settings_group_sources),
    APPEARANCE(R.string.settings_group_appearance),
    LAYOUT(R.string.settings_group_layout),
    CONTENT(R.string.settings_group_content_metadata),
    PLAYER(R.string.settings_vp_cat_player, VideoGroup.PLAYER),
    PICTURE(R.string.settings_vp_cat_picture, VideoGroup.PICTURE),
    SOUND(R.string.settings_group_sound_subtitles, VideoGroup.SOUND),
    LIVE(R.string.settings_live_tv, VideoGroup.LIVE),
    WATCHING(R.string.settings_group_watching_recording, VideoGroup.WATCHING),
    APP(R.string.settings_group_app),
    ;

    companion object {
        /** The group a Video player row lives in; Diagnostics sits at the end of App. */
        fun of(group: VideoGroup): SettingsGroup = entries.firstOrNull { it.video == group } ?: APP
    }
}
