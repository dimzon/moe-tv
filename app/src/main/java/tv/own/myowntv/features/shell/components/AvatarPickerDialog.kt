package tv.own.myowntv.features.shell.components
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.MyOwnTVAvatar
import tv.own.myowntv.ui.components.ProfileIcon
import tv.own.myowntv.ui.components.MyOwnTVAvatars
import tv.own.myowntv.ui.components.longPressMenuGuard

/** Full-screen avatar picker: a grid of the preset cartoon avatars. Picking one applies & closes. */
@Composable
fun AvatarPickerDialog(
    selectedId: Int,
    onSelect: (Int) -> Unit,
    // A picture of the user's own, when this profile has one — it takes the place of the drawn tile
    // wherever the avatar appears. Blank means none, and the two callbacks below are absent when the
    // host has nowhere to pick a picture from.
    customPath: String = "",
    onPickCustom: (() -> Unit)? = null,
    onClearCustom: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_avatar_picker_title),
        width = 760.mpx,
        modifier = Modifier.longPressMenuGuard(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.mpx)) {
            // The "no avatar" option showing the Rank 1 ProfileIcon (ID -1)
            AvatarTile(selected = selectedId == -1, onClick = { onSelect(-1); onDismiss() }, focus = selectedFocus.takeIf { selectedId == -1 }) {
                ProfileIcon(color = stageAccent.accent, modifier = Modifier.size(56.mpx))
            }

            // A picture of your own, alongside the drawn set. Same two ways in as the background
            // image — a file on this device, or a photo sent from a phone — so there is one idea to
            // learn rather than two; the host supplies that chooser.
            if (onPickCustom != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.mpx), verticalAlignment = Alignment.CenterVertically) {
                    AvatarTile(selected = customPath.isNotBlank(), onClick = onPickCustom) {
                        if (customPath.isNotBlank()) {
                            MyOwnTVAvatar(avatarId = selectedId, imagePath = customPath, modifier = Modifier.size(90.mpx))
                        } else {
                            ProfileIcon(color = StageColors.Muted, modifier = Modifier.size(56.mpx))
                        }
                    }
                    Text(
                        text = stringResource(R.string.profiles_avatar_own_picture),
                        style = stageText(19, 600),
                        color = StageColors.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (customPath.isNotBlank() && onClearCustom != null) {
                        tv.own.myowntv.ui.stage.StagePopupButton(
                            stringResource(R.string.common_clear),
                            onClick = onClearCustom,
                            primary = false,
                        )
                    }
                }
            }

            (0 until MyOwnTVAvatars.COUNT).toList().chunked(5).forEach { rowIds ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    rowIds.forEach { id ->
                        val isSelected = id == selectedId
                        AvatarTile(selected = isSelected, onClick = { onSelect(id); onDismiss() }, focus = selectedFocus.takeIf { isSelected }) {
                            MyOwnTVAvatar(avatarId = id, modifier = Modifier.size(90.mpx))
                        }
                    }
                }
            }
        }
    }
}

/** One choice: a 120 px Stage tile, ringed in accent while it is the current avatar. */
@Composable
private fun AvatarTile(selected: Boolean, onClick: () -> Unit, focus: FocusRequester? = null, content: @Composable () -> Unit) {
    val accent = stageAccent.accent
    tv.own.myowntv.ui.stage.StageSurface(
        onClick = onClick,
        radius = 28.mpx,
        modifier = (focus?.let { Modifier.focusRequester(it) } ?: Modifier).size(120.mpx),
        focusStyle = tv.own.myowntv.ui.stage.StageFocus.FX,
        idle = Modifier
            .background(StageColors.ControlFill, RoundedCornerShape(28.mpx))
            .then(if (selected) Modifier.border(3.mpx, accent, RoundedCornerShape(28.mpx)) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}
