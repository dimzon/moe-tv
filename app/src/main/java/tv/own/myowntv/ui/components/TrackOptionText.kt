package tv.own.myowntv.ui.components
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
    val locale = if (configuration.locales.isEmpty) Locale.ENGLISH else configuration.locales[0]
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
