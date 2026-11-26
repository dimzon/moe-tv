package tv.own.myowntv.ui.components

import tv.own.owntv.core.i18n.LocaleCompat
import tv.own.owntv.core.i18n.firstLocaleCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.player.TrackLabelKind
import tv.own.owntv.player.TrackOption
import java.util.Locale

/**
 * Final player presentation for an engine-owned track row. Engines expose raw labels, language
 * codes and stable ordinals; only this Compose mapper supplies localized fallback and source words.
 */
@Composable
fun TrackOption.displayLabel(): String {
    val configuration = LocalResources.current.configuration
    val locale = if (LocaleCompat.listOf(configuration).isEmpty) Locale.ENGLISH else configuration.firstLocaleCompat()
    val raw = label.takeIf { it.isNotBlank() }
    val language = lang
        ?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
        ?.let { code ->
            runCatching { Locale.forLanguageTag(code).getDisplayLanguage(locale) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: code.uppercase(locale)
        }
    val fallback = when (labelKind) {
        TrackLabelKind.AUDIO -> stringResource(R.string.player_audio_track_number, displayNumber())
        TrackLabelKind.SUBTITLE -> stringResource(R.string.player_subtitle_track_number, displayNumber())
    }
    // External subs already carry their source in the raw label's `OS_`/`LOCAL_` prefix (see
    // SubtitleTrackLabel), so appending a source word here would state it twice on every row.
    return raw ?: language ?: fallback
}

private fun TrackOption.displayNumber(): Int = (typeIndex.takeIf { it >= 0 } ?: id) + 1
