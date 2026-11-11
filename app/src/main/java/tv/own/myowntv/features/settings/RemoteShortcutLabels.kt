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

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.format.localizedInteger

@Composable
internal fun remoteButtonLabel(keyCode: Int): String = when {
    keyCode in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ->
        localizedInteger(keyCode - AndroidKeyEvent.KEYCODE_0, grouping = false)
    keyCode in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 ->
        localizedInteger(keyCode - AndroidKeyEvent.KEYCODE_NUMPAD_0, grouping = false)
    else -> when (keyCode) {
    AndroidKeyEvent.KEYCODE_CHANNEL_UP -> stringResource(R.string.settings_remote_button_channel_up)
    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> stringResource(R.string.settings_remote_button_channel_down)
    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> stringResource(R.string.settings_remote_button_rewind)
    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> stringResource(R.string.settings_remote_button_fast_forward)
    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> stringResource(R.string.settings_remote_button_previous)
    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> stringResource(R.string.settings_remote_button_next)
    AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> stringResource(R.string.settings_remote_button_last_channel)
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
    -> stringResource(R.string.settings_remote_action_play_pause)
    AndroidKeyEvent.KEYCODE_GUIDE -> stringResource(R.string.common_nav_guide)
    AndroidKeyEvent.KEYCODE_INFO -> stringResource(R.string.player_tool_info)
    AndroidKeyEvent.KEYCODE_MENU -> stringResource(R.string.settings_remote_button_menu)
    AndroidKeyEvent.KEYCODE_CAPTIONS -> stringResource(R.string.player_tool_subtitles)
    AndroidKeyEvent.KEYCODE_PROG_RED -> stringResource(R.string.settings_remote_button_red)
    AndroidKeyEvent.KEYCODE_PROG_GREEN -> stringResource(R.string.settings_remote_button_green)
    AndroidKeyEvent.KEYCODE_PROG_YELLOW -> stringResource(R.string.settings_remote_button_yellow)
    AndroidKeyEvent.KEYCODE_PROG_BLUE -> stringResource(R.string.settings_remote_button_blue)
        else -> stringResource(R.string.settings_remote_shortcuts_unknown_button, keyCode)
    }
}

internal fun remoteButtonIcon(keyCode: Int, fallbackAction: RemoteShortcutAction): MyOwnTVIcon = when {
    keyCode in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ||
        keyCode in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> MyOwnTVIcon.MORE
    else -> when (keyCode) {
        AndroidKeyEvent.KEYCODE_CHANNEL_UP -> MyOwnTVIcon.REMOTE_CHANNEL_UP
        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> MyOwnTVIcon.REMOTE_CHANNEL_DOWN
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> MyOwnTVIcon.REWIND
        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> MyOwnTVIcon.FORWARD
        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> MyOwnTVIcon.SKIP_PREVIOUS
        AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> MyOwnTVIcon.SKIP_NEXT
        AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> MyOwnTVIcon.HISTORY
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
        -> MyOwnTVIcon.PLAY
        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> MyOwnTVIcon.PAUSE
        AndroidKeyEvent.KEYCODE_GUIDE -> MyOwnTVIcon.EPG
        AndroidKeyEvent.KEYCODE_INFO -> MyOwnTVIcon.INFO
        AndroidKeyEvent.KEYCODE_MENU -> MyOwnTVIcon.MENU
        AndroidKeyEvent.KEYCODE_CAPTIONS -> MyOwnTVIcon.SUBTITLE
        AndroidKeyEvent.KEYCODE_PROG_RED,
        AndroidKeyEvent.KEYCODE_PROG_GREEN,
        AndroidKeyEvent.KEYCODE_PROG_YELLOW,
        AndroidKeyEvent.KEYCODE_PROG_BLUE,
        -> MyOwnTVIcon.LIVE_DOT
        else -> remoteActionIcon(fallbackAction)
    }
}

