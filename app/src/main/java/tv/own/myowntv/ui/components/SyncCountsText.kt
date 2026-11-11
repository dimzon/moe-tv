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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.SyncProgressDisplay
import tv.own.owntv.core.sync.SyncWarning
import tv.own.owntv.core.sync.breakdownText
import tv.own.owntv.core.sync.compactCount
import tv.own.owntv.core.sync.detailText
import tv.own.owntv.core.sync.displayText
import tv.own.owntv.core.sync.labelText
import tv.own.owntv.core.sync.primaryText
import tv.own.owntv.core.sync.remainderText
import tv.own.owntv.core.sync.summaryText
import tv.own.owntv.core.sync.warningText

/**
 * Import counts at the Compose presentation boundary. The sentences themselves are core's, shared
 * with the mobile app; these are only the composable wrappers that reach for the resources.
 */

@Composable
fun SyncCounts.breakdownText(includeEpg: Boolean = false): String =
    breakdownText(LocalContext.current.resources, includeEpg)

@Composable
fun SyncCounts.summaryText(includeEpg: Boolean = false): String =
    summaryText(LocalContext.current.resources, includeEpg)

@Composable
fun SyncProgressCounts.displayText(): String = displayText(LocalContext.current.resources)

internal fun compactCount(context: Context, value: Int): String = compactCount(context.resources, value)

@Composable
fun SyncProgressDisplay.primaryText(): String = primaryText(LocalContext.current.resources)

@Composable
fun SyncProgressDisplay.detailText(): String = detailText(LocalContext.current.resources)

@Composable
fun SyncWarning.labelText(): String = labelText(LocalContext.current.resources)

@Composable
fun List<SyncWarning>.warningText(): String? = warningText(LocalContext.current.resources)

@Composable
fun SyncContentTypes.remainderText(): String? = remainderText(LocalContext.current.resources)
