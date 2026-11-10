package tv.own.myowntv.features.shell.components
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
import tv.own.myowntv.R

/**
 * Shown at launch when a backup restore was interrupted. A restore spans the database and
 * several preference files, so an unlucky kill can leave it half-applied — a merge, so nothing was
 * deleted, but some sections may simply not be there. Rather than let that pass unnoticed, the app
 * says so and points at the fix: run the same restore again (it's idempotent — a re-run merges the
 * missing parts back in).
 *
 * [description] is the backup file name plus the sections that were being applied; never a secret.
 */
@Composable
fun IncompleteRestoreDialog(
    description: String,
    onDismiss: () -> Unit,
) {
    tv.own.myowntv.ui.stage.StageNotice(
        title = androidx.compose.ui.res.stringResource(R.string.content_restore_incomplete_title),
        body = androidx.compose.ui.res.stringResource(R.string.content_restore_incomplete_message, description),
        onDismiss = onDismiss,
        eyebrow = null,
    )
}
