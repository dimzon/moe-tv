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

/*
 * The words of each Settings row on a Stage page, by row key: root rows by their own key
 * ("theme", "tab_SOURCES"), Video player rows by their Quick key ("vp_hw"), Player buttons rows by "pb_*".
 * A row's own title and description are used wherever a key is missing here; a long description
 * the row used to carry becomes its panel text when [SETTING_HELP] has nothing better.
 */

/** The short line under the title. */
internal val SETTING_LINE: Map<String, Int> = mapOf(
    "quick_live_preview" to R.string.settings_line_quick_live_preview,
    "quick_preview_sound" to R.string.settings_line_preview_sound,
    "quick_channel_numbers" to R.string.settings_line_quick_channel_numbers,
    "quick_hdr" to R.string.settings_line_prefer_sdr,
    "quick_autoplay" to R.string.settings_line_quick_autoplay,
    "quick_check_update" to R.string.settings_line_when_app_opens,
    "epg_offset" to R.string.settings_line_epg_offset,
    "reminders" to R.string.settings_line_reminders,
    "reminder_lead" to R.string.settings_line_reminder_lead,
    "guide_days" to R.string.settings_line_guide_days,
    "accent" to R.string.settings_line_accent,
    "tab_GLASS_EFFECT" to R.string.settings_line_glass_bg,
    "fonts" to R.string.settings_line_fonts,
    "popup_size" to R.string.settings_line_popup_size,
    "animations" to R.string.settings_line_animations,
    "tab_WEATHER" to R.string.settings_line_weather,
    "live_opens_in" to R.string.settings_line_live_opens_in,
    "tab_PANEL_WIDTH" to R.string.settings_line_panel_width,
    "tab_GUIDE_WIDTH" to R.string.settings_line_guide_width,
    "browsing_lists" to R.string.settings_line_browsing,
    "tab_HOME" to R.string.settings_line_home,
    "tab_CONTENT_MENUS" to R.string.settings_line_content_menus,
    "tab_CH_NAV" to R.string.settings_line_remote_shortcuts,
    "tab_CUSTOMIZE" to R.string.settings_line_customize,
    "app_icon" to R.string.settings_line_app_icon,
    "brand_accent" to R.string.settings_line_brand_accent,
    "settings_lock" to R.string.settings_line_settings_lock,
    "settings_lock_menus" to R.string.settings_line_settings_lock_menus,
    "check_updates" to R.string.settings_line_check_updates,
    "update_startup" to R.string.settings_line_when_app_opens,
    "tab_NETWORK" to R.string.settings_line_proxy,
    "tab_DNS" to R.string.settings_line_dns,
    "vp_external" to R.string.settings_line_external,
    "vp_hw" to R.string.settings_line_hw,
    "vp_hdr" to R.string.settings_line_prefer_sdr,
    "vp_max_quality" to R.string.settings_line_max_quality,
    "vp_tunneled" to R.string.settings_line_tunneled,
    "vp_zoom" to R.string.settings_line_zoom,
    "vp_reset_zoom" to R.string.settings_line_saved_zoom,
    "vp_afr" to R.string.settings_vp_cat_frame_rate_summary,
    "vp_afr_live" to R.string.settings_vp_cat_frame_rate_summary,
    "vp_afr_pause" to R.string.settings_line_films_only,
    "vp_afr_resolution" to R.string.settings_line_films_only,
    "vp_volume" to R.string.settings_line_volume,
    "vp_reset_volume" to R.string.settings_line_saved_volume,
    "vp_surround" to R.string.settings_line_surround,
    "vp_passthrough" to R.string.settings_line_passthrough,
    "vp_night_mode" to R.string.settings_line_night_mode,
    "vp_volume_leveling" to R.string.settings_line_leveling,
    "vp_audio_sync" to R.string.settings_line_audio_sync,
    "vp_output_audio_delay" to R.string.settings_line_output_audio_delay,
    "vp_reset_audio_delay" to R.string.settings_line_saved_audio_sync,
    "vp_audio_lang" to R.string.settings_line_audio_lang,
    "vp_sub_lang" to R.string.settings_line_sub_lang,
    "vp_sub_style" to R.string.settings_line_sub_style,
    "vp_channel_numbers" to R.string.settings_line_channel_numbers,
    "vp_live_preview" to R.string.settings_line_live_preview,
    "vp_preview_audio" to R.string.settings_line_preview_sound,
    "vp_preview_on_ok" to R.string.settings_line_preview_on_ok,
    "vp_live_latency" to R.string.settings_line_latency,
    "vp_latency_sources" to R.string.settings_line_override_playlist,
    "vp_preroll" to R.string.settings_line_preroll,
    "vp_preroll_sources" to R.string.settings_line_override_playlist,
    "vp_tune_timeout" to R.string.settings_line_tune_timeout,
    "vp_tune_timeout_sources" to R.string.settings_line_override_playlist,
    "vp_seek_step" to R.string.settings_line_seek_step,
    "vp_multiview" to R.string.settings_vp_cat_multiview_summary_mobile,
    "vp_multiview_tiles" to R.string.settings_line_tiles,
    "vp_mini" to R.string.settings_line_mini,
    "vp_pip" to R.string.settings_line_pip,
    "vp_measured_stats" to R.string.settings_line_measured_stats,
    "vp_logging" to R.string.settings_line_logging,
)

