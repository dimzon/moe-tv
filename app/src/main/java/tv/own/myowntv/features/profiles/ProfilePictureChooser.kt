package tv.own.myowntv.features.profiles
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.myowntv.R
import tv.own.myowntv.features.settings.SettingsViewModel
import tv.own.myowntv.ui.components.BackgroundImageChooserDialog
import tv.own.myowntv.ui.components.BrowseMode
import tv.own.myowntv.ui.components.RemoteBackgroundDialog
import tv.own.myowntv.ui.components.StorageBrowser
import java.io.File

/**
 * A picture for a profile, taken the same two ways the background image is: a file on this
 * television, or a photo a phone sends over the local network. Both hand [onPicked] a File, which
 * core copies and scales — nothing here knows or cares which one it was. The host closes this on
 * [onPicked] and [onClear]; Back from the phone page returns to the choice, Back elsewhere closes.
 *
 * One flow for every place a picture is chosen — the rail's avatar, the profile editor and the
 * setup wizard — so a new profile gets its picture exactly the way an existing one does.
 */
@Composable
internal fun ProfilePictureChooser(
    hasImage: Boolean,
    onPicked: (File) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var source by remember { mutableStateOf(Source.CHOOSE) }
    when (source) {
        Source.CHOOSE -> BackgroundImageChooserDialog(
            hasImage = hasImage,
            onPickLocal = { source = Source.LOCAL },
            onPickRemote = { source = Source.REMOTE },
            onClear = onClear,
            onDismiss = onDismiss,
        )
        Source.LOCAL -> StorageBrowser(
            title = stringResource(R.string.profiles_avatar_own_picture),
            mode = BrowseMode.FILE,
            fileExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp"),
            onPick = onPicked,
            onDismiss = onDismiss,
        )
        Source.REMOTE -> {
            // The same companion listener the background image uses — one PIN-protected upload page,
            // so a phone sends a profile picture exactly the way it already sends a wallpaper.
            val remoteVm = org.koin.androidx.compose.koinViewModel<SettingsViewModel>()
            val remoteState by remoteVm.remoteState.collectAsStateWithLifecycle()
            RemoteBackgroundDialog(
                state = remoteState,
                images = remoteVm.remoteImages,
                onStart = remoteVm::startRemoteImageListener,
                onStop = remoteVm::stopRemoteListener,
                onImageReceived = onPicked,
                onDismiss = { source = Source.CHOOSE },
            )
        }
    }
}

private enum class Source { CHOOSE, LOCAL, REMOTE }
