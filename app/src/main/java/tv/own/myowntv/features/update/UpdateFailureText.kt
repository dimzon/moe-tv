package tv.own.myowntv.features.update
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

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.core.update.UpdateManager

@Composable
internal fun updateFailureText(failure: UpdateManager.Failure): String = when (failure) {
    is UpdateManager.Failure.CheckHttp -> stringResource(R.string.update_failed_check_http, failure.code.toString())
    UpdateManager.Failure.NoCompatibleApk -> stringResource(R.string.update_no_compatible_apk)
    UpdateManager.Failure.InvalidReleaseResponse -> stringResource(R.string.update_invalid_release_response)
    UpdateManager.Failure.CheckNetwork -> stringResource(R.string.update_failed_check)
    is UpdateManager.Failure.DownloadHttp -> stringResource(R.string.update_failed_download_http, failure.code.toString())
    UpdateManager.Failure.EmptyDownload -> stringResource(R.string.update_empty_download)
    UpdateManager.Failure.DownloadNetwork -> stringResource(R.string.update_failed_download)
    UpdateManager.Failure.Install -> stringResource(R.string.update_install_failed)
    is UpdateManager.Failure.NotEnoughSpace -> stringResource(
        R.string.update_not_enough_space,
        Formatter.formatShortFileSize(LocalContext.current, failure.requiredBytes),
    )
    UpdateManager.Failure.DamagedDownload -> stringResource(R.string.update_damaged_download)
    // The installer's own wording names the real cause when it gives one; fall back when it doesn't.
    is UpdateManager.Failure.InstallRejected -> failure.message
        ?.let { stringResource(R.string.update_install_rejected, it) }
        ?: stringResource(R.string.update_install_failed)
}
