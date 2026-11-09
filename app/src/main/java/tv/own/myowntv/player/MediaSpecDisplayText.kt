package tv.own.myowntv.player
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*
import tv.own.owntv.player.ffmpeg.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.player.MediaSpec
import tv.own.owntv.player.DecoderSpec

@Composable
internal fun MediaSpec.displayText(): String {
    val decoderText = decoder?.let {
        when (it) {
            is DecoderSpec.Hardware -> buildList {
                add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }.joinToString(stringResource(R.string.player_metadata_separator))
            is DecoderSpec.Software -> buildList {
                add(stringResource(R.string.player_decoder_software))
                if (it.gpu) add(stringResource(R.string.player_decoder_gpu))
            }.joinToString(stringResource(R.string.player_metadata_separator))
            is DecoderSpec.Named -> buildList {
                add(
                    when (it.value.lowercase()) {
                        "exoplayer" -> stringResource(R.string.settings_player_exoplayer)
                        "mpv" -> stringResource(R.string.settings_player_mpv)
                        else -> it.value
                    },
                )
                if (it.hardware) add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }.joinToString(stringResource(R.string.player_metadata_separator))
        }
    }
    return listOfNotNull(codec, resolution, decoderText)
        .joinToString(stringResource(R.string.player_metadata_separator))
}
