package tv.own.owntv.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES
import tv.own.owntv.core.live.MAX_MULTIVIEW_TILES
import tv.own.owntv.core.live.MIN_MULTIVIEW_TILES
import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.player.SurroundMode
import tv.own.owntv.core.menu.MenuLayout
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.PopupFontScale
import tv.own.owntv.core.theme.PopupSizeScale
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom

/** Startup landing. LAST_CHANNEL also covers "auto-play my channel" since
 *  it's always the one you last watched. LAST_CHANNEL_GUIDE and LAST_CHANNEL_LIVE open the TV Guide
 *  or Live TV instead, on that channel's category with the channel focused in the preview.
 *  Saved by name, so the order here is only the order the choices are listed in. */
enum class StartupMode {
    HOME, LAST_CHANNEL, LAST_CHANNEL_GUIDE, LAST_CHANNEL_LIVE, FAVORITES, SPECIFIC_CHANNEL
}

data class StartupChannelRef(
    val remoteId: String,
    val name: String,
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("remoteId", remoteId)
        .put("name", name)

    companion object {
        fun fromJson(raw: String?): StartupChannelRef? = runCatching {
            val o = org.json.JSONObject(raw ?: return null)
            StartupChannelRef(
                remoteId = o.optString("remoteId").takeIf { it.isNotBlank() } ?: return null,
                name = o.getString("name").takeIf { it.isNotBlank() } ?: return null,
            )
        }.getOrNull()
    }
}

/**
 * Per-source playlist auto-refresh mode. The interval entries are **staleness thresholds**, not strict
 * timers: a source is refreshed when `now - lastSyncAt >= thresholdMs`. OFF disables auto-refresh;
 * STARTUP refreshes on cold app start only; interval modes are checked on cold start and on resume.
 * [thresholdMs] has a default of null so OFF/STARTUP can be declared without an explicit value.
 *
 * Anything longer than 12 hours is [MANUAL], where the user names the number of days — the fixed
 * 24h/48h/7-day entries it replaced are still understood on read (see [PlaylistRefresh.parse]) so an
 * existing selection or an old backup keeps working.
 */
enum class PlaylistAutoRefresh(val thresholdMs: Long? = null) {
    OFF,
    STARTUP,
    HOURS_6(6 * 3600_000L),
    HOURS_12(12 * 3600_000L),

    /** A whole number of days chosen by the user; the count lives in [PlaylistRefresh.manualDays]. */
    MANUAL,
}

/**
 * A playlist's auto-refresh selection: the mode, plus the day count that only [PlaylistAutoRefresh.MANUAL]
 * uses. Stored as one string per source so nothing else — backup, restore, the companion form — has to
 * learn about a second value: `"OFF"`, `"HOURS_6"`, or `"MANUAL:14"`.
 */
data class PlaylistRefresh(
    val mode: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
    val manualDays: Int = DEFAULT_MANUAL_DAYS,
) {
    /** How stale the source may get before it is refreshed; null when it never auto-refreshes on a timer. */
    val thresholdMs: Long?
        get() = if (mode == PlaylistAutoRefresh.MANUAL) {
            manualDays.coerceIn(MIN_MANUAL_DAYS, MAX_MANUAL_DAYS) * 24 * 3600_000L
        } else {
            mode.thresholdMs
        }

    fun serialize(): String =
        if (mode == PlaylistAutoRefresh.MANUAL) "${mode.name}$SEPARATOR$manualDays" else mode.name

    companion object {
        const val MIN_MANUAL_DAYS = 1
        const val MAX_MANUAL_DAYS = 99
        const val DEFAULT_MANUAL_DAYS = 7

        /** Day counts offered directly where a stepper is not the right control — a phone's list. */
        val PRESET_MANUAL_DAYS = listOf(1, 2, 3, 7, 14, 30)

        private const val SEPARATOR = ':'

        val OFF = PlaylistRefresh(PlaylistAutoRefresh.OFF)

        /**
         * Reads a stored value, translating the retired fixed entries into the day counts that mean the
         * same thing. Nothing rewrites the stored string for them — the translation happens on every read,
         * so a user who never opens the picker again keeps the schedule they chose, forever.
         */
        fun parse(raw: String?): PlaylistRefresh = when (val value = raw?.trim().orEmpty()) {
            "HOURS_24" -> PlaylistRefresh(PlaylistAutoRefresh.MANUAL, 1)
            "HOURS_48" -> PlaylistRefresh(PlaylistAutoRefresh.MANUAL, 2)
            "DAYS_7" -> PlaylistRefresh(PlaylistAutoRefresh.MANUAL, 7)
            else -> {
                val mode = runCatching {
                    PlaylistAutoRefresh.valueOf(value.substringBefore(SEPARATOR))
                }.getOrDefault(PlaylistAutoRefresh.OFF)
                val days = value.substringAfter(SEPARATOR, "").toIntOrNull()
                    ?.coerceIn(MIN_MANUAL_DAYS, MAX_MANUAL_DAYS)
                    ?: DEFAULT_MANUAL_DAYS
                PlaylistRefresh(mode, days)
            }
        }
    }
}

/**
 * Per-EPG-source auto-refresh mode. Same staleness-threshold semantics as [PlaylistAutoRefresh].
 *
 * [MANUAL] is the same addition playlists already had: there was no reason a user could say "refresh
 * this playlist every 10 days" but not "refresh this guide every 10 days", and 48 hours — the longest
 * fixed option — used to be the storage horizon too, so even the best setting could run dry exactly at
 * the boundary.
 *
 * **Every existing entry is kept.** A stored `HOURS_48` still parses to `HOURS_48` and behaves
 * exactly as before; nothing rewrites a selection the user already made.
 */
enum class EpgAutoRefresh(val thresholdMs: Long? = null) {
    OFF,
    STARTUP,
    HOURS_1(1 * 3600_000L),
    HOURS_3(3 * 3600_000L),
    HOURS_6(6 * 3600_000L),
    HOURS_12(12 * 3600_000L),
    HOURS_24(24 * 3600_000L),
    HOURS_48(48 * 3600_000L),

    /** A whole number of days chosen by the user; the count lives in [EpgRefresh.manualDays]. */
    MANUAL,
}

/**
 * An EPG source's auto-refresh selection: the mode, plus the day count only [EpgAutoRefresh.MANUAL]
 * uses. The shape, the serialization and the bounds are [PlaylistRefresh]'s, deliberately — the two
 * screens ask the same question and should not answer it differently.
 *
 * Stored as one string per source, so backup, restore and the pickers need learn nothing about a
 * second value: `"OFF"`, `"HOURS_48"`, or `"MANUAL:10"`.
 */
