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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.stage.StageButton
import tv.own.myowntv.ui.stage.StagePopup
import tv.own.myowntv.ui.stage.StagePopupOption
import tv.own.myowntv.ui.stage.StageSwitch
import tv.own.myowntv.ui.theme.mpx

/**
 * A playlist's "Clean channel names" popup, opened from its row on the Playlists page: the switch,
 * then the user's own words to remove. Focus starts on the switch; Back cancels. Saving stores the
 * choice and, when the shown names change, hands the playlist to [onNeedsResync] so the next sync
 * rewrites them (cleaned, or the provider's originals again when switched off).
 */
@Composable
internal fun ChannelNameCleanupHost(
    source: SourceEntity,
    onNeedsResync: (SourceEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val vm: ChannelNameCleanupViewModel = koinViewModel()
    ChannelNameCleanupDialog(
        source = source,
        onSave = { enabled, text -> vm.save(source, enabled, text, onNeedsResync); onDismiss() },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ChannelNameCleanupDialog(
    source: SourceEntity,
    onSave: (enabled: Boolean, customText: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var enabled by remember { mutableStateOf(source.cleanChannelNames) }
    var customText by remember { mutableStateOf(source.channelNameStrip.orEmpty()) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { first.requestFocus() } }
    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_sources_clean_names_title, source.name),
        body = stringResource(R.string.settings_sources_clean_names_description),
        width = 820.mpx,
        buttons = {
            StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            StageButton(stringResource(R.string.common_save), onClick = { onSave(enabled, customText) }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        StagePopupOption(
            title = stringResource(R.string.settings_sources_clean_names_toggle),
            subtitle = stringResource(R.string.settings_sources_clean_names_example),
            onClick = { enabled = !enabled },
            modifier = Modifier.focusRequester(first),
            trailing = { StageSwitch(enabled) },
        )
        StageFieldRow(
            icon = MyOwnTVIcon.PENCIL,
            label = stringResource(R.string.settings_sources_clean_names_custom),
            value = customText,
            onValueChange = { customText = it },
            placeholder = stringResource(R.string.settings_sources_clean_names_custom_hint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
