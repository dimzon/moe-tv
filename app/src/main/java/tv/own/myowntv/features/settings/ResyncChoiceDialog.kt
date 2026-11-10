package tv.own.myowntv.features.settings
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
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.theme.mpx

/**
 * Resync picker: an ordinary refresh, or one that is also allowed to drop titles the provider has
 * stopped listing.
 *
 * A normal resync will not remove more than half a source's rows, because a truncated provider
 * response is indistinguishable from a genuinely shrunken catalog and guessing wrong wipes a working
 * playlist. The consequence is that a provider that really did drop a lot of titles leaves them
 * behind with no way out — this dialog is that way out, kept behind a deliberate choice.
 *
 * Focus starts on the ordinary refresh, so the common case is still Select-then-Select and a
 * mis-press can't trigger removals. Back cancels.
 */
@Composable
internal fun ResyncChoiceDialog(
    sourceName: String,
    onNormal: () -> Unit,
    onClean: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_sources_resync_title_full, sourceName),
        body = stringResource(R.string.settings_sources_resync_description),
        buttons = { tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        tv.own.myowntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_sources_resync_now_full), onClick = onNormal, modifier = Modifier.focusRequester(focus),
            leading = { tv.own.myowntv.ui.stage.StagePopupIcon(MyOwnTVIcon.REFRESH) },
        )
        tv.own.myowntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_sources_resync_remove_full), onClick = onClean, danger = true,
            leading = { tv.own.myowntv.ui.stage.StagePopupIcon(MyOwnTVIcon.TRASH, tv.own.myowntv.ui.theme.StageColors.Danger) },
        )
    }
}
