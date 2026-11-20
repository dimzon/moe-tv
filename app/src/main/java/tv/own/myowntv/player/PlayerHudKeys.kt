package tv.own.myowntv.player

import androidx.compose.ui.input.key.Key

/**
 * Whether a KeyDown received while the player controls are hidden should reveal them and be consumed.
 *
 * The hidden HUD keeps a full-screen focus sink that treats any key as "show the controls", so a bare
 * OK works on remotes without dedicated playback keys. Volume is the exception: it belongs to the
 * framework's fallback handler. Consuming the first press showed the OSD without changing the volume,
 * and only the second press reached the system (forum report). Back stays with the shell's handler.
 * Kept pure so the policy is unit-testable without Compose.
 */
internal fun revealsPlayerHudOn(key: Key): Boolean =
    key != Key.Back &&
        key != Key.VolumeUp &&
        key != Key.VolumeDown &&
        key != Key.VolumeMute
