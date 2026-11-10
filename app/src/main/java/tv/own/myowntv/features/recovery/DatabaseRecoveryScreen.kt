package tv.own.myowntv.features.recovery
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

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R

/**
 * Shown instead of the shell when the database cannot be opened — a migration that failed, or a file
 * SQLite refuses. The app used to wipe itself in this situation (`fallbackToDestructiveMigration`);
 * it no longer does, so the user's data is still on the device and the fix is a code fix, not a
 * reinstall. This screen says exactly that and offers a retry, with a clearly-labelled last-resort
 * reset that only happens if the user asks for it twice.
 */
@Composable
fun DatabaseRecoveryScreen(
    message: String?,
    onRetry: () -> Unit,
    onResetData: () -> Unit,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp).padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.recovery_title), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                stringResource(R.string.recovery_data_safe_full),
                fontSize = 18.sp,
                color = Color(0xFFCFCFD6),
            )
            if (!message.isNullOrBlank()) {
                Text(message, fontSize = 14.sp, color = Color(0xFF8A8A94))
            }
            Button(onClick = onRetry, modifier = Modifier.focusRequester(focus).focusable()) {
                Text(stringResource(R.string.recovery_retry))
            }
            Text(
                stringResource(R.string.recovery_backup_hint),
                fontSize = 14.sp,
                color = Color(0xFF8A8A94),
            )
            Button(onClick = { if (confirmingReset) onResetData() else confirmingReset = true }) {
                Text(stringResource(if (confirmingReset) R.string.recovery_confirm_reset else R.string.recovery_reset))
            }
        }
    }
}