/** A row's name where the page words it differently from the app's older screens. */
internal val SETTING_TITLE: Map<String, Int> = mapOf(
    "fonts" to R.string.settings_title_fonts,
    "tab_WEATHER" to R.string.settings_title_date_time_weather,
    "vp_tunneled" to R.string.settings_title_tunneled,
    "vp_reset_zoom" to R.string.settings_title_saved_zoom,
    "vp_reset_volume" to R.string.settings_title_saved_volume,
    "vp_reset_audio_delay" to R.string.settings_title_saved_audio_sync,
)

/** The context panel's explanation, where the row's own description is not already one. */
internal val SETTING_HELP: Map<String, Int> = mapOf(
    "quick_live_preview" to R.string.settings_help_live_preview,
    "quick_preview_sound" to R.string.settings_help_preview_audio,
    "quick_channel_numbers" to R.string.settings_help_channel_numbers,
    "quick_check_update" to R.string.settings_help_update_startup,
    "epg_offset" to R.string.settings_epg_offset_root_description,
    "reminders" to R.string.settings_reminders_description,
    "reminder_lead" to R.string.settings_reminder_lead_description,
    "guide_days" to R.string.settings_help_guide_days,
    "theme" to R.string.settings_help_theme,
    "accent" to R.string.settings_help_accent,
    "focus_highlight" to R.string.settings_help_focus,
    "tab_GLASS_EFFECT" to R.string.settings_help_bg_background,
    "fonts" to R.string.settings_help_fonts,
    "popup_size" to R.string.settings_help_popup_size,
    "ui_zoom" to R.string.settings_help_zoom,
    "animations" to R.string.settings_help_animations,
    "tab_WEATHER" to R.string.settings_help_weather,
    "navigation" to R.string.settings_help_navigation,
    "live_layout" to R.string.settings_help_live_layout,
    "live_opens_in" to R.string.settings_live_opens_in_description,
    "tab_PANEL_WIDTH" to R.string.settings_panel_width_description,
    "tab_GUIDE_WIDTH" to R.string.settings_guide_width_description,
    "browsing_lists" to R.string.settings_browsing_description,
    "tab_HOME" to R.string.settings_help_home,
    "tab_CONTENT_MENUS" to R.string.settings_content_menus_description,
    "tab_CH_NAV" to R.string.settings_ch_paging_description,
    "tab_CUSTOMIZE" to R.string.settings_help_customize,
    "app_icon" to R.string.settings_help_app_icon,
    "brand_accent" to R.string.settings_help_brand_accent,
    "app_startup" to R.string.settings_help_startup,
    "settings_lock" to R.string.settings_help_settings_lock,
    "settings_lock_menus" to R.string.settings_help_settings_lock_menus,
    "check_updates" to R.string.settings_help_check_updates,
    "update_startup" to R.string.settings_help_update_startup,
    "auto_start_boot" to R.string.settings_help_auto_start_boot,
    "auto_start_wake" to R.string.settings_help_auto_start_wake,
    "tab_NETWORK" to R.string.settings_proxy_description,
    "tab_DNS" to R.string.settings_help_dns,
    "vp_hw" to R.string.settings_help_hw,
    "vp_zoom" to R.string.settings_help_zoom_mode,
    "vp_volume" to R.string.settings_help_volume,
    "vp_sub_lang" to R.string.settings_help_sub_lang,
    "vp_channel_numbers" to R.string.settings_help_channel_numbers,
    "vp_live_preview" to R.string.settings_help_live_preview,
    "vp_preview_audio" to R.string.settings_help_preview_audio,
    "vp_preview_on_ok" to R.string.settings_help_preview_on_ok,
    "vp_seek_step" to R.string.settings_help_seek_step,
    "vp_mini" to R.string.settings_help_mini,
    "pb_hold_ok" to R.string.settings_help_player_hold_ok,
    "pb_short_ok" to R.string.settings_help_player_short_ok,
    "pb_swap_ch" to R.string.settings_help_player_swap_channel_keys,
    "pb_number_keys" to R.string.settings_help_player_number_keys_tune,
    "vp_pip" to R.string.settings_help_pip,
)

/** For a switch row, whether On is the recommended value (the panel tags it). Absent = no tag. */
internal val SWITCH_RECOMMENDED: Map<String, Boolean> = mapOf(
    "vp_hw" to true,
    "vp_sw_above_1080p" to false,
    "vp_measured_stats" to false,
    "vp_logging" to false,
)
