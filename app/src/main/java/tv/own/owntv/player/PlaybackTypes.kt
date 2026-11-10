package tv.own.owntv.player

import androidx.compose.runtime.Immutable

/**
 * Types the live engine and the HUD share. They used to be declared next to the old second player;
 * the live ExoPlayer engine and the interface it serves need them exactly the same.
 */

/** What a row in a track menu shows: an audio pick or a subtitle pick. */
enum class TrackLabelKind { AUDIO, SUBTITLE }

@Immutable
data class TrackOption(
    /** Raw engine/provider label. It is an identity, not translated in the player engine. */
    val label: String,
    /** The engine's own track id — ExoPlayer's renderer/group ordinal for a selection. */
    val id: Int,
    val selected: Boolean,
    /** Flags an image-based subtitle (PGS/VOBSUB/DVB) — ExoPlayer renders those on its own layer. */
    val image: Boolean = false,
    val codec: String? = null,
    val lang: String? = null,
    /** The track's 0-based position among tracks of its own type. */
    val typeIndex: Int = -1,
    val labelKind: TrackLabelKind = TrackLabelKind.AUDIO,
)

/** Metadata shown in the player HUD (breadcrumb path, channel logo). */
@Immutable
data class MediaMeta(
    val title: String? = null,
    val subtitle: String? = null,
    val logoUrl: String? = null,
    /** Stable per-channel identity — the key the remembered zoom, volume, A/V delay and track
     *  languages are filed under. Null falls back to the stream URL, which is what every channel
     *  used to be keyed on and is still correct for M3U/Xtream. */
    val contentKey: String? = null,
)

/** Video scaling modes exposed in the player's zoom menu. */
enum class ZoomMode(@param:androidx.annotation.StringRes val labelRes: Int) {
    FIT(tv.own.myowntv.R.string.player_zoom_fit_screen), FILL(tv.own.myowntv.R.string.player_zoom_fill_crop), STRETCH(tv.own.myowntv.R.string.player_zoom_stretch),
    ORIGINAL(tv.own.myowntv.R.string.player_zoom_original), FORCE_16_9(tv.own.myowntv.R.string.player_zoom_force_16_9), FORCE_4_3(tv.own.myowntv.R.string.player_zoom_force_4_3),
}