data class EpgRefresh(
    val mode: EpgAutoRefresh = EpgAutoRefresh.OFF,
    val manualDays: Int = PlaylistRefresh.DEFAULT_MANUAL_DAYS,
) {
    /** How stale the guide may get before it is refreshed; null when it never refreshes on a timer. */
    val thresholdMs: Long?
        get() = if (mode == EpgAutoRefresh.MANUAL) {
            manualDays.coerceIn(PlaylistRefresh.MIN_MANUAL_DAYS, PlaylistRefresh.MAX_MANUAL_DAYS) * 24 * 3600_000L
        } else {
            mode.thresholdMs
        }

    fun serialize(): String =
        if (mode == EpgAutoRefresh.MANUAL) "${mode.name}$SEPARATOR$manualDays" else mode.name

    companion object {
        private const val SEPARATOR = ':'

        val OFF = EpgRefresh(EpgAutoRefresh.OFF)

        /**
         * Reads a stored value.
         *
         * The old format was a bare enum name and the new one is a superset of it, so every existing
         * selection parses to exactly what it meant before and **nothing is ever rewritten**. An
         * unrecognised value falls back to OFF, which is also what a source that has never been
         * configured gets — new sources do not auto-refresh until the user asks them to.
         */
        fun parse(raw: String?): EpgRefresh {
            val value = raw?.trim().orEmpty()
            val mode = runCatching {
                EpgAutoRefresh.valueOf(value.substringBefore(SEPARATOR))
            }.getOrDefault(EpgAutoRefresh.OFF)
            val days = value.substringAfter(SEPARATOR, "").toIntOrNull()
                ?.coerceIn(PlaylistRefresh.MIN_MANUAL_DAYS, PlaylistRefresh.MAX_MANUAL_DAYS)
                ?: PlaylistRefresh.DEFAULT_MANUAL_DAYS
            return EpgRefresh(mode, days)
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_settings")

/**
 * The positions written while the user browses — last channel, last category per section — kept apart
 * from the settings file. Each one used to be a write to `owntv_settings`, and every write there
 * re-runs every settings collector in the player and every screen. Copied over once, on first open.
 */
private val Context.positionStore: DataStore<Preferences> by preferencesDataStore(
    name = "owntv_positions",
    produceMigrations = { context -> listOf(PositionsFromSettings(context)) },
)

private class PositionsFromSettings(private val context: Context) : androidx.datastore.core.DataMigration<Preferences> {
    private val moved = booleanPreferencesKey("moved_from_settings")

    override suspend fun shouldMigrate(currentData: Preferences) = currentData[moved] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val old = context.dataStore.data.first()
        return currentData.toMutablePreferences().apply {
            for (key in SettingsRepository.Keys.POSITIONS) {
                @Suppress("UNCHECKED_CAST")
                val k = key as Preferences.Key<Any>
                old[k]?.let { this[k] = it }
            }
            this[moved] = true
        }.toPreferences()
    }

    // The old values stay in the settings file, unread; nothing is deleted on the way.
    override suspend fun cleanUp() = Unit
}

/** CH+- key paging limits. Top-level so any caller (VM, UI) can reference them via the class. */
object ChNavLimits {
    /** Hard cap for the CH+- skip counts — protects against typos (e.g. 999999) overloading slow TVs. */
    const val HARD_MAX = 1000
    /** Above this value the settings UI shows an advisory warning (high skips overshoot short lists). */
    const val WARN_THRESHOLD = 50
    /** Default per-direction skip (single CH+/- press moves this many items). */
    const val DEFAULT_SKIP = 10
}

/** Seek step choices. Top-level so the settings UI and the defaults share one list. */
object SeekSteps {
    /** Rewind/forward inside a movie or episode — the player's long-standing 10 s. */
    const val DEFAULT_SEEK_STEP_SEC = 10
    val SEEK_CHOICES = listOf(5, 10, 15, 30, 60)
}

/**
 * Persists app-level preferences in the settings file. The app has one user: every value below is
 * simply the value, no per-profile copies.
 */
class SettingsRepository(private val context: Context, private val localeStore: LocaleStore) :
    CustomizePinSettings by CustomizePinStore(context.dataStore) {

    /** Result of importing the optional locale field without allowing bad backup data to abort the restore. */
    data class SettingsImportResult(
        val localePresent: Boolean = false,
        val localeTag: String? = null,
        val invalidLocale: Boolean = false,
    )

    /**
     * Every settings flow below is derived through this.
     *
     * DataStore emits the **whole** `Preferences` object to **every** collector on **every** write,
     * so without the `distinctUntilChanged` a single toggle re-ran ~100 `map { }` lambdas and pushed
     * ~100 "new" StateFlow values app-wide — each one a potential recomposition — even though only
     * one key had actually changed. One operator here stops that propagation for all of them at
     * once, which is why this is a helper rather than 74 hand-edited call sites.
     */
    private fun <T> prefsFlow(transform: (Preferences) -> T): Flow<T> =
        context.dataStore.data.map(transform).distinctUntilChanged()

    /** One stored string, parsed only when *it* changes — not on every write to the settings file, which
     *  is what [prefsFlow] costs a JSON or list parser. */
    private fun <V, T> parsedFlow(key: Preferences.Key<V>, parse: (V?) -> T): Flow<T> =
        context.dataStore.data.map { it[key] }.distinctUntilChanged().map(parse)

    private fun <T> positionFlow(key: Preferences.Key<T>): Flow<T?> =
        context.positionStore.data.map { it[key] }.distinctUntilChanged()

    // Glass is off until it is set (a fresh install's Stage defaults turn it on).
    private val GLASS_SCOPE_DEFAULT_BITS: Int = 0
    private val NAV_WIDEN_OFF = "OFF"

    // Internal (not private) only so SettingsBackupCoverageTest can check every key has a backup decision.
    internal object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val UI_ZOOM_PCT = intPreferencesKey("ui_zoom_percent")
        val FONT_SIZE_PCT = intPreferencesKey("font_size_percent")
        val POPUP_FONT_SIZE_PCT = intPreferencesKey("popup_font_size_percent")
        val POPUP_SIZE_PCT = intPreferencesKey("popup_size_percent")
        val MAIN_FONT_FAMILY = stringPreferencesKey("main_font_family")
        val POPUP_FONT_FAMILY = stringPreferencesKey("popup_font_family")
        val ACCENT = stringPreferencesKey("accent_color")
        val ACCENT_CUSTOM = stringPreferencesKey("accent_custom")
        val FOCUS_HIGHLIGHT = stringPreferencesKey("focus_highlight_color")
        val FOCUS_HIGHLIGHT_WIDTH = intPreferencesKey("focus_highlight_width")
        // The one user's startup landing and Home layout (plain keys; the app has
        // no profiles, so these are simply *the* values).
        val STARTUP_MODE = stringPreferencesKey("startup_mode")
        val STARTUP_CHANNEL = stringPreferencesKey("startup_channel")
        val HOME_CONFIG = stringPreferencesKey("home_config")
        val HIDE_NEW_CATEGORIES = booleanPreferencesKey("hide_new_categories")
        // The 4×2 sync matrix (phase ③): per category, whether this device gives its rows and
        // whether it reads the others'. Local DataStore only — these ticks are never synced.
        val SYNC_GIVE_FAVORITES = booleanPreferencesKey("sync_give_favorites")
        val SYNC_READ_FAVORITES = booleanPreferencesKey("sync_read_favorites")
        val SYNC_GIVE_ORDER = booleanPreferencesKey("sync_give_order")
        val SYNC_READ_ORDER = booleanPreferencesKey("sync_read_order")
        val SYNC_GIVE_HISTORY = booleanPreferencesKey("sync_give_history")
        val SYNC_READ_HISTORY = booleanPreferencesKey("sync_read_history")
        val SYNC_GIVE_REMINDERS = booleanPreferencesKey("sync_give_reminders")
        val SYNC_READ_REMINDERS = booleanPreferencesKey("sync_read_reminders")
        val REFRESH_SOURCE_IDS = stringSetPreferencesKey("refresh_source_ids")
        // Per-source auto-refresh selections (JSON maps: { "<sourceId>": "<EnumName>" }). Replace the
        // binary refresh-on-startup set with Off/Startup + staleness thresholds. Migration-safe: the legacy
        // REFRESH_SOURCE_IDS set is read once (see migrateLegacyRefreshFlags) then ignored.
        val PLAYLIST_AUTO_REFRESH = stringPreferencesKey("playlist_auto_refresh")
        val EPG_AUTO_REFRESH = stringPreferencesKey("epg_auto_refresh")
        // Per-EPG-source: use that feed's own <icon src> channel logos instead of the playlist's.
        val EPG_USE_LOGOS = stringPreferencesKey("epg_use_logos")
        val REFRESH_MIGRATED = booleanPreferencesKey("refresh_migration_done")
        val EPG_REFILL_CHECKED = booleanPreferencesKey("epg_refill_checked")
        // Set while a backup restore is applying, cleared only when it completes. A value that
        // survives to the next launch means the restore was interrupted and may be half-applied.
        val RESTORE_IN_PROGRESS = stringPreferencesKey("restore_in_progress")
        val LIVE_PREVIEW = booleanPreferencesKey("live_preview")
        val LIVE_PREVIEW_AUDIO = booleanPreferencesKey("live_preview_audio")
        // OK on a channel first plays it in the preview pane; OK again goes full screen. One switch
        // per screen: the TV Guide (on by default, its behaviour before the switch existed) and
        // Live TV (off by default — focus auto-previews there instead).
        val GUIDE_PREVIEW_ON_OK = booleanPreferencesKey("guide_preview_on_ok")
        val LIVE_PREVIEW_ON_OK = booleanPreferencesKey("live_preview_on_ok")
        // Whether the expanded Home hero plays its video. Unset means "device default" — see
        // heroPreviewEnabled.
        val HERO_PREVIEW = booleanPreferencesKey("hero_preview")
        // Docked mini-player: size (% of screen width) and screen corner/edge.
        val MINI_PLAYER_SIZE_PCT = intPreferencesKey("mini_player_size_pct")
        val MINI_PLAYER_POSITION = stringPreferencesKey("mini_player_position")
        // Live TV latency: preset name + the custom seconds used when the preset is CUSTOM.
        val LIVE_LATENCY_MODE = stringPreferencesKey("live_latency_mode")
        val LIVE_LATENCY_CUSTOM_SECS = intPreferencesKey("live_latency_custom_secs")
        val LIVE_PREROLL_SECS = intPreferencesKey("live_preroll_secs")
        // How long a live channel may take to produce a picture before it is called dead. 0 = never.
        val LIVE_TUNE_TIMEOUT_SECS = intPreferencesKey("live_tune_timeout_secs")
        // These lived on the built-in source's row; the row is gone (schema v5), so the last
        // successful catalogue sync and the "clean channel names" choice live here.
        val SOURCE_LAST_SYNC_AT = longPreferencesKey("source_last_sync_at")
        val CHANNEL_CLEANUP_ENABLED = booleanPreferencesKey("channel_cleanup_enabled")
        val CHANNEL_CLEANUP_STRIP = stringPreferencesKey("channel_cleanup_strip")
        // v4.1.6 one-shot: reset live latency to the safe Balanced preset. Subsequent user changes are
        // preserved across every later update.
        val LIVE_LATENCY_RESET_416 = booleanPreferencesKey("live_latency_reset_416")
        // Stage (2026-09-30): moved a saved System Sans to Plus Jakarta Sans once. Device-local, never backed up.
        val FONT_JAKARTA_MIGRATED = booleanPreferencesKey("font_jakarta_migrated")
        val LIST_ORDERS_RESET = booleanPreferencesKey("list_orders_reset")
        // Stage (2026-10-02): the TV app's new-UI defaults written once (applyStageDefaults). Device-local.
        val STAGE_DEFAULTS_APPLIED = booleanPreferencesKey("stage_defaults_applied")
        val PREFER_SDR = booleanPreferencesKey("prefer_sdr")
        // Auto frame rate for live TV.
        val AUTO_FRAME_RATE_LIVE = booleanPreferencesKey("auto_frame_rate_live")
        val AUDIO_PASSTHROUGH = booleanPreferencesKey("audio_passthrough")
        val NIGHT_MODE = booleanPreferencesKey("night_mode")
        val VOLUME_LEVELLING = booleanPreferencesKey("volume_levelling")
        val MAX_VIDEO_HEIGHT = intPreferencesKey("max_video_height")
        val MOBILE_DATA_MAX_VIDEO_HEIGHT = intPreferencesKey("mobile_data_max_video_height")
        val TUNNELED_PLAYBACK = booleanPreferencesKey("tunneled_playback")
        val TUNNELING_FAILED = booleanPreferencesKey("tunneling_failed")
        // v4.1.6 one-shot: AFR caused visible HDMI re-handshakes on some TVs. Existing installs are
        // forced Off once; subsequent user changes are preserved across every later update.
        val AUTO_FRAME_RATE_RESET_416 = booleanPreferencesKey("auto_frame_rate_reset_416")
        // v4.2.0 one-shot: below Android 12 the app cannot tell a seamless refresh-rate switch from one
        // that blanks the panel, so AFR is forced Off once on those devices. Set on every device (so the
        // migration and the [autoFrameRateLive] gate below both settle), but only clears AFR on pre-12.
        val AUTO_FRAME_RATE_RESET_PRE12 = booleanPreferencesKey("auto_frame_rate_reset_pre12")

        /** The one-time "this stream judders at your TV's refresh rate" suggestion has been answered. */
        val AUTO_FRAME_RATE_PROMPTED = booleanPreferencesKey("auto_frame_rate_prompted")
        val ANDROID_TV_HOME = booleanPreferencesKey("android_tv_home")

        // Multiview: watching up to four live channels at once.
        val MULTIVIEW_ENABLED = booleanPreferencesKey("multiview_enabled")
        val MULTIVIEW_TILES = intPreferencesKey("multiview_tiles")
        /** The "3 or 4 tiles may be more than this device or your provider can do" warning was accepted. */
        val MULTIVIEW_WARNING_ACCEPTED = booleanPreferencesKey("multiview_warning_accepted")
        // Recording. Recordings and multiview spend the same connections.
        // Video Player Settings
        val HW_DECODING = booleanPreferencesKey("hw_decoding")
        val SOFTWARE_DECODE_ABOVE_1080P = booleanPreferencesKey("software_decode_above_1080p")
        val MEASURED_STREAM_STATS = booleanPreferencesKey("measured_stream_stats")
        val DETAILED_DIAGNOSTICS = booleanPreferencesKey("detailed_diagnostics")
        val DIRECT_TUNE = booleanPreferencesKey("direct_tune")
        val SURROUND_SOUND = booleanPreferencesKey("surround_sound")
        val SURROUND_MODE = stringPreferencesKey("surround_mode")
        val VIDEO_SURFACE_MODE = stringPreferencesKey("video_surface_mode")
        val EXTERNAL_PLAYER_LIVE = booleanPreferencesKey("external_player_live")
        val DEFAULT_ZOOM = stringPreferencesKey("default_zoom")
        val DEFAULT_VOLUME = intPreferencesKey("default_volume")
        val SEEK_STEP_SEC = intPreferencesKey("seek_step_sec")
        /** Legacy single subtitle size, superseded by the ExoPlayer key below but still read as its
         *  default so an existing size survives an upgrade — and still written by nothing. */
        val SUB_SCALE = floatPreferencesKey("sub_scale")
        val SUB_SCALE_EXO = floatPreferencesKey("sub_scale_exo")
        // Subtitle appearance: off by default so every renderer keeps its stock look —
        // notably the embedded broadcaster styling of Live TV CEA-608/teletext cues.
        val SUB_STYLE_ENABLED = booleanPreferencesKey("sub_style_enabled")
        val SUB_FONT = stringPreferencesKey("sub_font")
        val SUB_COLOR = stringPreferencesKey("sub_color")
        val SUB_POSITION = stringPreferencesKey("sub_position")
        val SUB_BG_OPACITY = intPreferencesKey("sub_bg_opacity")
        val AUDIO_DELAY_MS = intPreferencesKey("audio_delay_ms")
        val AUDIO_DELAY_BY_OUTPUT = stringPreferencesKey("audio_delay_by_output")
        val PREF_AUDIO_LANG = stringPreferencesKey("pref_audio_lang")
        val PREF_SUB_LANG = stringPreferencesKey("pref_sub_lang")
        // Settings rows pinned to the Quick group, comma-joined, in display order.
        val QUICK_PINNED = stringPreferencesKey("settings_quick_pinned")
        // Per-section list sorting ("PLAYLIST" or "ALPHA")
        val SORT_LIVE = stringPreferencesKey("sort_live")
        val SORT_GUIDE = stringPreferencesKey("sort_guide")
        val GUIDE_SHOW_EMPTY = booleanPreferencesKey("guide_show_empty")
        val GUIDE_PREVIEW = booleanPreferencesKey("guide_preview")
        val UPDATE_CHECK_ON_START = booleanPreferencesKey("update_check_on_start")
        val EPG_OFFSET_MIN = intPreferencesKey("epg_offset_minutes")
        val GUIDE_DAYS_TO_KEEP = intPreferencesKey("guide_days_to_keep")
        val ANIMATION_LEVEL = stringPreferencesKey("animation_level")
        val AMBIENT_GLOW_ENABLED = booleanPreferencesKey("ambient_glow_enabled")
        val AMBIENT_GLOW_PULSE = booleanPreferencesKey("ambient_glow_pulse")
        val RESUME_LAST_CHANNEL = booleanPreferencesKey("resume_last_channel")
        val LAST_LIVE_CATEGORY = stringPreferencesKey("last_live_category")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val LAST_LIVE_CHANNEL = androidx.datastore.preferences.core.stringPreferencesKey("last_live_channel")
        val LIVE_LAYOUT = stringPreferencesKey("live_layout")
        val LIVE_VIEW = stringPreferencesKey("live_view")
        val REMINDER_MODE = stringPreferencesKey("reminder_mode")
        val REMINDER_LEAD_MIN = intPreferencesKey("reminder_lead_min")
        val GUIDE_VIEW = stringPreferencesKey("guide_view")
        val GUIDE_DENSITY_PCT = intPreferencesKey("guide_density_pct")
        // Touch-host settings. A television has no home button to shrink a video into, no metered
        // connection to save, and no swipe to calibrate — so all four default to the behaviour the TV
        // app already has, and only the phone app ever shows a row for them.
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val PIP_ENABLED = booleanPreferencesKey("pip_enabled")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val GESTURE_SENSITIVITY_PCT = intPreferencesKey("gesture_sensitivity_pct")
        // The mobility layer, all of it touch-host only: a window that floats over another app, a
        // screen that switches itself off, and a data plan that gets billed. A television has none of
        // the three, so every default below is what a TV does today — nothing.
        val MINI_PLAYER_STYLE = stringPreferencesKey("mini_player_style")
        val PIP_ON_BACK = booleanPreferencesKey("pip_on_back")
        val PIP_SIZE = stringPreferencesKey("pip_size")
        val PIP_SNAP = booleanPreferencesKey("pip_snap")
        val AUDIO_ON_SCREEN_OFF = booleanPreferencesKey("audio_on_screen_off")
        val AUDIO_ON_MOBILE_DATA = booleanPreferencesKey("audio_on_mobile_data")
        val AUDIO_PER_CHANNEL = booleanPreferencesKey("audio_per_channel")
        // Global proxy (Approach 1 — one app-wide HTTP proxy). HTTP only; no per-source override yet.
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USER = stringPreferencesKey("proxy_user")
        val PROXY_PASS = stringPreferencesKey("proxy_pass")
        // Global custom DNS — one app-wide DNS server (plain UDP or DoH). Sibling to global proxy.
        val DNS_ENABLED = booleanPreferencesKey("dns_enabled")
        val DNS_HOST = stringPreferencesKey("dns_host")
        val DNS_PORT = intPreferencesKey("dns_port")
        val DNS_DOH_URL = stringPreferencesKey("dns_doh_url")
        // Immersive fullscreen (phones/tablets): hide the system status + navigation bars
        // (SmartRuTube-style). A television never has the bars, so it simply ignores this.
        val FULLSCREEN_MODE = booleanPreferencesKey("fullscreen_mode")
        // Weather chip: show/hide + manual location override (blank = auto-detect from public IP).
        val WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        val WEATHER_LOCATION = stringPreferencesKey("weather_location")
        val WEATHER_FAHRENHEIT = booleanPreferencesKey("weather_fahrenheit")
        val CLOCK_TIME_COLOR = stringPreferencesKey("clock_time_color")
        val CLOCK_DATE_COLOR = stringPreferencesKey("clock_date_color")
        val CLOCK_WEATHER_COLOR = stringPreferencesKey("clock_weather_color")
        // Nav menu customization (v4.3.0): DYNAMIC auto-adapts the side icons to what the active playlist
        // offers; STATIC lets the user hide specific icons. NAV_HIDDEN holds MainSection.name values the
        // user has hidden (STATIC mode only — DYNAMIC ignores it).
        val NAV_MENU_MODE = stringPreferencesKey("nav_menu_mode")
        // Chosen flip-card icon colour (AppIcon.name). What the launcher shows is AppIconSwitcher.applied.
        val APP_ICON = stringPreferencesKey("app_icon")
        val NAV_MENU_HIDDEN = stringSetPreferencesKey("nav_menu_hidden")
        // Stage navigation (TV): where the rail sits (NavStyle.name), what it shows (NavSize.name), its
        // length (NavLength.name), and how long the floating rail waits before hiding.
        val NAV_STYLE = stringPreferencesKey("nav_style")
        val NAV_SIZE = stringPreferencesKey("nav_size")
        val NAV_LENGTH = stringPreferencesKey("nav_length")
        val NAV_HIDE_AFTER_MS = intPreferencesKey("nav_hide_after_ms")
        // Docked + Compact: the size the rail opens to while it holds focus (NavSize.name), or "OFF".
        val NAV_WIDEN = stringPreferencesKey("nav_widen")
        // In-app logos: the play triangle in the user's accent instead of the icon's own colour.
        val BRAND_ACCENT_TRIANGLE = booleanPreferencesKey("brand_accent_triangle")
        // Phone bottom bar (not the TV rail keys — a different control): names under the icons, and
        // whether it slides away while a list scrolls down. Plus counts in the browse sheets.
        val PHONE_NAV_LABELS = booleanPreferencesKey("phone_nav_labels")
        val PHONE_NAV_HIDE_ON_SCROLL = booleanPreferencesKey("phone_nav_hide_on_scroll")
        val SHEET_COUNTS = booleanPreferencesKey("sheet_counts")
        // CH+- key paging for browse panels (Live/Movies/Series: category rail + item list/grid).
        // Master toggle + a per-direction skip count (CH+ toward first, CH− toward last). Counts are
        // clamped to [1, CH_NAV_HARD_MAX] on write; the UI warns above CH_NAV_WARN_THRESHOLD.
        val CH_NAV_ENABLED = booleanPreferencesKey("ch_nav_enabled")
        val CH_NAV_UP_SKIP = intPreferencesKey("ch_nav_up_skip")
        val CH_NAV_DOWN_SKIP = intPreferencesKey("ch_nav_down_skip")
        val REMOTE_SHORTCUT_BINDINGS = stringSetPreferencesKey("remote_shortcut_bindings")
        // Player buttons (PlayerRemoteOptions): unset means the player's own behaviour.
        val PLAYER_HOLD_OK = stringPreferencesKey("player_hold_ok")
        val PLAYER_SHORT_OK = stringPreferencesKey("player_short_ok")
        val PLAYER_SWAP_CHANNEL_KEYS = booleanPreferencesKey("player_swap_channel_keys")
        val PLAYER_NUMBER_KEYS_TUNE = booleanPreferencesKey("player_number_keys_tune")
        // Manual panel-width adjustment (v4.3.x): per section (Live/Movies/Series) a master toggle plus
        // one percentage per panel (category rail · item list/grid · preview). 100 = stock width; the
        // three are normalized across the row, so they always fill the screen. See PanelWidths.kt.
        val PANEL_W_LIVE_ON = booleanPreferencesKey("panel_w_live_on")
        val PANEL_W_LIVE_CAT = intPreferencesKey("panel_w_live_cat")
        val PANEL_W_LIVE_LIST = intPreferencesKey("panel_w_live_list")
        val PANEL_W_LIVE_PREVIEW = intPreferencesKey("panel_w_live_preview")
        val LIVE_STAGE_SHEET = intPreferencesKey("live_stage_sheet")
        val LIVE_STAGE_LIST = intPreferencesKey("live_stage_list")
        val LIVE_STAGE_PREVIEW = intPreferencesKey("live_stage_preview")
        // Cinematic only: how tall the detail block is, as a percentage of the screen. Deliberately
        // NOT part of the three panel-width shares — those add up to 100 across one row, and a
        // height has no business competing with two widths for the same budget.
        // Guide's two-column split (pinned channels · scrollable EPG timeline).
        val GUIDE_WIDTH_ON = booleanPreferencesKey("guide_width_on")
        val GUIDE_WIDTH_CHANNELS = intPreferencesKey("guide_width_channels")
        val GUIDE_WIDTH_EPG = intPreferencesKey("guide_width_epg")
        // "Browsing & lists" — two independent per-section toggles (Live TV / Movies / Series).
        //
        // REMEMBER_LAST_LIVE = remember last ITEM. OFF (default) = switching category resets the browse
        //                      list to the top; ON = each category keeps its own scroll position. It
        //                      also gates lastLiveChannelId (the focused-channel restore on re-entry).
        // REMEMBER_CAT_LIVE  = remember last CATEGORY. ON (default) = reopening the section lands on the
        //                      category you left; OFF = always start on All.
        val REMEMBER_LAST_LIVE = booleanPreferencesKey("remember_last_live")
        val REMEMBER_CAT_LIVE = booleanPreferencesKey("remember_cat_live")
        // Live TV categories: show the built-in All channels entry. Default ON.
        val LIVE_SHOW_ALL_RAIL = booleanPreferencesKey("live_show_all_rail")
        val GUIDE_FOLLOWS_LIVE_CATEGORY = booleanPreferencesKey("guide_follows_live_category")

        /**
         * Written while browsing, so kept in their own store rather than the settings file. The one
         * user's remembered places — last focused channel, last category.
         */
        val POSITIONS: List<Preferences.Key<*>> = listOf(LAST_LIVE_CHANNEL, LAST_LIVE_CATEGORY)
        // Background picture (Glass & background). bg_image_path holds the absolute path of the image we
        // COPIED into app-private storage (so a USB unplug or source-folder delete never blanks it);
        // blank = no background (feature off, panels stay solid). glass_scope is Glass on (any bit set,
        // see GlassConfig.ON_BITS) or off (0); glass_alpha is the fill alpha in 0..100.
        val BG_IMAGE_PATH = stringPreferencesKey("bg_image_path")
        val GLASS_SCOPE = intPreferencesKey("glass_scope")
        val GLASS_ALPHA = intPreferencesKey("glass_alpha")
        // Glass & background (2 Oct): what sits behind every screen. Unset = the user's look before
        // this existed (see BackgroundConfig.resolve). Darken / Blur / Accent light are overrides of the
        // picture look's own defaults and are cleared when the look changes.
        val BG_STYLE = stringPreferencesKey("bg_style")
        val BG_LOOK = stringPreferencesKey("bg_look")
        val BG_DIM = intPreferencesKey("bg_dim")
        val BG_BLUR = intPreferencesKey("bg_blur")
        val BG_ACCENT_LIGHT = booleanPreferencesKey("bg_accent_light")

        // When the last successful backup was written, how big it was, and whether it was encrypted.
        // Nothing recorded this before: the Backup screen could say what a backup *would* contain but
        // never when one last happened, so "am I backed up?" had no answer anywhere in either app.
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_BYTES = longPreferencesKey("last_backup_bytes")
        val LAST_BACKUP_ENCRYPTED = booleanPreferencesKey("last_backup_encrypted")
        val LAST_BACKUP_PATH = stringPreferencesKey("last_backup_path")

        // When an update check last got an answer from GitHub (About's "Checked today, 20:12").
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
    }

    /**
     * The last successful backup — `null` until one has been written on this device.
     *
     * Deliberately not derived from the file on disk: the file lives wherever the user chose, often a
     * USB drive that is not plugged in, so its absence says nothing about whether a backup was taken.
     */
    data class LastBackup(val at: Long, val bytes: Long, val encrypted: Boolean, val path: String)

    val lastBackup: Flow<LastBackup?> = prefsFlow { prefs ->
        prefs[Keys.LAST_BACKUP_AT]?.takeIf { it > 0 }?.let { at ->
            LastBackup(
                at = at,
                bytes = prefs[Keys.LAST_BACKUP_BYTES] ?: 0L,
                encrypted = prefs[Keys.LAST_BACKUP_ENCRYPTED] ?: false,
                // Blank for a backup written before the path was recorded — the date is still true,
                // so the row is simply left out rather than the whole record thrown away.
                path = prefs[Keys.LAST_BACKUP_PATH].orEmpty(),
            )
        }
    }

    /** Called by [tv.own.owntv.core.backup.BackupManager] only after an export has actually landed. */
    suspend fun recordBackup(at: Long, bytes: Long, encrypted: Boolean, path: String) {
        context.dataStore.edit {
            it[Keys.LAST_BACKUP_AT] = at
            it[Keys.LAST_BACKUP_BYTES] = bytes
            it[Keys.LAST_BACKUP_ENCRYPTED] = encrypted
            it[Keys.LAST_BACKUP_PATH] = path
        }
    }

    /** The last update check that got an answer (up to date or a new version); `null` until one has. */
    val lastUpdateCheckAt: Flow<Long?> = prefsFlow { it[Keys.LAST_UPDATE_CHECK_AT]?.takeIf { at -> at > 0 } }

    /** Called by [tv.own.owntv.core.update.UpdateManager] only when a check succeeded. */
    suspend fun recordUpdateCheck(at: Long) {
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK_AT] = at }
    }

    // --- Live TV: remember the last focused channel so reopening lands focus back on it ---
    val lastLiveChannelId: Flow<String> = positionFlow(Keys.LAST_LIVE_CHANNEL).map { it.orEmpty() }
    suspend fun setLastLiveChannelId(id: String) {
        context.positionStore.edit { it[Keys.LAST_LIVE_CHANNEL] = id }
    }

    // --- Startup landing (v4.0.0). Falls back to the legacy global resume toggle for existing
    //     users (so "Resume last channel = On" keeps working until they pick a mode). ---
    fun startupMode(): Flow<StartupMode> = prefsFlow { prefs ->
        prefs[Keys.STARTUP_MODE]?.let { runCatching { StartupMode.valueOf(it) }.getOrNull() }
            ?: if (prefs[Keys.RESUME_LAST_CHANNEL] == true) StartupMode.LAST_CHANNEL else StartupMode.HOME
    }
    suspend fun setStartupMode(mode: StartupMode) {
        context.dataStore.edit { it[Keys.STARTUP_MODE] = mode.name }
    }

    fun startupChannel(): Flow<StartupChannelRef?> =
        parsedFlow(Keys.STARTUP_CHANNEL) { StartupChannelRef.fromJson(it) }

    suspend fun setStartupChannel(channel: StartupChannelRef?) {
        context.dataStore.edit { prefs ->
            if (channel == null) prefs.remove(Keys.STARTUP_CHANNEL) else prefs[Keys.STARTUP_CHANNEL] = channel.toJson().toString()
        }
    }

    suspend fun setSpecificStartupChannel(channel: StartupChannelRef) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STARTUP_CHANNEL] = channel.toJson().toString()
            prefs[Keys.STARTUP_MODE] = StartupMode.SPECIFIC_CHANNEL.name
        }
    }

    /** Whether a category the provider adds on a later resync is hidden automatically. Same across
     *  Live/Movies/Series — there's no reason to want it to differ by section. */
    fun hideNewCategoriesDefault(): Flow<Boolean> = prefsFlow { it[Keys.HIDE_NEW_CATEGORIES] ?: false }

    suspend fun setHideNewCategoriesDefault(hidden: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_NEW_CATEGORIES] = hidden }
    }

    // --- Home: row order / visibility / hero filters. ---
    fun homeConfig(): Flow<HomeConfig> = parsedFlow(Keys.HOME_CONFIG) { HomeConfig.fromJson(it) }

    suspend fun updateHomeConfig(transform: (HomeConfig) -> HomeConfig) {
        context.dataStore.edit { prefs ->
            val next = transform(HomeConfig.fromJson(prefs[Keys.HOME_CONFIG]))
            if (next == HomeConfig()) prefs.remove(Keys.HOME_CONFIG) else prefs[Keys.HOME_CONFIG] = next.toJson().toString()
        }
    }

    // --- Startup: auto-open the last-watched live channel (default OFF) — legacy, now migrated to startupMode ---
    val resumeLastChannel: Flow<Boolean> = prefsFlow { it[Keys.RESUME_LAST_CHANNEL] ?: false }
    suspend fun setResumeLastChannel(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESUME_LAST_CHANNEL] = enabled }
    }

    // --- Remember the last selected category so reopening a section lands where you left off.
    //     Written by each section's view model (debounced), read once on restore. ---
    val lastLiveCategory: Flow<String> = positionFlow(Keys.LAST_LIVE_CATEGORY).map { it ?: "" }
    suspend fun setLastLiveCategory(key: String) {
        context.positionStore.edit { it[Keys.LAST_LIVE_CATEGORY] = key }
    }

    // --- "TV Guide follows Live TV's category" (default off: the Guide keeps a category of its own). On,
    //     the Guide opens on Live TV's remembered category and a category picked there becomes it. ---
    val guideFollowsLiveCategory: Flow<Boolean> = prefsFlow { it[Keys.GUIDE_FOLLOWS_LIVE_CATEGORY] ?: false }
    suspend fun setGuideFollowsLiveCategory(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GUIDE_FOLLOWS_LIVE_CATEGORY] = enabled }
    }

    // --- Per-section "remember last CATEGORY" (default ON each — Live TV's long-standing behaviour,
    //     now also available for Movies/Series). OFF = the section always opens on All. ---
    val rememberCategoryLive: Flow<Boolean> = prefsFlow { it[Keys.REMEMBER_CAT_LIVE] ?: true }
    suspend fun setRememberCategoryLive(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_CAT_LIVE] = enabled }
    }

    // --- Built-in Live TV categories (default ON). ---
    val liveBuiltIns: Flow<tv.own.owntv.core.live.LiveBuiltIns> = prefsFlow {
        tv.own.owntv.core.live.LiveBuiltIns(showAll = it[Keys.LIVE_SHOW_ALL_RAIL] ?: true)
    }
    suspend fun setLiveShowAllRail(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_SHOW_ALL_RAIL] = enabled }
    }

    // --- Per-section "remember last ITEM per category" (default OFF each).
    //     OFF = switching category resets the browse list to the top; ON = each category keeps its own
    //     scroll position. The Live toggle additionally gates the lastLiveChannelId restore on re-entry. ---
    val rememberLastLive: Flow<Boolean> = prefsFlow { it[Keys.REMEMBER_LAST_LIVE] ?: false }
    suspend fun setRememberLastLive(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_LAST_LIVE] = enabled }
    }

    // --- Search: recent search terms (most-recent first, capped). Stored as one newline-joined string
    //     so no schema/table is needed; blank entries are ignored on read. ---
    val recentSearches: Flow<List<String>> = parsedFlow(Keys.RECENT_SEARCHES) { raw ->
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    /** Push a query to the top of the recents (case-insensitive dedup), capped at 12 entries. */
    suspend fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_SEARCHES]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val next = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(12)
            prefs[Keys.RECENT_SEARCHES] = next.joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it.remove(Keys.RECENT_SEARCHES) }
    }

    // --- Appearance: animation level (perf control for low-end boxes) ---
    val animationLevel: Flow<tv.own.owntv.core.theme.AnimationLevel> = prefsFlow { prefs ->
        prefs[Keys.ANIMATION_LEVEL]?.let { runCatching { tv.own.owntv.core.theme.AnimationLevel.valueOf(it) }.getOrNull() }
            ?: tv.own.owntv.core.theme.AnimationLevel.FULL
    }

    suspend fun setAnimationLevel(level: tv.own.owntv.core.theme.AnimationLevel) {
        context.dataStore.edit { it[Keys.ANIMATION_LEVEL] = level.name }
    }

    // --- Solid UI ambient radiance (the setup-wizard-style aura) ---
    // Opt-in: upgrading users keep the familiar solid appearance until they choose this effect.
    val ambientGlowEnabled: Flow<Boolean> = prefsFlow { it[Keys.AMBIENT_GLOW_ENABLED] ?: false }

    suspend fun setAmbientGlowEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AMBIENT_GLOW_ENABLED] = enabled }
    }

    val ambientGlowPulse: Flow<Boolean> = prefsFlow { it[Keys.AMBIENT_GLOW_PULSE] ?: true }

    suspend fun setAmbientGlowPulse(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AMBIENT_GLOW_PULSE] = enabled }
    }

    // --- Fullscreen mode: hide the system status + navigation bars (phones/tablets) ---

    /** Immersive fullscreen over the whole app (default ON; a television has no bars, so it is a no-op). */
    val fullscreenMode: Flow<Boolean> = prefsFlow { it[Keys.FULLSCREEN_MODE] ?: true }

    suspend fun setFullscreenMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FULLSCREEN_MODE] = enabled }
    }

    // --- Weather chip (top bar): show/hide + manual location override for VPN users ---

    /** Show the weather chip in the top bar (default ON). */
    val weatherEnabled: Flow<Boolean> = prefsFlow { it[Keys.WEATHER_ENABLED] ?: true }

    suspend fun setWeatherEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WEATHER_ENABLED] = enabled }
    }

    /**
     * Manual weather location. Blank (default) = auto-detect from public IP. Otherwise a city name
     * (geocoded via Open-Meteo) or a raw "lat,lon" pair. Lets users fix the wrong-city behaviour
     * they see behind a VPN, where IP geolocation resolves to the VPN server's city.
     */
    val weatherLocation: Flow<String> = prefsFlow { it[Keys.WEATHER_LOCATION] ?: "" }

    suspend fun setWeatherLocation(location: String) {
        context.dataStore.edit { it[Keys.WEATHER_LOCATION] = location.trim() }
    }

    /** Show the weather temperature in Fahrenheit instead of Celsius (default °C). */
    val weatherFahrenheit: Flow<Boolean> = prefsFlow { it[Keys.WEATHER_FAHRENHEIT] ?: false }

    suspend fun setWeatherFahrenheit(fahrenheit: Boolean) {
        context.dataStore.edit { it[Keys.WEATHER_FAHRENHEIT] = fahrenheit }
    }

    /**
     * Colours of the top-right clock's three parts (TV Settings › Appearance › Date, time & weather).
     * Each is blank (the shipped look), [ClockColors.ACCENT] (follows the accent) or a "#RRGGBB" hex.
     */
    val clockColors: Flow<ClockColors> = prefsFlow {
        ClockColors(it[Keys.CLOCK_TIME_COLOR] ?: "", it[Keys.CLOCK_DATE_COLOR] ?: "", it[Keys.CLOCK_WEATHER_COLOR] ?: "")
    }

    suspend fun setClockColor(part: ClockPart, value: String) {
        val key = when (part) {
            ClockPart.TIME -> Keys.CLOCK_TIME_COLOR
            ClockPart.DATE -> Keys.CLOCK_DATE_COLOR
            ClockPart.WEATHER -> Keys.CLOCK_WEATHER_COLOR
        }
        context.dataStore.edit { it[key] = value.trim() }
    }

    // --- EPG time offset ---

    /** How far the guide may be shifted, in minutes (±12/14 h, in 30-minute steps from the UI). */
    val epgOffsetRangeMinutes: IntRange = -12 * 60..14 * 60

    /**
     * Global guide shift, in minutes (0 = off). Some feeds publish their XMLTV in a timezone that
     * isn't the one the channels actually air in; this moves every programme by a fixed amount.
     * A per-channel override in the channel's long-press menu wins over this — that's what a
     * mixed East/West lineup on a single guide needs, since one global shift can only fix one half.
     */
    val epgOffsetMinutes: Flow<Int> = prefsFlow { it[Keys.EPG_OFFSET_MIN] ?: 0 }

    /**
     * How many days of guide to keep, forward from now.
     *
     * Replaces a hard-coded 48-hour horizon that threw away everything beyond two days even when the
     * provider had sent a fortnight — so the grid could only ever scroll two days, and two days after
     * the last sync the guide was simply empty. That is the "EPG stopped working" report, with
     * nothing actually broken.
     *
     * One value, read by three things that must agree: what the sync stores, what the prune keeps,
     * and how far the grid can scroll. Costs roughly 200,000 rows a day on a large lineup, which is
     * why the retention split landed first.
     */
    val guideDaysToKeep: Flow<Int> = prefsFlow {
        (it[Keys.GUIDE_DAYS_TO_KEEP] ?: GuideRetention.DEFAULT_DAYS)
            .coerceIn(GuideRetention.MIN_DAYS, GuideRetention.MAX_DAYS)
    }

    suspend fun setGuideDaysToKeep(days: Int) {
        context.dataStore.edit { it[Keys.GUIDE_DAYS_TO_KEEP] = days.coerceIn(GuideRetention.MIN_DAYS, GuideRetention.MAX_DAYS) }
    }

    suspend fun setEpgOffsetMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.EPG_OFFSET_MIN] = minutes.coerceIn(epgOffsetRangeMinutes) }
    }

    // Update checking is force-disabled and locked off: the app is not published anywhere a device
    // could pull a newer build from (apartment-only, sideloaded), so there is nothing to check and no
    // where to. The stored key is ignored and the setter is a no-op — the toggle can never be turned
    // on, and the startup check never runs. (Re-enable by restoring a prefsFlow read here.)
    val updateCheckOnStart: Flow<Boolean> = flowOf(false)

    @Suppress("UNUSED_PARAMETER")
    suspend fun setUpdateCheckOnStart(enabled: Boolean) {
        // Locked off — deliberately does nothing.
    }

    // --- Nav menu customization (v4.3.0) ---
    // DYNAMIC: the side icons adapt to what the active playlist actually contains (Home & Settings
    // always show; Live/Guide show when there are channels). STATIC: the user picks exactly which
    // icons to hide. Default STATIC (all visible) → existing users see no change until they opt in.

    enum class NavMenuMode { DYNAMIC, STATIC }

    val navMenuMode: Flow<NavMenuMode> = prefsFlow { prefs ->
        prefs[Keys.NAV_MENU_MODE]?.let { runCatching { NavMenuMode.valueOf(it) }.getOrNull() } ?: NavMenuMode.STATIC
    }

    suspend fun setNavMenuMode(mode: NavMenuMode) {
        context.dataStore.edit { it[Keys.NAV_MENU_MODE] = mode.name }
    }

    /** The icon and logo colour the user chose. It reaches the launcher on the next restart. */
    val appIcon: Flow<AppIcon> = prefsFlow { prefs -> AppIcon.fromStored(prefs[Keys.APP_ICON]) }

    suspend fun setAppIcon(icon: AppIcon) {
        context.dataStore.edit { it[Keys.APP_ICON] = icon.name }
    }

    /** Names of the `MainSection` browse items the user has hidden (STATIC mode). */
    val navMenuHidden: Flow<Set<String>> = prefsFlow { it[Keys.NAV_MENU_HIDDEN] ?: emptySet() }

    /** Replace the whole hidden set. Empty = all visible. */
    suspend fun setNavMenuHidden(hidden: Set<String>) {
        context.dataStore.edit { prefs ->
            if (hidden.isEmpty()) prefs.remove(Keys.NAV_MENU_HIDDEN) else prefs[Keys.NAV_MENU_HIDDEN] = hidden
        }
    }

    /**
     * Where the navigation rail sits. FLOATING floats over the content and always hides itself; DOCKED
     * (the default: Docked · Compact · Fit) stays on screen and the content makes room for it.
     */
    enum class NavStyle { FLOATING, DOCKED }

    /** What the rail shows: icons only, + names, + counts, + a details line under each name. */
    enum class NavSize { COMPACT, NORMAL, WIDE, EXTRA_WIDE }

    /** Whether the rail fits its items or runs the full height of the screen. */
    enum class NavLength { FIT, FULL }

    /** The floating rail's hide delays, in milliseconds, as the Navigation popup offers them. */
    object NavHideAfter {
        val CHOICES_MS = listOf(500, 1000, 2000, 3000, 4000, 5000)
        const val DEFAULT_MS = 2000
    }

    val navStyle: Flow<NavStyle> = prefsFlow { prefs ->
        prefs[Keys.NAV_STYLE]?.let { runCatching { NavStyle.valueOf(it) }.getOrNull() } ?: NavStyle.DOCKED
    }

    suspend fun setNavStyle(style: NavStyle) {
        context.dataStore.edit { it[Keys.NAV_STYLE] = style.name }
    }

    val navSize: Flow<NavSize> = prefsFlow { prefs ->
        prefs[Keys.NAV_SIZE]?.let { runCatching { NavSize.valueOf(it) }.getOrNull() } ?: NavSize.COMPACT
    }

    suspend fun setNavSize(size: NavSize) {
        context.dataStore.edit { it[Keys.NAV_SIZE] = size.name }
    }

    /**
     * Docked + Compact only: the size the rail opens to over the content while it holds focus, so the
     * names show without the content giving up room. null = Off (it stays icons only). Default Off
     * (a fresh install or an update gets the plain Docked rail).
     */
    val navWiden: Flow<NavSize?> = prefsFlow { prefs ->
        prefs[Keys.NAV_WIDEN]?.let { runCatching { NavSize.valueOf(it) }.getOrNull() }?.takeIf { it != NavSize.COMPACT }
    }

    suspend fun setNavWiden(size: NavSize?) {
        context.dataStore.edit { it[Keys.NAV_WIDEN] = size?.name ?: NAV_WIDEN_OFF }
    }

    /** Phone bottom bar: names under the icons (default) or icons only. */
    val phoneNavLabels: Flow<Boolean> = prefsFlow { it[Keys.PHONE_NAV_LABELS] ?: true }

    suspend fun setPhoneNavLabels(on: Boolean) {
        context.dataStore.edit { it[Keys.PHONE_NAV_LABELS] = on }
    }

    /** Phone bottom bar: slides away while a list scrolls down, back on the way up (default on). */
    val phoneNavHideOnScroll: Flow<Boolean> = prefsFlow { it[Keys.PHONE_NAV_HIDE_ON_SCROLL] ?: true }

    suspend fun setPhoneNavHideOnScroll(on: Boolean) {
        context.dataStore.edit { it[Keys.PHONE_NAV_HIDE_ON_SCROLL] = on }
    }

    /** Channel and title counts in the phone's browse sheets: playlists, categories (default on). */
    val sheetCounts: Flow<Boolean> = prefsFlow { it[Keys.SHEET_COUNTS] ?: true }

    suspend fun setSheetCounts(on: Boolean) {
        context.dataStore.edit { it[Keys.SHEET_COUNTS] = on }
    }

    /** In-app logos: the play triangle in the accent (default) or in the chosen icon's own colour. */
    val brandAccentTriangle: Flow<Boolean> = prefsFlow { it[Keys.BRAND_ACCENT_TRIANGLE] ?: true }

    suspend fun setBrandAccentTriangle(on: Boolean) {
        context.dataStore.edit { it[Keys.BRAND_ACCENT_TRIANGLE] = on }
    }

    val navLength: Flow<NavLength> = prefsFlow { prefs ->
        prefs[Keys.NAV_LENGTH]?.let { runCatching { NavLength.valueOf(it) }.getOrNull() } ?: NavLength.FIT
    }

    suspend fun setNavLength(length: NavLength) {
        context.dataStore.edit { it[Keys.NAV_LENGTH] = length.name }
    }

    /** How long the floating rail waits after focus leaves it before it hides; one of [NavHideAfter.CHOICES_MS]. */
    val navHideAfterMs: Flow<Int> = prefsFlow { prefs ->
        prefs[Keys.NAV_HIDE_AFTER_MS]?.takeIf { it in NavHideAfter.CHOICES_MS } ?: NavHideAfter.DEFAULT_MS
    }

    suspend fun setNavHideAfterMs(ms: Int) {
        if (ms in NavHideAfter.CHOICES_MS) context.dataStore.edit { it[Keys.NAV_HIDE_AFTER_MS] = ms }
    }

    // --- List sorting (per browse section) ---

    /** How a browse section's lists are ordered. */
    enum class SortMode { PLAYLIST, ALPHA, RATING, DATE_ADDED }

    /** Live defaults to the playlist/provider's own order — the natural grouping a user expects right
     *  after a sync. A–Z is one tap away (toggleSort). */
    val sortLive: Flow<SortMode> = prefsFlow { parseSort(it[Keys.SORT_LIVE], SortMode.PLAYLIST) }

    suspend fun setSortLive(mode: SortMode) {
        context.dataStore.edit { it[Keys.SORT_LIVE] = mode.name }
    }

    private fun parseSort(raw: String?, default: SortMode): SortMode =
        raw?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: default

    /** The TV Guide's own ordering. LIVE_TV mirrors the Live TV sort. */
    enum class GuideSort { ALPHA, PROVIDER, LIVE_TV, FAVORITES }

    /**
     * How the guide is drawn on a touch screen: the time grid, the "on now" list, or one channel's
     * schedule down the page. Unset means "decide from the screen" — a two-dimensional grid is
     * unusable on a portrait phone and the natural choice on a tablet, so a device that has never
     * been told otherwise picks per orientation rather than being locked to one answer.
     */
    enum class GuideView { GRID, ON_NOW, TIMELINE }
    val guideView: Flow<GuideView?> = prefsFlow { prefs ->
        prefs[Keys.GUIDE_VIEW]?.let { runCatching { GuideView.valueOf(it) }.getOrNull() }
    }
    suspend fun setGuideView(view: GuideView) {
        context.dataStore.edit { it[Keys.GUIDE_VIEW] = view.name }
    }

    /** Guide row height, as a percentage of the standard one. The phone's answer to the TV app's
     *  Guide Column Widths, which has no meaning where the channel column is a fixed strip. */
    val guideDensityPct: Flow<Int> = prefsFlow { it[Keys.GUIDE_DENSITY_PCT] ?: 100 }
    suspend fun setGuideDensityPct(pct: Int) {
        context.dataStore.edit { it[Keys.GUIDE_DENSITY_PCT] = pct.coerceIn(70, 130) }
    }

    /**
     * Live TV layout. STAGE (default for everyone, updates included) opens the categories as a sheet
     * with Left; SEPARATE keeps a category column on screen beside the channel list and preview.
     */
    enum class LiveLayout { STAGE, SEPARATE }
    val liveLayout: Flow<LiveLayout> = prefsFlow { prefs ->
        prefs[Keys.LIVE_LAYOUT]?.let { runCatching { LiveLayout.valueOf(it) }.getOrNull() } ?: LiveLayout.STAGE
    }
    suspend fun setLiveLayout(layout: LiveLayout) {
        context.dataStore.edit { it[Keys.LIVE_LAYOUT] = layout.name }
    }

    /** What Live TV opens in: the channel list, or the guide grid. Remembered from its toggle. */
    enum class LiveView { LIST, GUIDE }
    val liveView: Flow<LiveView> = prefsFlow { prefs ->
        prefs[Keys.LIVE_VIEW]?.let { runCatching { LiveView.valueOf(it) }.getOrNull() } ?: LiveView.LIST
    }
    suspend fun setLiveView(view: LiveView) {
        context.dataStore.edit { it[Keys.LIVE_VIEW] = view.name }
    }

    /**
     * What a programme reminder does when its moment comes: ask whether to switch, switch
     * straight away, or only say so. [reminderLeadMinutes] is how early that moment is.
     */
    enum class ReminderMode { ASK, SWITCH, NOTIFY }
    val reminderMode: Flow<ReminderMode> = prefsFlow { prefs ->
        prefs[Keys.REMINDER_MODE]?.let { runCatching { ReminderMode.valueOf(it) }.getOrNull() } ?: ReminderMode.ASK
    }
    suspend fun setReminderMode(mode: ReminderMode) {
        context.dataStore.edit { it[Keys.REMINDER_MODE] = mode.name }
    }
    val reminderLeadMinutes: Flow<Int> = prefsFlow { prefs ->
        prefs[Keys.REMINDER_LEAD_MIN]?.takeIf { it in tv.own.owntv.core.reminder.ReminderSchedule.LEAD_CHOICES } ?: tv.own.owntv.core.reminder.ReminderSchedule.DEFAULT_LEAD_MINUTES
    }
    suspend fun setReminderLeadMinutes(minutes: Int) {
        if (minutes in tv.own.owntv.core.reminder.ReminderSchedule.LEAD_CHOICES) context.dataStore.edit { it[Keys.REMINDER_LEAD_MIN] = minutes }
    }

    /** Keep the sound going after the app is left. On by default — it is why the service exists. */
    val backgroundPlayback: Flow<Boolean> = prefsFlow { it[Keys.BACKGROUND_PLAYBACK] ?: true }
    suspend fun setBackgroundPlayback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_PLAYBACK] = enabled }
    }

    /** Shrink the video into a floating window on home. On by default. */
    val pipEnabled: Flow<Boolean> = prefsFlow { it[Keys.PIP_ENABLED] ?: true }
    suspend fun setPipEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PIP_ENABLED] = enabled }
    }

    /**
     * Refuse to stream over a metered connection. Off by default: a user who has not asked for it
     * must never find their film blocked on a network that was working a moment ago.
     */
    val dataSaver: Flow<Boolean> = prefsFlow { it[Keys.DATA_SAVER] ?: false }
    suspend fun setDataSaver(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DATA_SAVER] = enabled }
    }

    /** How far a swipe travels before it has moved a slider, as a percentage of the default. */
    val gestureSensitivityPct: Flow<Int> = prefsFlow { it[Keys.GESTURE_SENSITIVITY_PCT] ?: 100 }
    suspend fun setGestureSensitivityPct(pct: Int) {
        context.dataStore.edit { it[Keys.GESTURE_SENSITIVITY_PCT] = pct.coerceIn(50, 200) }
    }

    suspend fun dataSaverNow(): Boolean = dataSaver.first()

    // --- The mobility layer (touch hosts only) ---------------------------------------------------

    /** What keeps playing while the user browses: a window they drag, a bar docked above the tabs, or
     *  nothing at all. */
    enum class MiniPlayerStyle { FLOATING, DOCKED, OFF }

    val miniPlayerStyle: Flow<MiniPlayerStyle> = prefsFlow { prefs ->
        prefs[Keys.MINI_PLAYER_STYLE]?.let { runCatching { MiniPlayerStyle.valueOf(it) }.getOrNull() }
            ?: MiniPlayerStyle.FLOATING
    }
    suspend fun setMiniPlayerStyle(style: MiniPlayerStyle) {
        context.dataStore.edit { it[Keys.MINI_PLAYER_STYLE] = style.name }
    }

    /** How big the floating window starts. The user pinches it from there. */
    enum class PipSize { SMALL, MEDIUM, LARGE }

    val pipSize: Flow<PipSize> = prefsFlow { prefs ->
        prefs[Keys.PIP_SIZE]?.let { runCatching { PipSize.valueOf(it) }.getOrNull() } ?: PipSize.MEDIUM
    }
    suspend fun setPipSize(size: PipSize) {
        context.dataStore.edit { it[Keys.PIP_SIZE] = size.name }
    }

    /** Let go of the floating window and it settles against the nearest edge. On by default — a window
     *  left mid-screen covers what the user is browsing. */
    val pipSnap: Flow<Boolean> = prefsFlow { it[Keys.PIP_SNAP] ?: true }
    suspend fun setPipSnap(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PIP_SNAP] = enabled }
    }

    /**
     * Drop the video decoder and keep the sound when the screen goes off. On by default, because it is
     * the single biggest saving this app can make on a phone battery, and turning it off only means
     * decoding frames nobody can see.
     */
    val audioOnScreenOff: Flow<Boolean> = prefsFlow { it[Keys.AUDIO_ON_SCREEN_OFF] ?: true }
    suspend fun setAudioOnScreenOff(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_ON_SCREEN_OFF] = enabled }
    }

    /** Start in sound-only when there is no Wi-Fi. Off by default: it changes what the user gets from
     *  a tap, and that is only ever their own choice. */
    val audioOnMobileData: Flow<Boolean> = prefsFlow { it[Keys.AUDIO_ON_MOBILE_DATA] ?: false }
    suspend fun setAudioOnMobileData(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_ON_MOBILE_DATA] = enabled }
    }

    /** Remember sound-only per channel, so a radio station opens without a picture next time and a
     *  television channel does not. On by default — the whole point is not having to ask twice. */
    val audioPerChannel: Flow<Boolean> = prefsFlow { it[Keys.AUDIO_PER_CHANNEL] ?: true }
    suspend fun setAudioPerChannel(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_PER_CHANNEL] = enabled }
    }

    /** Both read once, on the path that starts a stream and cannot wait on a flow. */
    suspend fun audioOnMobileDataNow(): Boolean = audioOnMobileData.first()
    suspend fun audioPerChannelNow(): Boolean = audioPerChannel.first()

    val sortGuide: Flow<GuideSort> = prefsFlow { prefs ->
        prefs[Keys.SORT_GUIDE]?.let { runCatching { GuideSort.valueOf(it) }.getOrNull() } ?: GuideSort.LIVE_TV
    }

    suspend fun setSortGuide(mode: GuideSort) {
        context.dataStore.edit { it[Keys.SORT_GUIDE] = mode.name }
    }

    /** The TV Guide also lists channels with no guide data (Order ▾ › Show), so they can be matched from there. */
    val guideShowEmpty: Flow<Boolean> = prefsFlow { it[Keys.GUIDE_SHOW_EMPTY] ?: true }
    suspend fun setGuideShowEmpty(show: Boolean) {
        context.dataStore.edit { it[Keys.GUIDE_SHOW_EMPTY] = show }
    }

    /** The TV Guide plays the focused channel in its preview pane (Order ▾ › Show); off shows the logo only. */
    val guidePreview: Flow<Boolean> = prefsFlow { it[Keys.GUIDE_PREVIEW] ?: true }
    suspend fun setGuidePreview(show: Boolean) {
        context.dataStore.edit { it[Keys.GUIDE_PREVIEW] = show }
    }

    // --- Video Player Settings ---

    /** Hardware decoding: hardware (MediaCodec) decoders first on the player. Off = software
     *  decoders first, for streams a vendor hardware decoder mishandles. */
    val hwDecoding: Flow<Boolean> = prefsFlow { it[Keys.HW_DECODING] ?: true }

    suspend fun setHwDecoding(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HW_DECODING] = enabled }
    }

    /**
     * Let software decoding play video above 1080p. Off by default: a TV-class CPU usually cannot keep
     * up with 4K in software, so playback above 1080p is stopped there with an honest error instead.
     * On, for devices with the CPU for it, the software rescue and the "Hardware decoding = Off"
     * path are allowed at any resolution.
     */
    val softwareDecodeAbove1080p: Flow<Boolean> = prefsFlow { it[Keys.SOFTWARE_DECODE_ABOVE_1080P] ?: false }

    suspend fun setSoftwareDecodeAbove1080p(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOFTWARE_DECODE_ABOVE_1080P] = enabled }
    }

    /** Measure live fps / bitrate / dropped frames for the stream-info overlay. On (default) = the
     *  overlay shows measured values that ExoPlayer doesn't declare for raw MPEG-TS. Off = a hard
     *  escape hatch: no live measuring runs at all (declared values only), for any low-end TV where
     *  the measuring is ever suspected of causing stutter. Never affects the actual playback pipeline.
     *  Defaults off on a low-RAM device, like [heroPreviewDefault]; auto frame rate measures fps on
     *  its own, so it does not depend on this. */
    val measuredStreamStats: Flow<Boolean> = prefsFlow { it[Keys.MEASURED_STREAM_STATS] ?: measuredStreamStatsDefault }

    /** What [measuredStreamStats] reports until the user picks; also the settings row's first value. */
    val measuredStreamStatsDefault: Boolean get() = !lowSpecDevice

    suspend fun setMeasuredStreamStats(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MEASURED_STREAM_STATS] = enabled }
    }

    /** Detailed playback logging. Off (default): the live engine's trace is kept in memory only, as it
     *  always was in a release build. On: the same trace is written to Logcat and to a bounded file, and
     *  it rides along with an exported report — the only way a normal user can produce a live trace at
     *  all, since a release build's diagnostics were previously compile-time off. */
    val detailedDiagnostics: Flow<Boolean> = prefsFlow { it[Keys.DETAILED_DIAGNOSTICS] ?: false }

    suspend fun setDetailedDiagnostics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DETAILED_DIAGNOSTICS] = enabled }
    }

    /** Type a provider channel number on the remote during full-screen live playback to jump straight
     *  to that channel. On (default). Off = number keys are ignored during playback, for anyone whose
     *  remote sends digits accidentally or who doesn't want the keys captured. */
    val directTune: Flow<Boolean> = prefsFlow { it[Keys.DIRECT_TUNE] ?: true }

    suspend fun setDirectTune(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DIRECT_TUNE] = enabled }
    }

    /** Hand live streams to an external player (VLC, MX Player) instead of the in-app engine.
     *  Off by default. */
    val externalPlayerLive: Flow<Boolean> = prefsFlow { it[Keys.EXTERNAL_PLAYER_LIVE] ?: false }

    suspend fun setExternalPlayerLive(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EXTERNAL_PLAYER_LIVE] = enabled }
    }

    /**
     * The three-state replacement for the old [Keys.SURROUND_SOUND] boolean (Auto / Stereo only / Surround).
     *
     * Multichannel output fails in two known ways. Flaky vendor audio HALs and HDMI-ARC chips claim 5.1
     * then mis-play it (drained 2× → "fast video, no sound"). And even when multichannel LPCM plays
     * correctly, the wider HDMI/ARC buffer adds latency the TV/soundbar doesn't report back, so audio lags
     * video (lip-sync drift). Dolby/DTS bitstreaming is the separate [audioPassthrough] switch.
     *
     * The old boolean was a poor fit for two reasons — a TV that mis-plays multichannel kept mis-playing
     * it on the decoder paths the switch never reached, and "off" is the wrong default to *have* to choose: a
     * user with a real receiver should get surround without reading a changelog, and a user whose TV
     * lies about its capabilities should get sound back without knowing why it went.
     *
     * Hence Auto by default — try multichannel, but fall back the instant the output is caught failing
     * (see `AudioOutputPolicy`). The fallback watchdog runs in **all three** modes,
     * including Surround: "no sound" is never what the user picked. Reading falls back to the old
     * boolean so nobody's explicit choice is lost, and nothing is rewritten on upgrade.
     */
    val surroundMode: Flow<SurroundMode> = prefsFlow {
        SurroundMode.of(it[Keys.SURROUND_MODE], it[Keys.SURROUND_SOUND])
    }

    suspend fun setSurroundMode(mode: SurroundMode) {
        context.dataStore.edit {
            it[Keys.SURROUND_MODE] = mode.name
            // Keep the legacy key consistent so a downgrade to 4.1.6 lands somewhere sane.
            it[Keys.SURROUND_SOUND] = mode == SurroundMode.SURROUND
        }
    }

    /** ExoPlayer may hand Dolby/DTS to the TV or receiver undecoded when the output claims it can
     *  play it (on, as before). Off: decoded in the app. */
    val audioPassthrough: Flow<Boolean> = prefsFlow { it[Keys.AUDIO_PASSTHROUGH] ?: true }

    suspend fun setAudioPassthrough(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUDIO_PASSTHROUGH] = enabled }
    }

    /** Night mode: loud passages are turned down and quiet ones up. Off by default. */
    val nightMode: Flow<Boolean> = prefsFlow { it[Keys.NIGHT_MODE] ?: false }

    suspend fun setNightMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NIGHT_MODE] = enabled }
    }

    /** Volume levelling: every channel and film is brought slowly to one loudness. Off by default. */
    val volumeLevelling: Flow<Boolean> = prefsFlow { it[Keys.VOLUME_LEVELLING] ?: false }

    suspend fun setVolumeLevelling(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOLUME_LEVELLING] = enabled }
    }

    /** The choices for the highest picture played (lines; 0 = Auto, no limit). */
    val maxVideoHeightChoices: List<Int> = listOf(0, 2160, 1080, 720, 480)

    /** The highest picture played when a stream offers several; 0 = Auto. */
    val maxVideoHeight: Flow<Int> = prefsFlow { (it[Keys.MAX_VIDEO_HEIGHT] ?: 0).takeIf { v -> v in maxVideoHeightChoices } ?: 0 }

    suspend fun setMaxVideoHeight(height: Int) {
        context.dataStore.edit { it[Keys.MAX_VIDEO_HEIGHT] = height.takeIf { v -> v in maxVideoHeightChoices } ?: 0 }
    }

    /** The choices for the mobile-data limit (lines; 0 = Off, the same as on Wi-Fi). */
    val mobileDataMaxVideoHeightChoices: List<Int> = listOf(0, 1080, 720, 480)

    /** The highest picture played on a metered connection (the phone's row); 0 = Off. */
    val mobileDataMaxVideoHeight: Flow<Int> =
        prefsFlow { (it[Keys.MOBILE_DATA_MAX_VIDEO_HEIGHT] ?: 0).takeIf { v -> v in mobileDataMaxVideoHeightChoices } ?: 0 }

    suspend fun setMobileDataMaxVideoHeight(height: Int) {
        context.dataStore.edit { it[Keys.MOBILE_DATA_MAX_VIDEO_HEIGHT] = height.takeIf { v -> v in mobileDataMaxVideoHeightChoices } ?: 0 }
    }

    /** Tunneled playback for live TV on ExoPlayer (experimental, off by default). */
    val tunneledPlayback: Flow<Boolean> = prefsFlow { it[Keys.TUNNELED_PLAYBACK] ?: false }

    /** True once tunneled playback failed on this device and switched itself off; the row says so until
     *  the user turns it on again. */
    val tunnelingFailed: Flow<Boolean> = prefsFlow { it[Keys.TUNNELING_FAILED] ?: false }

    suspend fun setTunneledPlayback(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.TUNNELED_PLAYBACK] = enabled
            if (enabled) it[Keys.TUNNELING_FAILED] = false
        }
    }

    /** The engine's first-failure switch-off. */
    suspend fun disableTunnelingAfterFailure() {
        context.dataStore.edit {
            it[Keys.TUNNELED_PLAYBACK] = false
            it[Keys.TUNNELING_FAILED] = true
        }
    }

    /**
     * How the video picture reaches the screen. [VideoSurface.SURFACE_VIEW] — the default — punches a
     * hole in the app's window that the display hardware fills directly: 4K, HDR and the cheapest
     * path, but a television has only so many hardware video planes, and what the hardware composes
     * itself is exactly what a screenshot or a screen recording may miss (a GPU-less stand shows a
     * black frame there too). [VideoSurface.TEXTURE_VIEW] draws the picture through an ordinary view
     * in the GPU-composited hierarchy, so it always appears in captures and wherever several surfaces
     * must show at once — at the cost of the hardware-overlay path.
     */
    enum class VideoSurface { SURFACE_VIEW, TEXTURE_VIEW }
    val videoSurfaceMode: Flow<VideoSurface> = prefsFlow { prefs ->
        prefs[Keys.VIDEO_SURFACE_MODE]?.let { runCatching { VideoSurface.valueOf(it) }.getOrNull() }
            ?: VideoSurface.SURFACE_VIEW
    }
    suspend fun setVideoSurfaceMode(mode: VideoSurface) {
        context.dataStore.edit { it[Keys.VIDEO_SURFACE_MODE] = mode.name }
    }

    /** Default zoom/aspect mode applied when playback starts (a `ZoomMode` name). */
    val defaultZoom: Flow<String> = prefsFlow { it[Keys.DEFAULT_ZOOM] ?: "FIT" }

    suspend fun setDefaultZoom(name: String) {
        context.dataStore.edit { it[Keys.DEFAULT_ZOOM] = name }
    }

    /**
     * Volume percent every item starts at (100 = untouched, up to the 150 boost ceiling). A film the
     * user has individually turned up overrides this — see
     * [tv.own.owntv.core.player.PlaybackPrefsStore].
     */
    val defaultVolume: Flow<Int> = prefsFlow { (it[Keys.DEFAULT_VOLUME] ?: 100).coerceIn(0, 150) }

    suspend fun setDefaultVolume(percent: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_VOLUME] = percent.coerceIn(0, 150) }
    }

    /**
     * How far the player's rewind/forward buttons and the seek bar's ◀/▶ jump in a movie or episode.
     */
    val seekStepSec: Flow<Int> = prefsFlow { it[Keys.SEEK_STEP_SEC] ?: SeekSteps.DEFAULT_SEEK_STEP_SEC }

    suspend fun setSeekStepSec(seconds: Int) {
        context.dataStore.edit { it[Keys.SEEK_STEP_SEC] = seconds }
    }

    // --- Subtitle appearance: size, text color, screen position, background transparency ---
    // Two levels of opt-in. The master toggle gates everything: while it's off NOTHING here is
    // applied and every renderer keeps its stock look (the overlay's hardcoded 45%
    // box, and — the case that matters most — SubtitleView's embedded broadcaster styling).
    // Each option then has its own "Default" value, so turning the toggle ON still changes nothing
    // until the user picks something: only the options actually set reach a renderer.

    // The size key falls back to the legacy single [Keys.SUB_SCALE] until it is set, so an upgrade
    // keeps the size the user already chose and nothing changes until they move it.

    /** Subtitle scale multiplier for the Media3 SubtitleView; [SubtitleStyle.SCALE_DEFAULT] = untouched. */
    val subtitleScaleExo: Flow<Float> = prefsFlow {
        it[Keys.SUB_SCALE_EXO] ?: it[Keys.SUB_SCALE] ?: SubtitleStyle.SCALE_DEFAULT
    }

    suspend fun setSubtitleScaleExo(scale: Float) {
        context.dataStore.edit { it[Keys.SUB_SCALE_EXO] = scale }
    }

    /**
     * Master toggle for the custom subtitle look; off = stock rendering everywhere.
     *
     * Unset defaults to *on* for anyone who had already changed the subtitle size back when it was
     * a standalone setting — it lives under this toggle now, so defaulting to off would silently
     * revert their size on upgrade.
     */
    val subtitleStyleEnabled: Flow<Boolean> = prefsFlow { prefs ->
        prefs[Keys.SUB_STYLE_ENABLED] ?: SubtitleStyle.hasScale(prefs[Keys.SUB_SCALE] ?: SubtitleStyle.SCALE_DEFAULT)
    }

    suspend fun setSubtitleStyleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SUB_STYLE_ENABLED] = enabled }
    }

    /** Null leaves the renderer's own or embedded subtitle font untouched. */
    val subtitleFont: Flow<AppFontFamily?> = prefsFlow { prefs ->
        prefs[Keys.SUB_FONT]?.let { stored -> AppFontFamily.entries.firstOrNull { it.name == stored } }
    }

    suspend fun setSubtitleFont(font: AppFontFamily?) {
        context.dataStore.edit { prefs ->
            if (font == null) prefs.remove(Keys.SUB_FONT) else prefs[Keys.SUB_FONT] = font.name
        }
    }

    /** Subtitle text color as "#RRGGBB"; blank ([SubtitleStyle.COLOR_DEFAULT]) = untouched. */
    val subtitleColor: Flow<String> = prefsFlow { it[Keys.SUB_COLOR] ?: SubtitleStyle.COLOR_DEFAULT }

    suspend fun setSubtitleColor(hex: String) {
        context.dataStore.edit { it[Keys.SUB_COLOR] = hex.trim() }
    }

    /** One of six fixed screen anchors, or [SubtitleStyle.Position.DEFAULT] = untouched. */
    val subtitlePosition: Flow<SubtitleStyle.Position> =
        prefsFlow { SubtitleStyle.Position.fromKey(it[Keys.SUB_POSITION]) }

    suspend fun setSubtitlePosition(position: SubtitleStyle.Position) {
        context.dataStore.edit { it[Keys.SUB_POSITION] = position.key }
    }

    /** Subtitle background opacity 0..100 (0 = no box, 100 = solid); negative = untouched. */
    val subtitleBgOpacity: Flow<Int> = prefsFlow { it[Keys.SUB_BG_OPACITY] ?: SubtitleStyle.OPACITY_DEFAULT }

    suspend fun setSubtitleBgOpacity(pct: Int) {
        val value = if (pct < SubtitleStyle.OPACITY_MIN) SubtitleStyle.OPACITY_DEFAULT else SubtitleStyle.clampOpacity(pct)
        context.dataStore.edit { it[Keys.SUB_BG_OPACITY] = value }
    }

    /** Audio sync offset in milliseconds (ExoPlayer's audio clock); +ve delays audio. */
    val audioDelayMs: Flow<Int> = prefsFlow { it[Keys.AUDIO_DELAY_MS] ?: 0 }

    suspend fun setAudioDelayMs(ms: Int) {
        context.dataStore.edit { it[Keys.AUDIO_DELAY_MS] = ms }
    }

    /** Extra audio delay per kind of output, on top of [audioDelayMs] — see [tv.own.owntv.core.player.OutputAudioDelays]. */
    val outputAudioDelays: Flow<tv.own.owntv.core.player.OutputAudioDelays> =
        parsedFlow(Keys.AUDIO_DELAY_BY_OUTPUT, tv.own.owntv.core.player.OutputAudioDelays::parse)

    suspend fun setOutputAudioDelay(kind: tv.own.owntv.core.player.AudioOutputKind, ms: Int) {
        context.dataStore.edit {
            val next = tv.own.owntv.core.player.OutputAudioDelays.parse(it[Keys.AUDIO_DELAY_BY_OUTPUT]).with(kind, ms)
            it[Keys.AUDIO_DELAY_BY_OUTPUT] = next.encode()
        }
    }

    // --- CH+- key paging (browse panels): master toggle + per-direction skip counts ---
    // Clamped to [1, ChNavLimits.HARD_MAX] on write so an accidental huge value can never persist.
    val chNavEnabled: Flow<Boolean> = prefsFlow { it[Keys.CH_NAV_ENABLED] ?: true }
    suspend fun setChNavEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CH_NAV_ENABLED] = enabled }
    }
    /** CH+ skip count (jumps this many items toward the first item). */
    val chNavUpSkip: Flow<Int> = prefsFlow {
        (it[Keys.CH_NAV_UP_SKIP] ?: ChNavLimits.DEFAULT_SKIP).coerceIn(1, ChNavLimits.HARD_MAX)
    }
    suspend fun setChNavUpSkip(n: Int) {
        context.dataStore.edit { it[Keys.CH_NAV_UP_SKIP] = n.coerceIn(1, ChNavLimits.HARD_MAX) }
    }
    /** CH− skip count (jumps this many items toward the last item). */
    val chNavDownSkip: Flow<Int> = prefsFlow {
        (it[Keys.CH_NAV_DOWN_SKIP] ?: ChNavLimits.DEFAULT_SKIP).coerceIn(1, ChNavLimits.HARD_MAX)
    }
    suspend fun setChNavDownSkip(n: Int) {
        context.dataStore.edit { it[Keys.CH_NAV_DOWN_SKIP] = n.coerceIn(1, ChNavLimits.HARD_MAX) }
    }

    /** The player's remote-button options; see [PlayerRemoteOptions]. */
    val playerRemoteOptions: Flow<PlayerRemoteOptions> = prefsFlow(::readPlayerRemoteOptions)

    /** Changes the options from their stored values in one write, so two quick changes cannot undo each other. */
    suspend fun updatePlayerRemoteOptions(change: (PlayerRemoteOptions) -> PlayerRemoteOptions) {
        context.dataStore.edit { writePlayerRemoteOptions(it, change(readPlayerRemoteOptions(it))) }
    }

    /** Configurable remote shortcuts. An absent key means factory defaults; an empty set means none. */
    val remoteShortcutBindings: Flow<List<RemoteShortcutBinding>> = parsedFlow(Keys.REMOTE_SHORTCUT_BINDINGS) { raw ->
        if (raw != null) {
            RemoteShortcutBindings.decode(raw)
        } else {
            RemoteShortcutBindings.defaults
        }
    }

    suspend fun setRemoteShortcutBinding(binding: RemoteShortcutBinding) {
        context.dataStore.edit { prefs ->
            val current = if (Keys.REMOTE_SHORTCUT_BINDINGS in prefs) {
                RemoteShortcutBindings.decode(prefs[Keys.REMOTE_SHORTCUT_BINDINGS].orEmpty())
            } else {
                RemoteShortcutBindings.defaults
            }
            prefs[Keys.REMOTE_SHORTCUT_BINDINGS] =
                RemoteShortcutBindings.encode(RemoteShortcutBindings.replace(current, binding))
        }
    }

    suspend fun removeRemoteShortcutBinding(keyCode: Int, press: RemoteShortcutPress) {
        context.dataStore.edit { prefs ->
            val current = if (Keys.REMOTE_SHORTCUT_BINDINGS in prefs) {
                RemoteShortcutBindings.decode(prefs[Keys.REMOTE_SHORTCUT_BINDINGS].orEmpty())
            } else {
                RemoteShortcutBindings.defaults
            }
            prefs[Keys.REMOTE_SHORTCUT_BINDINGS] =
                RemoteShortcutBindings.encode(current.filterNot { it.keyCode == keyCode && it.press == press })
        }
    }

    suspend fun resetRemoteShortcutBindings() {
        context.dataStore.edit { it.remove(Keys.REMOTE_SHORTCUT_BINDINGS) }
    }

    // --- Manual panel widths: per browse section, a master toggle + one percentage per panel ---
    // While the toggle is off the screens keep their stock layout code path entirely, so the feature
    // can't affect anyone who never opens it. Percentages are clamped on both read and write.
    private fun panelOnKey(s: PanelSection) = Keys.PANEL_W_LIVE_ON
    private fun panelCategoryKey(s: PanelSection) = Keys.PANEL_W_LIVE_CAT
    private fun panelListKey(s: PanelSection) = Keys.PANEL_W_LIVE_LIST
    private fun panelPreviewKey(s: PanelSection) = Keys.PANEL_W_LIVE_PREVIEW

    private fun livePreviewPanelHidden(p: Preferences): Boolean =
        (p[Keys.PANEL_W_LIVE_ON] ?: false) &&
            (p[Keys.PANEL_W_LIVE_CAT] ?: 0) > 0 &&
            (p[Keys.PANEL_W_LIVE_LIST] ?: 0) > 0 &&
            p[Keys.PANEL_W_LIVE_PREVIEW] == 0

    fun panelWidthEnabled(s: PanelSection): Flow<Boolean> = prefsFlow { it[panelOnKey(s)] ?: false }

    /**
     * The section's three shares, or null when nothing has been saved yet (the dialog then seeds
     * itself from the stock layout). Read back through [balanceToTotal] so a value written by a
     * different build can never leave the row over- or under-filled.
     */
    fun panelShares(s: PanelSection): Flow<PanelShares?> = prefsFlow { p ->
        val category = p[panelCategoryKey(s)]
        val list = p[panelListKey(s)]
        val preview = p[panelPreviewKey(s)]
        if (category == null || list == null || preview == null || category <= 0 || list <= 0 || preview < 0) {
            null
        } else {
            val snappedPreview = PanelWidthLimits.snapPreview(preview)
            balanceToTotal(
                PanelShares(
                    PanelWidthLimits.snap(category),
                    PanelWidthLimits.snap(list, PanelWidthLimits.listMax(snappedPreview)),
                    snappedPreview,
                ),
            )
        }
    }

    /** "Okay" in the panel-width dialog — the toggle and all three shares land in one write. */
    suspend fun setPanelWidths(s: PanelSection, enabled: Boolean, shares: PanelShares) {
        val safe = balanceToTotal(shares)
        context.dataStore.edit {
            it[panelOnKey(s)] = enabled
            it[panelCategoryKey(s)] = safe.category
            it[panelListKey(s)] = safe.list
            it[panelPreviewKey(s)] = safe.preview
            if (s == PanelSection.LIVE && enabled && safe.preview == 0) {
                it[Keys.LIVE_PREVIEW] = false
            }
        }
    }

    /**
     * Live TV's Stage-layout widths ([LiveStageWidths]), or null when never saved. They share the
     * section's on/off toggle with [panelShares], which stays the Separate-panels layout's row.
     */
    val liveStageWidths: Flow<LiveStageWidths?> = prefsFlow { p ->
        val sheet = p[Keys.LIVE_STAGE_SHEET]
        val list = p[Keys.LIVE_STAGE_LIST]
        val preview = p[Keys.LIVE_STAGE_PREVIEW]
        if (sheet == null || list == null || preview == null) null
        else LiveStageWidths(sheet, list, preview).takeIf { it.isValid }
    }

    /** "Okay" in the Live TV panel-width dialog while the layout is Stage. An invalid set is ignored. */
    suspend fun setLiveStageWidths(enabled: Boolean, widths: LiveStageWidths) {
        if (!widths.isValid) return
        context.dataStore.edit {
            it[Keys.PANEL_W_LIVE_ON] = enabled
            it[Keys.LIVE_STAGE_SHEET] = widths.sheet
            it[Keys.LIVE_STAGE_LIST] = widths.list
            it[Keys.LIVE_STAGE_PREVIEW] = widths.preview
            // As for the Separate row: a hidden preview pane means no preview playback either.
            if (enabled && widths.preview == 0) it[Keys.LIVE_PREVIEW] = false
        }
    }

    // --- Guide column widths: toggle + two percentages that must total exactly 100 ---
    val guideWidthEnabled: Flow<Boolean> = prefsFlow { it[Keys.GUIDE_WIDTH_ON] ?: false }

    val guideWidthShares: Flow<GuideWidthShares?> = prefsFlow { prefs ->
        val channels = prefs[Keys.GUIDE_WIDTH_CHANNELS]
        val epg = prefs[Keys.GUIDE_WIDTH_EPG]
        if (channels == null || epg == null) null
        else normalizeGuideWidths(GuideWidthShares(channels, epg))
    }

    suspend fun setGuideWidths(enabled: Boolean, shares: GuideWidthShares) {
        if (!shares.isValid) return
        context.dataStore.edit {
            it[Keys.GUIDE_WIDTH_ON] = enabled
            it[Keys.GUIDE_WIDTH_CHANNELS] = shares.channels
            it[Keys.GUIDE_WIDTH_EPG] = shares.epg
        }
    }

    // The user's preferred track languages — one pair of values ("pref_audio_lang" / "pref_sub_lang").

    /** Preferred audio language (ISO code, the player's track preference); blank = no preference. */
    val preferredAudioLang: Flow<String> = prefsFlow { it[Keys.PREF_AUDIO_LANG] ?: "" }

    suspend fun setPreferredAudioLang(lang: String) {
        context.dataStore.edit { it[Keys.PREF_AUDIO_LANG] = lang }
    }

    /** Preferred subtitle language (ISO code); blank = no preference. */
    val preferredSubLang: Flow<String> = prefsFlow { it[Keys.PREF_SUB_LANG] ?: "" }

    suspend fun setPreferredSubLang(lang: String) {
        context.dataStore.edit { it[Keys.PREF_SUB_LANG] = lang }
    }

    /**
     * The Settings rows the user pinned to the Quick group, in the order they should appear.
     * Stored as a comma-joined list of row keys. Unknown keys are kept here but ignored when the list
     * is drawn, so a pin that belongs to a row hidden by the current theme survives.
     */
    val quickPinnedKeys: Flow<List<String>> = parsedFlow(Keys.QUICK_PINNED) { raw ->
        (raw ?: DEFAULT_QUICK_PINNED.joinToString(","))
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    suspend fun setQuickPinnedKeys(keys: List<String>) {
        context.dataStore.edit { it[Keys.QUICK_PINNED] = keys.joinToString(",") }
    }

    /**
     * How the user arranged the long-press channel menu: the order, as a comma-joined list of action
     * keys (empty means "as shipped"), and the actions they hid, the same way. Keys that no longer
     * exist are ignored on read and actions the order has never heard of are appended and shown,
     * which is what lets a later release add an action without it vanishing.
     */
    fun menuLayout(menu: ContentMenu): Flow<MenuLayout> =
        context.dataStore.data
            .map { it[menuOrderKey(menu)] to it[menuHiddenKey(menu)] }
            .distinctUntilChanged()
            .map { (order, hidden) -> MenuLayout(splitKeys(order), splitKeys(hidden).toSet()) }

    /** Saves [layout]'s order and hidden actions; an empty layout is the shipped menu again. */
    suspend fun setMenuLayout(menu: ContentMenu, layout: MenuLayout) {
        context.dataStore.edit {
            it[menuOrderKey(menu)] = layout.order.joinToString(",")
            it[menuHiddenKey(menu)] = layout.hidden.joinToString(",")
        }
    }

    private fun splitKeys(raw: String?) = (raw ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun menuOrderKey(menu: ContentMenu) = stringPreferencesKey("settings_menu_order_${menu.name.lowercase()}")
    private fun menuHiddenKey(menu: ContentMenu) = stringPreferencesKey("settings_menu_hidden_${menu.name.lowercase()}")

    // --- Per-source auto-refresh (Off / Startup / staleness threshold) ---
    // Stored as a JSON map { "<sourceId>": "<EnumName>" } in the owntv_settings DataStore — migration-safe
    // (Room uses destructive migrations, so anything that must survive a schema bump lives here). Reuses the
    // existing lastSyncAt columns (SourceEntity.lastSyncAt for playlists, EpgSource.lastSyncAt for EPG) as the
    // "last successful sync" timestamp; nothing new is stored for that.

    /** Per-source playlist auto-refresh selection. Missing ids default to [PlaylistRefresh.OFF]. */
    val playlistAutoRefresh: Flow<Map<Long, PlaylistRefresh>> =
        prefsFlow { prefs ->
            readRefreshMap(prefs[Keys.PLAYLIST_AUTO_REFRESH]).entries.mapNotNull { (key, value) ->
                key.toLongOrNull()?.let { it to PlaylistRefresh.parse(value) }
            }.toMap()
        }

    /** Per-source EPG auto-refresh selection. Missing ids default to [EpgAutoRefresh.OFF]. */
    val epgAutoRefresh: Flow<Map<Long, EpgRefresh>> =
        prefsFlow { prefs ->
            readRefreshMap(prefs[Keys.EPG_AUTO_REFRESH]).entries.mapNotNull { (key, value) ->
                key.toLongOrNull()?.let { it to EpgRefresh.parse(value) }
            }.toMap()
        }


    suspend fun setPlaylistAutoRefresh(sourceId: Long, refresh: PlaylistRefresh) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAYLIST_AUTO_REFRESH] = writeRefreshMap(readRefreshMap(prefs[Keys.PLAYLIST_AUTO_REFRESH]), sourceId, refresh.serialize())
        }
    }

    suspend fun setEpgAutoRefresh(sourceId: Long, refresh: EpgRefresh) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EPG_AUTO_REFRESH] = writeRefreshMap(readRefreshMap(prefs[Keys.EPG_AUTO_REFRESH]), sourceId, refresh.serialize())
        }
    }

    /**
     * One-time migration of the legacy binary `refresh_source_ids` set → per-source `STARTUP` entries.
     * Idempotent (guarded by [Keys.REFRESH_MIGRATED]) and non-overwriting: if the new
     * [playlistAutoRefresh] map is already non-empty (user picked a mode in the new UI, or a prior migration
     * ran), we only flip the flag and return — never clobbering existing selections.
     */
    suspend fun migrateLegacyRefreshFlags() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.REFRESH_MIGRATED] == true) return@edit
            val existing = readRefreshMap(prefs[Keys.PLAYLIST_AUTO_REFRESH])
            val legacyIds = prefs[Keys.REFRESH_SOURCE_IDS].orEmpty()
            // Only migrate into an empty map — never overwrite selections already made in the new UI.
            if (existing.isEmpty() && legacyIds.isNotEmpty()) {
                val migrated = legacyIds.associate { it to PlaylistAutoRefresh.STARTUP.name }
                prefs[Keys.PLAYLIST_AUTO_REFRESH] =
                    org.json.JSONObject(migrated).toString()
            }
            prefs[Keys.REFRESH_MIGRATED] = true
        }
    }

    /**
     * One-shot guard for the post-migration EPG refill.
     *
     * `MIGRATION_8_9` deletes every row in `epg_programmes` (it adds `contentHash` and a natural-key
     * unique index, which the old rows can't satisfy) and nothing schedules a re-fetch — so an
     * upgrading user was left with an empty guide until they happened to re-sync EPG by hand. This
     * runs the detection exactly once per install, which also covers users who passed through 8→9
     * long ago and are still sitting on an empty guide.
     */
    val epgRefillChecked: Flow<Boolean> = prefsFlow { it[Keys.EPG_REFILL_CHECKED] == true }

    suspend fun markEpgRefillChecked() {
        context.dataStore.edit { prefs -> prefs[Keys.EPG_REFILL_CHECKED] = true }
    }

    /**
     * Interrupted-restore marker. A restore writes to the database *and* to several DataStore
     * files; only the row writes can share a transaction, so a crash or a pulled plug part-way
     * through leaves a half-applied merge that nothing would otherwise notice. The marker is written
     * before the first write and removed after the last one, so a value still present at the next
     * launch means "that restore didn't finish". The value is the backup file name plus the sections
     * that were being applied — enough to tell the user what to re-run, and never a secret.
     */
    val restoreInProgress: Flow<String?> = prefsFlow { it[Keys.RESTORE_IN_PROGRESS] }

    suspend fun markRestoreStarted(description: String) {
        context.dataStore.edit { prefs -> prefs[Keys.RESTORE_IN_PROGRESS] = description }
    }

    suspend fun clearRestoreMarker() {
        context.dataStore.edit { prefs -> prefs.remove(Keys.RESTORE_IN_PROGRESS) }
    }

    private inline fun <reified E : Enum<E>> parseRefreshMap(raw: String?, valueOf: (String) -> E): Map<Long, E> {
        if (raw.isNullOrBlank()) return emptyMap()
        val obj = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<Long, E>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = key.toLongOrNull() ?: continue
            val name = obj.optString(key)
            val mode = runCatching { valueOf(name) }.getOrNull() ?: continue
            out[id] = mode
        }
        return out
    }

    private fun readRefreshMap(raw: String?): MutableMap<String, String> {
        if (raw.isNullOrBlank()) return LinkedHashMap()
        val obj = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return LinkedHashMap()
        val out = LinkedHashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = obj.optString(key)
        }
        return out
    }

    private fun writeRefreshMap(map: MutableMap<String, String>, sourceId: Long, value: String): String {
        map[sourceId.toString()] = value
        return org.json.JSONObject(map.toMap()).toString()
    }

    /** Whether focusing a channel auto-plays it in the Live preview pane. */
    val livePreviewEnabled: Flow<Boolean> = prefsFlow { it[Keys.LIVE_PREVIEW] ?: true }

    /** False only while a saved, enabled Live layout has intentionally hidden its preview panel. */
    val livePreviewPanelActive: Flow<Boolean> = prefsFlow { !livePreviewPanelHidden(it) }

    suspend fun setLivePreviewEnabled(enabled: Boolean) {
        context.dataStore.edit {
            if (!enabled || !livePreviewPanelHidden(it)) it[Keys.LIVE_PREVIEW] = enabled
        }
    }

    /**
     * Whether the expanded Home hero plays its video. The hero preview holds a live decoder for as
     * long as the user browses Home, which is the one piece of background playback that had no off
     * switch. Defaults on, but off on a low-RAM device — the same test [PlayerBudget] already uses to
     * decide the player's memory budget, and the devices where a second video pipeline hurts most.
     */
    val heroPreviewEnabled: Flow<Boolean> = prefsFlow { it[Keys.HERO_PREVIEW] ?: heroPreviewDefault }

    /** The value [heroPreviewEnabled] reports until the user picks one; also the settings row's
     *  initial value, so the chip does not read "On" for a frame on a device where it is off. */
    val heroPreviewDefault: Boolean get() = !lowSpecDevice

    suspend fun setHeroPreviewEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HERO_PREVIEW] = enabled }
    }

    private val lowSpecDevice: Boolean by lazy { tv.own.owntv.core.player.PlayerBudget.of(context).lowSpec }

    /** Whether the Live preview plays audio (off by default so browsing stays quiet). */
    val livePreviewAudio: Flow<Boolean> = prefsFlow { it[Keys.LIVE_PREVIEW_AUDIO] ?: false }

    suspend fun setLivePreviewAudio(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_PREVIEW_AUDIO] = enabled }
    }

    /** TV Guide: OK on a channel first plays it in the preview, OK again goes full screen. */
    val guidePreviewOnOk: Flow<Boolean> = prefsFlow { it[Keys.GUIDE_PREVIEW_ON_OK] ?: true }

    suspend fun setGuidePreviewOnOk(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GUIDE_PREVIEW_ON_OK] = enabled }
    }

    /** Live TV: focus no longer auto-previews; OK previews first, OK again goes full screen. */
    val livePreviewOnOk: Flow<Boolean> = prefsFlow { it[Keys.LIVE_PREVIEW_ON_OK] ?: false }

    suspend fun setLivePreviewOnOk(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_PREVIEW_ON_OK] = enabled }
    }

    /**
     * An HLS stream offering both an SDR and an HDR picture plays the SDR one, for a TV whose HDR looks
     * washed out. Streams with one kind only are untouched.
     */
    val preferSdr: Flow<Boolean> = prefsFlow { it[Keys.PREFER_SDR] ?: false }

    suspend fun setPreferSdr(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PREFER_SDR] = enabled }
    }

    /**
     * Multiview — up to four live channels on screen at once. Off by default: it costs one provider
     * connection per tile, so it is never something the app starts doing on its own.
     */
    val multiviewEnabled: Flow<Boolean> = prefsFlow { it[Keys.MULTIVIEW_ENABLED] ?: false }

    suspend fun setMultiviewEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MULTIVIEW_ENABLED] = enabled }
    }

    /**
     * How many tiles the grid has, 1..4. Never reduced behind the user's back — a device or a provider
     * that cannot manage the number chosen says so on the tile that fails.
     */
    val multiviewTiles: Flow<Int> = prefsFlow {
        (it[Keys.MULTIVIEW_TILES] ?: DEFAULT_MULTIVIEW_TILES).coerceIn(MIN_MULTIVIEW_TILES, MAX_MULTIVIEW_TILES)
    }

    suspend fun setMultiviewTiles(tiles: Int) {
        context.dataStore.edit {
            it[Keys.MULTIVIEW_TILES] = tiles.coerceIn(MIN_MULTIVIEW_TILES, MAX_MULTIVIEW_TILES)
        }
    }

    /** Whether the >2-tile warning has been accepted, so it is asked once and not on every change. */
    val multiviewWarningAccepted: Flow<Boolean> = prefsFlow { it[Keys.MULTIVIEW_WARNING_ACCEPTED] ?: false }

    suspend fun setMultiviewWarningAccepted(accepted: Boolean) {
        context.dataStore.edit { it[Keys.MULTIVIEW_WARNING_ACCEPTED] = accepted }
    }

    /**
     * Switch the display's refresh rate to match the video frame rate (24/25/30/50/60 fps and the
     * 23.976/29.97/59.94 variants) during full-screen playback, and restore it on exit. Default off;
     * users whose display switches cleanly can opt in.
     */
    val autoFrameRateLive: Flow<Boolean> = prefsFlow { prefs ->
        if (afrMigrated(prefs)) prefs[Keys.AUTO_FRAME_RATE_LIVE] ?: false else false
    }

    // Also report Off before the startup migration coroutines complete, so an auto-resumed channel
    // cannot briefly request a display-mode switch on the first launch after either safety reset.
    private fun afrMigrated(prefs: Preferences): Boolean =
        prefs[Keys.AUTO_FRAME_RATE_RESET_416] == true && prefs[Keys.AUTO_FRAME_RATE_RESET_PRE12] == true

    /**
     * Whether the one-time Auto-frame-rate suggestion has already been answered.
     *
     * The suggestion only appears when a 24/25/50 fps stream is playing full-screen on a display whose
     * refresh rate is not a multiple of it *and* a matching mode exists — the case where the direct
     * surface path judders and AFR is the only cure. Answering it either way (or turning AFR
     * on by hand) sets this for good; it is never shown twice.
     */
    val autoFrameRatePrompted: Flow<Boolean> = prefsFlow { it[Keys.AUTO_FRAME_RATE_PROMPTED] ?: false }

    suspend fun setAutoFrameRatePrompted() {
        context.dataStore.edit { it[Keys.AUTO_FRAME_RATE_PROMPTED] = true }
    }

    suspend fun setAutoFrameRateLive(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.AUTO_FRAME_RATE_LIVE] = enabled
            it[Keys.AUTO_FRAME_RATE_RESET_416] = true
            it[Keys.AUTO_FRAME_RATE_RESET_PRE12] = true
            // Someone who has found the setting doesn't need to be told it exists.
            if (enabled) it[Keys.AUTO_FRAME_RATE_PROMPTED] = true
        }
    }

    /** v4.1.6 only: force AFR Off exactly once, including for users who previously enabled it. */
    suspend fun migrateAutoFrameRate416() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.AUTO_FRAME_RATE_RESET_416] == true) return@edit
            prefs[Keys.AUTO_FRAME_RATE_LIVE] = false
            prefs[Keys.AUTO_FRAME_RATE_RESET_416] = true
        }
    }

    /**
     * v4.2.0 one-shot: force AFR Off exactly once on devices below Android 12.
     *
     * Only from API 31 does a display report [android.view.Display.Mode.getAlternativeRefreshRates] —
     * the set of rates it can switch to *without* blanking. Below that the app is switching blind, so
     * every mode change risks an HDMI re-handshake that blacks the picture out mid-programme; this is
     * what users on older TV boxes report as "the stream pauses when the frame rate changes". Frame-rate
     * snapping and the change cooldown in `FrameRateController` make it rarer, but
     * nothing in the platform can make it seamless there.
     *
     * So the default is corrected once, silently, rather than left on from an earlier install. Turning it
     * back on afterwards is a deliberate choice (Settings warns first on these devices) and is never
     * overridden again — the flag is written on every device, including Android 12+ where nothing is
     * reset, so this can only ever run once.
     */
    suspend fun migrateAutoFrameRatePre12() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.AUTO_FRAME_RATE_RESET_PRE12] == true) return@edit
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                prefs[Keys.AUTO_FRAME_RATE_LIVE] = false
            }
            prefs[Keys.AUTO_FRAME_RATE_RESET_PRE12] = true
        }
    }

    /**
     * Gate for Android TV home publishing.
     *
     * A false [CoreBuildInfo.tvHome] pins it off and the stored preference is never consulted.
     * Without that the phone app published Watch Next rows after every sync — into a content
     * provider that does not exist and permissions it explicitly removes — and only a `runCatching`
     * in the sync worker kept it quiet.
     */
    val androidTvHomeEnabled: Flow<Boolean> =
        if (!CoreBuildInfo.tvHome) flowOf(false) else prefsFlow { it[Keys.ANDROID_TV_HOME] ?: true }

    suspend fun setAndroidTvHomeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANDROID_TV_HOME] = enabled }
    }

    val themeMode: Flow<ThemeMode> = prefsFlow { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val uiZoomPercent: Flow<Int> = prefsFlow { prefs ->
        UiZoom.clamp(prefs[Keys.UI_ZOOM_PCT] ?: UiZoom.DEFAULT)
    }

    suspend fun setUiZoomPercent(percent: Int) {
        context.dataStore.edit { it[Keys.UI_ZOOM_PCT] = UiZoom.clamp(percent) }
    }

    val fontCustomization: Flow<FontCustomization> = prefsFlow { prefs ->
        FontCustomization(
            sizePercent = UiFontScale.clamp(prefs[Keys.FONT_SIZE_PCT] ?: UiFontScale.DEFAULT),
            mainFamily = AppFontFamily.fromStored(
                prefs[Keys.MAIN_FONT_FAMILY],
                AppFontFamily.PLUS_JAKARTA_SANS,
            ),
            popupFamily = AppFontFamily.fromStored(
                prefs[Keys.POPUP_FONT_FAMILY],
                AppFontFamily.PLUS_JAKARTA_SANS,
            ),
            popupFontSizePercent = PopupFontScale.clamp(
                prefs[Keys.POPUP_FONT_SIZE_PCT] ?: PopupFontScale.DEFAULT,
            ),
            popupSizePercent = PopupSizeScale.clamp(
                prefs[Keys.POPUP_SIZE_PCT] ?: PopupSizeScale.DEFAULT,
            ),
        )
    }

    /** Saves the staged font dialog in one DataStore transaction, so the app never sees half a preset. */
    suspend fun setFontCustomization(value: FontCustomization) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE_PCT] = UiFontScale.clamp(value.sizePercent)
            prefs[Keys.POPUP_FONT_SIZE_PCT] = PopupFontScale.clamp(value.popupFontSizePercent)
            prefs[Keys.POPUP_SIZE_PCT] = PopupSizeScale.clamp(value.popupSizePercent)
            prefs[Keys.MAIN_FONT_FAMILY] = value.mainFamily.name
            prefs[Keys.POPUP_FONT_FAMILY] = value.popupFamily.name
        }
    }

    /** Docked mini-player size as a percentage of screen width (clamped to the allowed range). */
    val miniPlayerSizePct: Flow<Int> = prefsFlow { prefs ->
        tv.own.owntv.core.player.MiniPlayerSize.clamp(prefs[Keys.MINI_PLAYER_SIZE_PCT] ?: tv.own.owntv.core.player.MiniPlayerSize.DEFAULT)
    }

    suspend fun setMiniPlayerSizePct(percent: Int) {
        context.dataStore.edit { it[Keys.MINI_PLAYER_SIZE_PCT] = tv.own.owntv.core.player.MiniPlayerSize.clamp(percent) }
    }

    /** Docked mini-player screen position (a [tv.own.owntv.core.player.MiniPlayerPosition] name). */
    val miniPlayerPosition: Flow<String> = prefsFlow { prefs ->
        prefs[Keys.MINI_PLAYER_POSITION] ?: tv.own.owntv.core.player.MiniPlayerPosition.DEFAULT.name
    }

    suspend fun setMiniPlayerPosition(name: String) {
        context.dataStore.edit { it[Keys.MINI_PLAYER_POSITION] = name }
    }

    /** Live TV latency preset (a [LiveLatency] name). */
    val liveLatencyMode: Flow<String> = prefsFlow { prefs ->
        // Also report Balanced before the startup migration coroutine completes, so an auto-resumed
        // channel cannot briefly reuse an unsafe low/custom latency on the first 4.1.6 launch.
        if (prefs[Keys.LIVE_LATENCY_RESET_416] == true) {
            prefs[Keys.LIVE_LATENCY_MODE] ?: LiveLatency.DEFAULT.name
        } else {
            LiveLatency.BALANCED.name
        }
    }

    suspend fun setLiveLatencyMode(name: String) {
        context.dataStore.edit {
            it[Keys.LIVE_LATENCY_MODE] = name
            it[Keys.LIVE_LATENCY_RESET_416] = true
        }
    }

    /**
     * Every one-shot settings migration, for both apps at start. They used to run from the TV shell
     * only, so on the phone [liveLatencyMode] and [autoFrameRateLive] — which read as Balanced / Off until
     * their migration has run — ignored a restored or synced choice. Each is idempotent and flag-guarded,
     * and one failing must not stop the rest.
     */
    suspend fun runOneTimeMigrations() {
        runCatching { migrateLegacyRefreshFlags() }
        runCatching { migrateAutoFrameRate416() }
        runCatching { migrateAutoFrameRatePre12() }
        runCatching { migrateLiveLatency416() }
        runCatching { migrateSystemSansToJakarta() }
    }

    /** Whether the one-time drop of old list orders (see `ContentOrderDao.dropListOrdersExcept`) has run. */
    suspend fun listOrdersReset(): Boolean = context.dataStore.data.first()[Keys.LIST_ORDERS_RESET] == true

    suspend fun markListOrdersReset() {
        context.dataStore.edit { it[Keys.LIST_ORDERS_RESET] = true }
    }

    /**
     * Plus Jakarta Sans became the default main and popup font, but the font dialog stores every field
     * when it is saved, so anyone who ever pressed Apply there kept System Sans — the old default — and
     * never got the new one. Owner's decision (2026-09-30): move them once. Only System Sans is moved;
     * any other font was a real choice and stays. A System Sans picked after this has run is kept too.
     */
    suspend fun migrateSystemSansToJakarta() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.FONT_JAKARTA_MIGRATED] == true) return@edit
            for (key in listOf(Keys.MAIN_FONT_FAMILY, Keys.POPUP_FONT_FAMILY)) {
                if (prefs[key] == AppFontFamily.SYSTEM_SANS.name) prefs[key] = AppFontFamily.PLUS_JAKARTA_SANS.name
            }
            prefs[Keys.FONT_JAKARTA_MIGRATED] = true
        }
    }

    /**
     * The TV app's Stage UI starts everyone on its own defaults, once: the rail
     * Docked · Compact · Fit (navigation choices stored by older versions do not apply), glass on
     * every surface at 50%, and the Stage background — a stored picture is removed. TV only:
     * the phone has its own look.
     */
    suspend fun applyStageDefaults() {
        var oldPicture: String? = null
        context.dataStore.edit { prefs ->
            if (prefs[Keys.STAGE_DEFAULTS_APPLIED] == true) return@edit
            stageDefaults(prefs)
            oldPicture = prefs[Keys.BG_IMAGE_PATH]
            prefs.remove(Keys.BG_IMAGE_PATH)
        }
        oldPicture?.takeIf { it.isNotBlank() }?.let { runCatching { java.io.File(it).delete() } }
    }

    /**
     * The phone app's start for a new user: the Stage background and glass on every surface
     * at [alphaPct]. Written only on a fresh install — neither glass nor background ever set —
     * so an existing install keeps whatever it has. The TV uses [applyStageDefaults].
     */
    suspend fun applyPhoneStageDefaults(alphaPct: Int) {
        context.dataStore.edit { phoneStageDefaults(it, alphaPct) }
    }

    /** v4.1.6 only: force live latency to Balanced exactly once, including existing custom choices. */
    suspend fun migrateLiveLatency416() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.LIVE_LATENCY_RESET_416] == true) return@edit
            prefs[Keys.LIVE_LATENCY_MODE] = LiveLatency.BALANCED.name
            prefs[Keys.LIVE_LATENCY_RESET_416] = true
        }
    }

    /** Custom live buffer seconds, used when the preset is [LiveLatency.CUSTOM]. */
    val liveLatencyCustomSecs: Flow<Int> = prefsFlow { prefs ->
        LiveBuffer.clampCustom(prefs[Keys.LIVE_LATENCY_CUSTOM_SECS] ?: LiveBuffer.CUSTOM_DEFAULT)
    }

    suspend fun setLiveLatencyCustomSecs(secs: Int) {
        context.dataStore.edit { it[Keys.LIVE_LATENCY_CUSTOM_SECS] = LiveBuffer.clampCustom(secs) }
    }

    /**
     * "Pre-buffer": how many seconds to fill before a live channel starts playing,
     * and how many to refill after a rebuffer. 0 = Off, which keeps the player's existing 1 s / 2 s
     * start thresholds. A per-playlist override lives on `SourceEntity.livePrerollSecs`.
     */
    val livePrerollSecs: Flow<Int> = prefsFlow { prefs ->
        (prefs[Keys.LIVE_PREROLL_SECS] ?: LiveBuffer.PREROLL_OFF).coerceIn(0, 30)
    }

    suspend fun setLivePrerollSecs(secs: Int) {
        context.dataStore.edit { it[Keys.LIVE_PREROLL_SECS] = secs.coerceIn(0, 30) }
    }

    /**
     * "Give up after": how long a live channel may take to produce a picture before OwnTV stops trying
     * and shows the error, however many formats the fallback ladder has left.
     *
     * 0 = Never, the behaviour before this setting existed: each rung keeps its own timeout and nothing
     * bounds their sum, which on a removed channel is about a minute and a half of black screen. Kept as
     * an escape hatch for a genuinely slow panel. A provider's own `Retry-After` countdown is never
     * charged against this.
     */
    val liveTuneTimeoutSecs: Flow<Int> = prefsFlow { prefs ->
        (prefs[Keys.LIVE_TUNE_TIMEOUT_SECS] ?: tv.own.owntv.core.player.PlaybackDefaults.LIVE_TUNE_BUDGET_SECS)
            .coerceIn(0, 60)
    }

    suspend fun setLiveTuneTimeoutSecs(secs: Int) {
        context.dataStore.edit { it[Keys.LIVE_TUNE_TIMEOUT_SECS] = secs.coerceIn(0, 60) }
    }

    // --- The one built-in source's settings-backed fields (schema v5 removed its DB row) ---

    /** Epoch ms of the last complete catalogue sync; null = never synced. */
    val sourceLastSyncAt: Flow<Long?> = prefsFlow { prefs ->
        prefs[Keys.SOURCE_LAST_SYNC_AT]?.takeIf { it > 0L }
    }

    suspend fun markSourceSynced(atMs: Long) {
        context.dataStore.edit { it[Keys.SOURCE_LAST_SYNC_AT] = atMs }
    }

    /** Global "Clean channel names" choice, replacing the per-source row it used to live on. */
    val channelCleanupEnabled: Flow<Boolean> = prefsFlow { prefs ->
        prefs[Keys.CHANNEL_CLEANUP_ENABLED] ?: false
    }

    val channelNameStrip: Flow<String?> = prefsFlow { prefs ->
        prefs[Keys.CHANNEL_CLEANUP_STRIP]?.takeIf { it.isNotBlank() }
    }

    suspend fun setChannelNameCleanup(enabled: Boolean, strip: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHANNEL_CLEANUP_ENABLED] = enabled
            val words = strip?.trim()?.takeIf { it.isNotEmpty() }
            if (words == null) prefs.remove(Keys.CHANNEL_CLEANUP_STRIP) else prefs[Keys.CHANNEL_CLEANUP_STRIP] = words
        }
    }

    /**
     * Everything [tv.own.owntv.core.repository.BuiltInSource] folds into its DTO, as one snapshot.
     * Combines the global values one source now uses; a source's former per-playlist overrides are
     * the global settings.
     */
    val sourcePreferences: Flow<SourcePreferences> = combine(
        livePrerollSecs,
        liveLatencyMode,
        liveLatencyCustomSecs,
        liveTuneTimeoutSecs,
        sourceLastSyncAt,
    ) { preroll, latencyMode, latencyCustom, tuneTimeout, lastSync ->
        SourcePreferences(
            livePrerollSecs = preroll,
            liveLatencyMode = latencyMode,
            liveLatencyCustomSecs = latencyCustom,
            liveTuneTimeoutSecs = tuneTimeout,
            lastSyncAt = lastSync,
            cleanChannelNames = false,
            channelNameStrip = null,
        )
    }.combine(channelCleanupEnabled) { prefs, cleanup ->
        prefs.copy(cleanChannelNames = cleanup)
    }.combine(channelNameStrip) { prefs, strip ->
        prefs.copy(channelNameStrip = strip)
    }.distinctUntilChanged()

    /** Effective live buffer in seconds the player applies (null = keep its defaults, i.e. Balanced). */
    val liveBufferSeconds: Flow<Int?> = combine(liveLatencyMode, liveLatencyCustomSecs) { mode, custom ->
        LiveBuffer.effectiveSeconds(LiveLatency.fromName(mode), custom)
    }

    val accent: Flow<AccentColor> = prefsFlow { prefs ->
        prefs[Keys.ACCENT]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
            ?: AccentColor.VIOLET
    }

    /** Picking a preset clears any custom accent so the preset takes effect. */
    suspend fun setAccent(accent: AccentColor) {
        context.dataStore.edit {
            it[Keys.ACCENT] = accent.name
            it[Keys.ACCENT_CUSTOM] = ""
        }
    }

    /** Custom accent as a hex string ("#52DBC8"); blank = use the [accent] preset. */
    val customAccent: Flow<String> = prefsFlow { it[Keys.ACCENT_CUSTOM] ?: "" }

    suspend fun setCustomAccent(hex: String) {
        context.dataStore.edit { it[Keys.ACCENT_CUSTOM] = hex.trim() }
    }

    // --- Focus highlight: the ring around whatever the remote is pointing at ---
    /** Focus ring color as a hex string; blank = follow the accent (the shipped behaviour). */
    val focusHighlight: Flow<String> = prefsFlow { it[Keys.FOCUS_HIGHLIGHT] ?: "" }

    /** Focus ring width in dp; 2 dp is the shipped default. */
    val focusHighlightWidth: Flow<Int> = prefsFlow { it[Keys.FOCUS_HIGHLIGHT_WIDTH] ?: 2 }

    suspend fun setFocusHighlight(hex: String) {
        context.dataStore.edit { it[Keys.FOCUS_HIGHLIGHT] = hex.trim() }
    }

    suspend fun setFocusHighlightWidth(dp: Int) {
        context.dataStore.edit { it[Keys.FOCUS_HIGHLIGHT_WIDTH] = dp }
    }

    // --- Glass & background: background picture, Glass on/off and opacity ---
    /** Absolute path to the user's background image (copied into app-private storage); blank = off. */
    val bgImagePath: Flow<String> = prefsFlow { it[Keys.BG_IMAGE_PATH] ?: "" }

    /** Glass on or off, and its opacity. */
    val glassConfig: Flow<tv.own.owntv.core.theme.GlassConfig> = prefsFlow { p ->
        tv.own.owntv.core.theme.GlassConfig(
            enabled = (p[Keys.GLASS_SCOPE] ?: GLASS_SCOPE_DEFAULT_BITS) != 0,
            alpha = ((p[Keys.GLASS_ALPHA] ?: GLASS_ALPHA_DEFAULT_PCT) / 100f).coerceIn(0f, 1f),
        )
    }

    /** The resolved background (Glass & background page). */
    val backgroundConfig: Flow<tv.own.owntv.core.theme.BackgroundConfig> = prefsFlow { p ->
        tv.own.owntv.core.theme.BackgroundConfig.resolve(
            style = p[Keys.BG_STYLE],
            look = p[Keys.BG_LOOK],
            dimPct = p[Keys.BG_DIM],
            blurPct = p[Keys.BG_BLUR],
            accentLight = p[Keys.BG_ACCENT_LIGHT],
            imagePath = p[Keys.BG_IMAGE_PATH] ?: "",
            glassOn = (p[Keys.GLASS_SCOPE] ?: GLASS_SCOPE_DEFAULT_BITS) != 0,
        )
    }

    /** Background: Stage colours, Picture or Plain. Choosing Picture with no look yet starts on Soft. */
    suspend fun setBackgroundStyle(style: tv.own.owntv.core.theme.BackgroundStyle) {
        context.dataStore.edit {
            it[Keys.BG_STYLE] = style.name
            if (style == tv.own.owntv.core.theme.BackgroundStyle.PICTURE && it[Keys.BG_LOOK] == null) {
                it[Keys.BG_LOOK] = tv.own.owntv.core.theme.PictureLook.SOFT.name
            }
            it.remove(Keys.BG_ACCENT_LIGHT)
        }
    }

    /** Picture look; Darken, Blur and Accent light go back to that look's own values. */
    suspend fun setPictureLook(look: tv.own.owntv.core.theme.PictureLook) {
        context.dataStore.edit {
            it[Keys.BG_LOOK] = look.name
            it.remove(Keys.BG_DIM); it.remove(Keys.BG_BLUR); it.remove(Keys.BG_ACCENT_LIGHT)
        }
    }

    /**
     * A newly chosen picture: Background becomes Picture and starts on Soft, with
     * that look's own values.
     */
    suspend fun setNewBackgroundPicture(path: String) {
        context.dataStore.edit {
            it[Keys.BG_IMAGE_PATH] = path.trim()
            it[Keys.BG_STYLE] = tv.own.owntv.core.theme.BackgroundStyle.PICTURE.name
            it[Keys.BG_LOOK] = tv.own.owntv.core.theme.PictureLook.SOFT.name
            it.remove(Keys.BG_DIM); it.remove(Keys.BG_BLUR); it.remove(Keys.BG_ACCENT_LIGHT)
        }
    }

    suspend fun setBackgroundDim(pct: Int) {
        context.dataStore.edit { it[Keys.BG_DIM] = pct.coerceIn(0, tv.own.owntv.core.theme.BackgroundConfig.DIM_MAX) }
    }

    suspend fun setBackgroundBlur(pct: Int) {
        context.dataStore.edit { it[Keys.BG_BLUR] = pct.coerceIn(0, tv.own.owntv.core.theme.BackgroundConfig.BLUR_MAX) }
    }

    suspend fun setBackgroundAccentLight(on: Boolean) {
        context.dataStore.edit { it[Keys.BG_ACCENT_LIGHT] = on }
    }

    /**
     * Reset (Glass & background): Stage colours, glass on every surface, opacity 50%. The picture file
     * is kept so Background › Picture can return to it.
     */
    suspend fun resetGlassAndBackground(alphaPct: Int = GLASS_ALPHA_DEFAULT_PCT) {
        context.dataStore.edit {
            it[Keys.BG_STYLE] = tv.own.owntv.core.theme.BackgroundStyle.STAGE.name
            it.remove(Keys.BG_LOOK); it.remove(Keys.BG_DIM); it.remove(Keys.BG_BLUR); it.remove(Keys.BG_ACCENT_LIGHT)
            it[Keys.GLASS_SCOPE] = tv.own.owntv.core.theme.GlassConfig.ON_BITS
            it[Keys.GLASS_ALPHA] = alphaPct
        }
    }

    /** Persist the background image path. Pass "" to clear (turn glass off). */
    suspend fun setBgImagePath(path: String) {
        context.dataStore.edit { it[Keys.BG_IMAGE_PATH] = path.trim() }
    }

    /** Glass on ([tv.own.owntv.core.theme.GlassConfig.ON_BITS]) or off (0). */
    suspend fun setGlassScopeBitmask(bits: Int) {
        context.dataStore.edit { it[Keys.GLASS_SCOPE] = bits }
    }

    /** Persist glass alpha as an integer 0..100. */
    suspend fun setGlassAlphaPercent(pct: Int) {
        context.dataStore.edit { it[Keys.GLASS_ALPHA] = pct.coerceIn(0, 100) }
    }

    // --- Global proxy (Approach 1 — one app-wide HTTP proxy) ---
    // Covers all OkHttp traffic (playlist/API/EPG/images/downloads/updates/weather) and ExoPlayer
    // playback. Per-source overrides and SOCKS are future work; the proxy
    // password is intentionally NOT part of settings backup/export — see extras/PROXY_SUPPORT_PLAN.md.

    /** Live snapshot of the proxy settings as a single object (consumed by ProxyConfigHolder). */
    val proxyConfig: Flow<tv.own.owntv.core.network.ProxyConfig> = prefsFlow { p ->
        tv.own.owntv.core.network.ProxyConfig(
            enabled = p[Keys.PROXY_ENABLED] ?: false,
            host = p[Keys.PROXY_HOST] ?: "",
            port = p[Keys.PROXY_PORT] ?: 0,
            username = p[Keys.PROXY_USER] ?: "",
            password = p[Keys.PROXY_PASS] ?: "",
        )
    }

    /** Persist the proxy form in one write (enabled + host/port/user/pass). Blank user/pass = no auth.
     *  Port is clamped to a valid range; 0 means "unset". */
    suspend fun saveProxy(enabled: Boolean, host: String, port: Int, username: String, password: String) {
        context.dataStore.edit {
            it[Keys.PROXY_ENABLED] = enabled
            it[Keys.PROXY_HOST] = host.trim()
            it[Keys.PROXY_PORT] = port.coerceIn(0, 65535)
            it[Keys.PROXY_USER] = username.trim()
            it[Keys.PROXY_PASS] = password
        }
    }

    // --- Backup / restore of pure UI/player preferences (device-agnostic) ---
    // Deliberately EXCLUDES the download folder (a device-specific path) and the source-coupled keys
    // (default source, refresh-on-startup) — those ride with the sources backup.

    // A new key belongs in one of these lists, or in SettingsBackupCoverageTest's exclusions with a reason.
    internal object BackupKeys {
        val strings = listOf(
            Keys.THEME_MODE, Keys.ACCENT, Keys.ACCENT_CUSTOM, Keys.FOCUS_HIGHLIGHT, Keys.DEFAULT_ZOOM,
            Keys.MAIN_FONT_FAMILY, Keys.POPUP_FONT_FAMILY,
            Keys.PREF_AUDIO_LANG, Keys.PREF_SUB_LANG, Keys.SORT_LIVE, Keys.SORT_GUIDE, Keys.ANIMATION_LEVEL, Keys.GUIDE_VIEW,
            Keys.LIVE_LAYOUT, Keys.LIVE_VIEW, Keys.REMINDER_MODE, Keys.PLAYER_HOLD_OK, Keys.PLAYER_SHORT_OK,
            Keys.WEATHER_LOCATION, Keys.RECENT_SEARCHES,
            // The one user's startup landing and Home layout ride with settings now that
            // they are plain keys rather than per-profile maps. A restored startup target is checked
            // against the surviving sources after the SOURCES section lands (repairSpecificStartupChannel).
            Keys.STARTUP_MODE, Keys.STARTUP_CHANNEL, Keys.HOME_CONFIG,
            Keys.CLOCK_TIME_COLOR, Keys.CLOCK_DATE_COLOR, Keys.CLOCK_WEATHER_COLOR,
            // Global proxy — non-secret fields only. The proxy password (Keys.PROXY_PASS) is NEVER part of
            // this whitelist; it is handled separately by BackupManager (encrypted or omitted).
            Keys.PROXY_HOST, Keys.PROXY_USER,
            // Nav menu mode rides with settings backup so a reinstall keeps the user's DYNAMIC/STATIC choice.
            Keys.NAV_MENU_MODE,
            // Stage navigation style, size and length (the hide delay is an int key).
            Keys.NAV_STYLE, Keys.NAV_SIZE, Keys.NAV_LENGTH, Keys.NAV_WIDEN,
            // The chosen icon colour. After a restore the app compares it with the applied icon and offers a restart.
            Keys.APP_ICON,
            // The user's own words for "Clean channel names" (the toggle is a bool key).
            Keys.CHANNEL_CLEANUP_STRIP,
            // Docked mini-player position rides with settings backup (size is an int key, see backupIntKeys).
            Keys.MINI_PLAYER_POSITION,
            // Live TV latency preset (custom seconds is an int key, see backupIntKeys).
            Keys.LIVE_LATENCY_MODE,
            // Glass & background's picture path (Glass on/off and opacity are int keys). Only the path string
            // travels — the image bytes live in app-private storage which is wiped on uninstall, so on a new
            // device a stale path is ignored gracefully (falls back to none).
            Keys.BG_IMAGE_PATH,
            // Glass & background: Background and Picture look (Darken / Blur are int keys, Accent light a bool).
            Keys.BG_STYLE, Keys.BG_LOOK,
            // Subtitle appearance: text color and screen position (toggle is a bool key, size a float
            // key, background transparency an int key).
            Keys.SUB_COLOR,
            Keys.SUB_FONT,
            Keys.SUB_POSITION,
            // Custom DNS — not secret, backed up alongside proxy
            Keys.DNS_HOST, Keys.DNS_DOH_URL,
            // Surround mode (Auto/Stereo only/Surround). The legacy boolean is in backupBoolKeys and stays
            // in sync, but the string is what is read first, so it has to travel too.
            Keys.SURROUND_MODE,
            // Video surface (SURFACE_VIEW/TEXTURE_VIEW) — the same enum-name shape as surround mode.
            Keys.VIDEO_SURFACE_MODE,
            // The mobility layer's two choices. Same reasoning as the touch-host booleans below: a phone
            // restored from a phone keeps them, a television never reads them.
            Keys.MINI_PLAYER_STYLE, Keys.PIP_SIZE,
            // Settings personalization: Quick pins (including their order) and the independently arranged
            // action order for each of the four long-press content menus.
            Keys.QUICK_PINNED,
        )
        val stringSets = listOf(
            // The STATIC-mode hidden set rides with backup so a reinstall keeps the user's hidden icons.
            Keys.NAV_MENU_HIDDEN,
            Keys.REMOTE_SHORTCUT_BINDINGS,
        )
        val ints = listOf(Keys.GUIDE_DAYS_TO_KEEP, Keys.FOCUS_HIGHLIGHT_WIDTH, Keys.DEFAULT_VOLUME, Keys.SEEK_STEP_SEC, Keys.UI_ZOOM_PCT, Keys.FONT_SIZE_PCT, Keys.AUDIO_DELAY_MS, Keys.EPG_OFFSET_MIN, Keys.PROXY_PORT, Keys.DNS_PORT, Keys.CH_NAV_UP_SKIP, Keys.CH_NAV_DOWN_SKIP, Keys.MINI_PLAYER_SIZE_PCT, Keys.LIVE_LATENCY_CUSTOM_SECS, Keys.LIVE_PREROLL_SECS, Keys.LIVE_TUNE_TIMEOUT_SECS, Keys.GLASS_SCOPE, Keys.GLASS_ALPHA, Keys.BG_DIM, Keys.BG_BLUR, Keys.SUB_BG_OPACITY,
            Keys.PANEL_W_LIVE_CAT, Keys.PANEL_W_LIVE_LIST, Keys.PANEL_W_LIVE_PREVIEW,
            Keys.LIVE_STAGE_SHEET, Keys.LIVE_STAGE_LIST, Keys.LIVE_STAGE_PREVIEW,
            Keys.GUIDE_WIDTH_CHANNELS, Keys.GUIDE_WIDTH_EPG,
            Keys.POPUP_FONT_SIZE_PCT, Keys.POPUP_SIZE_PCT, Keys.GUIDE_DENSITY_PCT,
            Keys.GESTURE_SENSITIVITY_PCT,
            Keys.NAV_HIDE_AFTER_MS, Keys.REMINDER_LEAD_MIN,
            // Multiview's tile count (the reader clamps it).
            Keys.MULTIVIEW_TILES,
            // The two quality limits; the readers accept only their own choices.
            Keys.MAX_VIDEO_HEIGHT, Keys.MOBILE_DATA_MAX_VIDEO_HEIGHT)
        val bools = listOf(
            Keys.GUIDE_SHOW_EMPTY, Keys.GUIDE_PREVIEW,
            Keys.LIVE_PREVIEW, Keys.LIVE_PREVIEW_AUDIO, Keys.GUIDE_PREVIEW_ON_OK, Keys.LIVE_PREVIEW_ON_OK, Keys.HERO_PREVIEW, Keys.PREFER_SDR, Keys.AUTO_FRAME_RATE_LIVE, Keys.AUTO_FRAME_RATE_PROMPTED, Keys.ANDROID_TV_HOME, Keys.HW_DECODING, Keys.SOFTWARE_DECODE_ABOVE_1080P,
            Keys.MEASURED_STREAM_STATS, Keys.DETAILED_DIAGNOSTICS, Keys.DIRECT_TUNE,
            Keys.EXTERNAL_PLAYER_LIVE, Keys.UPDATE_CHECK_ON_START, Keys.SURROUND_SOUND, Keys.PROXY_ENABLED,
            Keys.WEATHER_ENABLED, Keys.WEATHER_FAHRENHEIT, Keys.RESUME_LAST_CHANNEL, Keys.CH_NAV_ENABLED, Keys.PLAYER_SWAP_CHANNEL_KEYS, Keys.PLAYER_NUMBER_KEYS_TUNE,
            Keys.HIDE_NEW_CATEGORIES, Keys.CHANNEL_CLEANUP_ENABLED,
            Keys.DNS_ENABLED,
            Keys.REMEMBER_LAST_LIVE,
            Keys.REMEMBER_CAT_LIVE, Keys.GUIDE_FOLLOWS_LIVE_CATEGORY,
            Keys.LIVE_SHOW_ALL_RAIL,
            Keys.SUB_STYLE_ENABLED,
                Keys.PANEL_W_LIVE_ON, Keys.GUIDE_WIDTH_ON,
            Keys.AMBIENT_GLOW_ENABLED, Keys.AMBIENT_GLOW_PULSE,
            Keys.BG_ACCENT_LIGHT, Keys.BRAND_ACCENT_TRIANGLE,
            // Touch-host settings. They travel even though a television has no row for them: a phone
            // restored from a phone must keep them, and a television simply ignores what it never reads.
            Keys.BACKGROUND_PLAYBACK, Keys.PIP_ENABLED, Keys.DATA_SAVER,
            Keys.PHONE_NAV_LABELS, Keys.PHONE_NAV_HIDE_ON_SCROLL, Keys.SHEET_COUNTS,
            Keys.PIP_ON_BACK, Keys.PIP_SNAP, Keys.AUDIO_ON_SCREEN_OFF, Keys.AUDIO_ON_MOBILE_DATA,
            Keys.AUDIO_PER_CHANNEL,
            Keys.MULTIVIEW_ENABLED, Keys.MULTIVIEW_WARNING_ACCEPTED,
            Keys.AUDIO_PASSTHROUGH, Keys.NIGHT_MODE, Keys.VOLUME_LEVELLING, Keys.TUNNELED_PLAYBACK,
            // Fullscreen mode: a television never reads it, a phone restored from a phone keeps it.
            Keys.FULLSCREEN_MODE,
        )
        val floats = listOf(Keys.SUB_SCALE, Keys.SUB_SCALE_EXO)

        /**
         * Old keys that are still *imported* (older backups carry them) but exported only while they
         * still decide something — each is read as the fallback for the keys listed with it, until those
         * are written. `pip_on_back` has no reader at all, so it is never exported.
         */
        val legacySuccessors: Map<Preferences.Key<*>, List<Preferences.Key<*>>> = mapOf(
            Keys.SURROUND_SOUND to listOf(Keys.SURROUND_MODE),
            Keys.SUB_SCALE to listOf(Keys.SUB_SCALE_EXO, Keys.SUB_STYLE_ENABLED),
        )

        /** Whether [k] goes into a new backup, given this device's stored [p]. */
        fun exports(k: Preferences.Key<*>, p: Preferences): Boolean = when (k) {
            Keys.PIP_ON_BACK -> false
            // The fallback for a user who never picked a startup mode; only "on" changes anything.
            Keys.RESUME_LAST_CHANNEL -> p[Keys.RESUME_LAST_CHANNEL] == true
            else -> legacySuccessors[k]?.any { p[it] == null } ?: true
        }
    }

    private val backupStringKeys = BackupKeys.strings + ContentMenu.entries.flatMap { listOf(menuOrderKey(it), menuHiddenKey(it)) }
    private val backupStringSetKeys = BackupKeys.stringSets
    private val backupIntKeys = BackupKeys.ints
    private val backupBoolKeys = BackupKeys.bools
    private val backupFloatKeys = BackupKeys.floats

    /**
     * "Remember last category" values (see the REMEMBER_CAT_* toggles, which are backed up as plain
     * booleans above). These need a filter rather than a straight whitelist entry, so they live apart:
     * a provider folder is stored as "FOLDER:<Room category id>", and Room content ids are recreated
     * by every sync (the catalog is clear-then-insert), so that value means nothing on another device
     * — restoring it would land the user in an arbitrary category. The stable forms travel:
     * "ALL" / "FAV" / "HIST" and "CUSTOM:<uuid>", a user-created category whose id really
     * is portable. The one user's value travels in [exportLastCategories]; [importSettings] reads
     * the same plain key an older file carries.
     *
     * `last_live_channel` is deliberately absent for the same reason and has no stable form at all:
     * it is a Room channel id, so there is nothing here worth carrying.
     */
    private val backupLastCategoryKeys = listOf(
        Keys.LAST_LIVE_CATEGORY,
    )

    private fun isPortableCategoryKey(value: String): Boolean =
        value == "ALL" || value == "FAV" || value == "HIST" || value.startsWith("CUSTOM:")

    /** The remembered category, portable values only, as `{ "last_live_category": … }`. */
    suspend fun exportLastCategories(): org.json.JSONObject {
        val out = org.json.JSONObject()
        val value = context.positionStore.data.first()[Keys.LAST_LIVE_CATEGORY]
        if (value != null && isPortableCategoryKey(value)) out.put(Keys.LAST_LIVE_CATEGORY.name, value)
        return out
    }

    /** Restores [exportLastCategories]: the portable value only; anything else is dropped safely. */
    suspend fun importLastCategories(o: org.json.JSONObject) {
        val name = Keys.LAST_LIVE_CATEGORY.name
        if (!o.has(name)) return
        val value = runCatching { o.getString(name) }.getOrNull()?.takeIf(::isPortableCategoryKey) ?: return
        context.positionStore.edit { positions -> positions[Keys.LAST_LIVE_CATEGORY] = value }
    }

    suspend fun exportSettings(): org.json.JSONObject {
        val p = context.dataStore.data.first()
        return org.json.JSONObject().apply {
            backupStringKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupStringSetKeys.forEach { k -> p[k]?.let { put(k.name, org.json.JSONArray(it)) } }
            backupIntKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupBoolKeys.filter { BackupKeys.exports(it, p) }.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupFloatKeys.filter { BackupKeys.exports(it, p) }.forEach { k -> p[k]?.let { put(k.name, it.toDouble()) } }
            // The UI language lives in SharedPreferences (LocaleStore), not DataStore, so it carries as
            // solate, explicitly serialised field rather than pretending it is a DataStore key. `""` means
            // follow system (see docs/internationalization.md 0b, "Backup interaction").
            put(UI_LANGUAGE_KEY, localeStore.currentTag.value)
        }
    }

    /**
     * The 4×2 sync matrix (phase ③): what this device gives to and reads from the other household
     * TVs. Defaults from [SyncMatrix.DEFAULTS] — history is private by default (give=false,
     * read=true), everything else gives and reads. The ticks live in the local DataStore only and
     * are deliberately NOT part of the sync payload.
     */
    val syncMatrix: Flow<SyncMatrix> = prefsFlow {
        SyncMatrix(
            giveFavorites = it[Keys.SYNC_GIVE_FAVORITES] ?: true,
            readFavorites = it[Keys.SYNC_READ_FAVORITES] ?: true,
            giveOrder = it[Keys.SYNC_GIVE_ORDER] ?: true,
            readOrder = it[Keys.SYNC_READ_ORDER] ?: true,
            giveHistory = it[Keys.SYNC_GIVE_HISTORY] ?: false,
            readHistory = it[Keys.SYNC_READ_HISTORY] ?: true,
            giveReminders = it[Keys.SYNC_GIVE_REMINDERS] ?: true,
            readReminders = it[Keys.SYNC_READ_REMINDERS] ?: true,
        )
    }

    suspend fun setSyncGiveFavorites(v: Boolean) { context.dataStore.edit { it[Keys.SYNC_GIVE_FAVORITES] = v } }
    suspend fun setSyncReadFavorites(v: Boolean) { context.dataStore.edit { it[Keys.SYNC_READ_FAVORITES] = v } }
    suspend fun setSyncGiveOrder(v: Boolean) { context.dataStore.edit { it[Keys.SYNC_GIVE_ORDER] = v } }
    suspend fun setSyncReadOrder(v: Boolean) { context.dataStore.edit { it[Keys.SYNC_READ_ORDER] = v } }
    suspend fun setSyncGiveHistory(v: Boolean) { context.dataStore.edit { it[Keys.SYNC_GIVE_HISTORY] = v } }
    suspend fun setSyncReadHistory(v: Boolean) { context.dataStore.edit { it[Keys.SYNC_READ_HISTORY] = v } }
    suspend fun setSyncGiveReminders(v: Boolean) { context.dataStore.edit { it[Keys.SYNC_GIVE_REMINDERS] = v } }
    suspend fun setSyncReadReminders(v: Boolean) { context.dataStore.edit { it[Keys.SYNC_READ_REMINDERS] = v } }

    /** One setter for the whole matrix, for the settings screen that edits all eight at once. */
    suspend fun setSyncMatrix(matrix: SyncMatrix) {
        context.dataStore.edit {
            it[Keys.SYNC_GIVE_FAVORITES] = matrix.giveFavorites
            it[Keys.SYNC_READ_FAVORITES] = matrix.readFavorites
            it[Keys.SYNC_GIVE_ORDER] = matrix.giveOrder
            it[Keys.SYNC_READ_ORDER] = matrix.readOrder
            it[Keys.SYNC_GIVE_HISTORY] = matrix.giveHistory
            it[Keys.SYNC_READ_HISTORY] = matrix.readHistory
            it[Keys.SYNC_GIVE_REMINDERS] = matrix.giveReminders
            it[Keys.SYNC_READ_REMINDERS] = matrix.readReminders
        }
    }

    /** This device, as a backup records it (see [tv.own.owntv.core.sync.local.DeviceIdentity]). */
    fun deviceIdentity(): tv.own.owntv.core.sync.local.DeviceIdentity = tv.own.owntv.core.sync.local.DeviceIdentity.of(context)

    /**
     * The settings that describe this device's hardware rather than the user's taste: whether hardware
     * decoding works on it, whether its display can
     * switch frame rate, HDR, what its audio output accepts. Restored from another
     * device only when the user says so ([importSettings]'s `keepDeviceSettings`) — a phone's or an
     * Android 11 box's values were simply copied onto whatever the backup was restored to.
     */
    private val deviceSpecificSettings: Set<String> = listOf(
        Keys.HW_DECODING, Keys.SOFTWARE_DECODE_ABOVE_1080P, Keys.AUTO_FRAME_RATE_LIVE,
        Keys.PREFER_SDR, Keys.SURROUND_MODE, Keys.SURROUND_SOUND, Keys.AUDIO_PASSTHROUGH,
        Keys.TUNNELED_PLAYBACK,
    ).map { it.name }.toSet()

    /** Whether a backup's settings block holds any of the [deviceSpecificSettings]. */
    fun carriesDeviceSettings(o: org.json.JSONObject): Boolean = deviceSpecificSettings.any { o.has(it) }

    /**
     * [keepDeviceSettings]: leave this device's [deviceSpecificSettings] as they are, because the file
     * came from another device and the user did not ask for them.
     */
    suspend fun importSettings(o: org.json.JSONObject, keepDeviceSettings: Boolean = false): SettingsImportResult {
        context.dataStore.edit { prefs ->
            // Every value on its own: one wrong-typed or out-of-range entry (another build, a hand edit)
            // is skipped and this device keeps its own value, instead of aborting the whole restore.
            // Values the UI can only ever write in range are checked here too — see [SettingsImportRules].
            fun each(k: Preferences.Key<*>, apply: () -> Unit) {
                if (keepDeviceSettings && k.name in deviceSpecificSettings) return
                if (o.has(k.name)) runCatching(apply).onFailure { android.util.Log.w("OwnTV-Settings", "settings import: skipped ${k.name}") }
            }
            backupStringKeys.forEach { k ->
                each(k) { SettingsImportRules.string(k.name, o.getString(k.name))?.let { prefs[k] = it } }
            }
            backupStringSetKeys.forEach { k ->
                each(k) { prefs[k] = o.getJSONArray(k.name).let { arr -> buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) } } }
            }
            backupIntKeys.forEach { k -> each(k) { SettingsImportRules.int(k.name, o.getInt(k.name))?.let { prefs[k] = it } } }
            backupBoolKeys.forEach { k -> each(k) { prefs[k] = o.getBoolean(k.name) } }
            backupFloatKeys.forEach { k -> each(k) { SettingsImportRules.float(o.getDouble(k.name))?.let { prefs[k] = it } } }
            if (livePreviewPanelHidden(prefs)) {
                prefs[Keys.LIVE_PREVIEW] = false
            }
            // A restored latency / frame-rate choice is the user's own, exactly like one made in
            // Settings, so the v4.1.6 one-shot resets must neither hide it nor overwrite it later. The
            // pre-Android-12 AFR reset is deliberately left to run: it is a fact about this device.
            if (o.has(Keys.LIVE_LATENCY_MODE.name)) prefs[Keys.LIVE_LATENCY_RESET_416] = true
            if (o.has(Keys.AUTO_FRAME_RATE_LIVE.name) && !keepDeviceSettings) {
                prefs[Keys.AUTO_FRAME_RATE_RESET_416] = true
            }
        }
        // The last categories live in the position store. Guarded on read as well as on write: a file
        // written by another build (or edited by hand) must not be able to restore a "FOLDER:<id>" that
        // points at whatever this device's sync happens to have put behind that number.
        context.positionStore.edit { positions ->
            backupLastCategoryKeys.forEach { k ->
                if (o.has(k.name)) runCatching { o.getString(k.name).takeIf(::isPortableCategoryKey)?.let { positions[k] = it } }
            }
        }
        // Do not publish a locale while the restore is still applying database/DataStore sections.
        // The caller applies this validated value after the restore marker is cleared. Invalid data
        // is reported but ignored so a malformed optional field cannot abort an otherwise valid import.
        if (!o.has(UI_LANGUAGE_KEY)) return SettingsImportResult()
        val raw = o.opt(UI_LANGUAGE_KEY) as? String
        val normalized = raw?.let(localeStore::normalize)
        return SettingsImportResult(
            localePresent = true,
            localeTag = normalized,
            // Only a JSON string is a valid locale field. In particular, JSONObject.NULL and
            // numbers must not silently turn into the empty system-default tag.
            invalidLocale = raw == null || normalized == null,
        )
    }

    /** Applies a locale deferred until a complete backup restore has cleared its marker. */
    suspend fun applyImportedLocale(tag: String) {
        localeStore.set(tag)
    }

    companion object {
        /** Glass opacity when glass is on: the Stage default and Reset. */
        const val GLASS_ALPHA_DEFAULT_PCT = 50

        /** The writes of [applyStageDefaults] (except the picture file), apart so a unit test can check them. */
        internal fun stageDefaults(prefs: MutablePreferences) {
            prefs[Keys.NAV_STYLE] = NavStyle.DOCKED.name
            prefs[Keys.NAV_SIZE] = NavSize.COMPACT.name
            prefs[Keys.NAV_LENGTH] = NavLength.FIT.name
            prefs[Keys.GLASS_SCOPE] = tv.own.owntv.core.theme.GlassConfig.ON_BITS
            prefs[Keys.GLASS_ALPHA] = GLASS_ALPHA_DEFAULT_PCT
            prefs[Keys.BG_STYLE] = tv.own.owntv.core.theme.BackgroundStyle.STAGE.name
            prefs.remove(Keys.BG_LOOK); prefs.remove(Keys.BG_DIM); prefs.remove(Keys.BG_BLUR); prefs.remove(Keys.BG_ACCENT_LIGHT)
            prefs[Keys.STAGE_DEFAULTS_APPLIED] = true
        }

        /** The writes of [applyPhoneStageDefaults]; nothing at all unless this is a fresh install. */
        internal fun phoneStageDefaults(prefs: MutablePreferences, alphaPct: Int) {
            val fresh = prefs[Keys.GLASS_SCOPE] == null && prefs[Keys.BG_STYLE] == null
            if (!fresh) return
            prefs[Keys.GLASS_SCOPE] = tv.own.owntv.core.theme.GlassConfig.ON_BITS
            prefs[Keys.GLASS_ALPHA] = alphaPct
            prefs[Keys.BG_STYLE] = tv.own.owntv.core.theme.BackgroundStyle.STAGE.name
        }

        /** Backup payload field name for the UI locale tag (read from / written to [LocaleStore]). */
        private const val UI_LANGUAGE_KEY = "ui_language"


        /** The six toggles Quick started life with, kept as the out-of-the-box pin list. */
        private val DEFAULT_QUICK_PINNED = listOf(
            "quick_live_preview", "quick_preview_sound", "quick_channel_numbers",
            "quick_hdr", "quick_autoplay", "quick_check_update",
        )
    }

    // --- Backup repair after the SOURCES section lands (startup_mode/startup_channel/home_config/
    //     now ride with "settings" as plain keys; per-profile section maps are gone) ---

    /**
     * A restored SPECIFIC_CHANNEL must never point at a source that did not survive the restore.
     * BackupManager calls this once the SOURCES section has been applied, with the ids it has then.
     */
    suspend fun repairSpecificStartupChannel(existingSourceIds: Set<Long>) {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.STARTUP_MODE] != StartupMode.SPECIFIC_CHANNEL.name) return@edit
            val channel = StartupChannelRef.fromJson(prefs[Keys.STARTUP_CHANNEL])
            // The hex key survives restores, so the ref is valid while the channel itself is.
            if (channel == null) {
                prefs[Keys.STARTUP_MODE] = StartupMode.HOME.name
                prefs.remove(Keys.STARTUP_CHANNEL)
            }
        }
    }

    // --- Backup: proxy password (handled out-of-band by BackupManager: encrypted or omitted) ---

    /** Current proxy password, for the backup layer to encrypt. Never logged. */
    suspend fun currentProxyPassword(): String = context.dataStore.data.first()[Keys.PROXY_PASS] ?: ""

    /** Sets only the proxy password (used on restore once decrypted). Blank clears it. */
    suspend fun setProxyPassword(password: String) {
        context.dataStore.edit { it[Keys.PROXY_PASS] = password }
    }

    // --- Global custom DNS — sibling to the global proxy. Supports plain DNS-over-UDP (host + port)
    //     and DNS-over-HTTPS (DoH) via a URL. No auth is needed for DNS; the server field is not a
    //     secret (it's the DNS server the user wants to use). Backed by DnsConfigHolder, same pattern
    //     as ProxyConfigHolder. ---

    /** Live snapshot of the DNS config as a single object (consumed by DnsConfigHolder). */
    val dnsConfig: Flow<tv.own.owntv.core.network.DnsConfig> = prefsFlow { p ->
        tv.own.owntv.core.network.DnsConfig(
            enabled = p[Keys.DNS_ENABLED] ?: false,
            host = p[Keys.DNS_HOST] ?: "",
            port = p[Keys.DNS_PORT] ?: 53,
            dohUrl = p[Keys.DNS_DOH_URL] ?: "",
        )
    }

    suspend fun saveDns(enabled: Boolean, host: String, port: Int, dohUrl: String) {
        context.dataStore.edit {
            it[Keys.DNS_ENABLED] = enabled
            it[Keys.DNS_HOST] = host.trim()
            it[Keys.DNS_PORT] = port.coerceIn(1, 65535)
            it[Keys.DNS_DOH_URL] = dohUrl.trim()
        }
    }
}

