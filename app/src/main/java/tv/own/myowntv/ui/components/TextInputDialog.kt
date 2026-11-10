package tv.own.myowntv.ui.components
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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import tv.own.myowntv.R
import tv.own.myowntv.ui.theme.mpx

/**
 * A simple TV dialog with one text field (e.g. renaming a channel/category). [onConfirm] receives the
 * trimmed text — possibly empty, which callers treat as "reset to original". [onDelete] is optional
 * and renders a destructive button (tinted with the app's favorite/error color) at the left end of
 * the button row — used by the custom-category rename dialog to delete the category.
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String? = null,
    confirmLabel: String? = null,
    hint: String? = null,
    onDelete: (() -> Unit)? = null,
    allowBlank: Boolean = true,
) {
    val resolvedLabel = label ?: stringResource(R.string.common_name)
    val resolvedConfirmLabel = confirmLabel ?: stringResource(R.string.common_save)
    var value by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    // Its own platform window: a hard focus boundary from the screen or popup it was opened from, so a
    // nested editor (Rule builder -> Rule value) can always reach its field. Focus waits for the window.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        runCatching { fieldFocus.requestFocus() }
    }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = title,
        body = hint,
        width = 820.mpx,
        buttons = {
            if (onDelete != null) tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_delete), onClick = onDelete, height = 56.mpx, textSize = 19)
            tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.myowntv.ui.stage.StageButton(resolvedConfirmLabel, onClick = { if (allowBlank || value.isNotBlank()) onConfirm(value.trim()) }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        tv.own.myowntv.features.settings.StageFieldRow(
            icon = tv.own.myowntv.ui.components.MyOwnTVIcon.PENCIL,
            label = resolvedLabel,
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
        )
    }
}
