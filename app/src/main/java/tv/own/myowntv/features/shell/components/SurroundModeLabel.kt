package tv.own.myowntv.features.shell.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.myowntv.core.player.SurroundMode

@Composable
internal fun surroundModeLabel(mode: SurroundMode): String = stringResource(
    when (mode) {
        SurroundMode.AUTO -> R.string.settings_auto
        SurroundMode.STEREO -> R.string.settings_surround_stereo
        SurroundMode.SURROUND -> R.string.settings_surround_sound
    },
)