@Composable
internal fun remoteActionLabel(action: RemoteShortcutAction): String = when (action) {
    RemoteShortcutAction.OPEN_HOME -> stringResource(R.string.common_nav_home)
    RemoteShortcutAction.OPEN_LIVE_TV -> stringResource(R.string.common_nav_live_tv)
    RemoteShortcutAction.OPEN_GUIDE -> stringResource(R.string.common_nav_guide)
    RemoteShortcutAction.OPEN_SEARCH -> stringResource(R.string.common_nav_search)
    RemoteShortcutAction.OPEN_SETTINGS -> stringResource(R.string.common_nav_settings)
    RemoteShortcutAction.OPEN_PLAYLIST_SWITCHER -> stringResource(R.string.settings_playlists)
    RemoteShortcutAction.CONTINUE_LAST_WATCHED -> stringResource(R.string.settings_remote_action_continue)
    RemoteShortcutAction.FOCUS_NOW_PLAYING -> stringResource(R.string.settings_remote_action_focus_now_playing)
    RemoteShortcutAction.EXPAND_NOW_PLAYING -> stringResource(R.string.settings_remote_action_expand_now_playing)
    RemoteShortcutAction.ENTER_MINI_PLAYER -> stringResource(R.string.settings_remote_action_mini_player)
    RemoteShortcutAction.ENTER_AUDIO_MODE -> stringResource(R.string.player_tool_audio_only)
    RemoteShortcutAction.PLAY_PAUSE -> stringResource(R.string.settings_remote_action_play_pause)
    RemoteShortcutAction.PAGE_TOWARD_FIRST -> stringResource(R.string.settings_remote_action_page_first)
    RemoteShortcutAction.PAGE_TOWARD_LAST -> stringResource(R.string.settings_remote_action_page_last)
    RemoteShortcutAction.JUMP_TO_FIRST -> stringResource(R.string.settings_remote_action_jump_first)
    RemoteShortcutAction.JUMP_TO_LAST -> stringResource(R.string.settings_remote_action_jump_last)
    RemoteShortcutAction.RETURN_TO_LIVE -> stringResource(R.string.player_go_live)
    RemoteShortcutAction.PREVIOUS_CHANNEL -> stringResource(R.string.player_previous_channel)
    RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS -> stringResource(R.string.player_tool_subtitles)
    RemoteShortcutAction.OPEN_AUDIO_CONTROLS -> stringResource(R.string.player_tool_audio)
    RemoteShortcutAction.OPEN_ASPECT_CONTROLS -> stringResource(R.string.player_tool_aspect)
    RemoteShortcutAction.TOGGLE_PLAYBACK_INFO -> stringResource(R.string.player_tool_info)
    // Legacy VOD destinations: never offered by the picker; shown by their stable
    // identifier so a binding saved before wave 2 is still identifiable.
    RemoteShortcutAction.OPEN_MOVIES,
    RemoteShortcutAction.OPEN_SERIES,
    RemoteShortcutAction.OPEN_DOWNLOADS,
    -> action.name
}

internal fun remoteActionIcon(action: RemoteShortcutAction): MyOwnTVIcon = when (action) {
    RemoteShortcutAction.OPEN_HOME -> MyOwnTVIcon.HOME
    RemoteShortcutAction.OPEN_LIVE_TV, RemoteShortcutAction.RETURN_TO_LIVE -> MyOwnTVIcon.LIVE_TV
    RemoteShortcutAction.OPEN_GUIDE -> MyOwnTVIcon.EPG
    RemoteShortcutAction.OPEN_SEARCH -> MyOwnTVIcon.SEARCH
    RemoteShortcutAction.OPEN_SETTINGS -> MyOwnTVIcon.SETTINGS
    RemoteShortcutAction.OPEN_PLAYLIST_SWITCHER -> MyOwnTVIcon.PLAYLIST
    RemoteShortcutAction.CONTINUE_LAST_WATCHED, RemoteShortcutAction.PLAY_PAUSE -> MyOwnTVIcon.PLAY
    RemoteShortcutAction.FOCUS_NOW_PLAYING -> MyOwnTVIcon.FOCUS_HIGHLIGHT
    RemoteShortcutAction.EXPAND_NOW_PLAYING -> MyOwnTVIcon.EXPAND
    RemoteShortcutAction.ENTER_MINI_PLAYER -> MyOwnTVIcon.PIP
    RemoteShortcutAction.ENTER_AUDIO_MODE, RemoteShortcutAction.OPEN_AUDIO_CONTROLS -> MyOwnTVIcon.AUDIO
    RemoteShortcutAction.PAGE_TOWARD_FIRST, RemoteShortcutAction.JUMP_TO_FIRST -> MyOwnTVIcon.SKIP_PREVIOUS
    RemoteShortcutAction.PAGE_TOWARD_LAST, RemoteShortcutAction.JUMP_TO_LAST -> MyOwnTVIcon.SKIP_NEXT
    RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS -> MyOwnTVIcon.SUBTITLE
    RemoteShortcutAction.OPEN_ASPECT_CONTROLS -> MyOwnTVIcon.ASPECT
    RemoteShortcutAction.TOGGLE_PLAYBACK_INFO -> MyOwnTVIcon.INFO
    RemoteShortcutAction.PREVIOUS_CHANNEL -> MyOwnTVIcon.HISTORY
    RemoteShortcutAction.OPEN_MOVIES -> MyOwnTVIcon.MOVIES
    RemoteShortcutAction.OPEN_SERIES -> MyOwnTVIcon.SERIES
    RemoteShortcutAction.OPEN_DOWNLOADS -> MyOwnTVIcon.DOWNLOADS
}
