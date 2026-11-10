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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.LockedNotificationTitles
import tv.own.owntv.core.download.DownloadActivityTracker
import tv.own.owntv.core.recording.RecordingActivityTracker
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.categorylock.namedIds
import tv.own.owntv.core.categorylock.sealedCategoryKeys

/** The running download and recordings the status pill may name; the others it labels as locked. */
internal data class PillNamedTitles(val downloads: Set<Long> = emptySet(), val recordings: Set<Long> = emptySet())

/**
 * Which running download and recordings [SyncStatusPill] may name. The notifications hide a locked
 * category's titles in every session; the pill is inside the app, behind the PIN, so it names a title
 * once the PIN has opened its category, and hides it again when the category is sealed.
 */
@Composable
internal fun rememberPillNamedTitles(downloads: DownloadActivityTracker, recordings: RecordingActivityTracker): PillNamedTitles {
    val customize: CustomizationStore = koinInject()
    val settings: SettingsRepository = koinInject()
    val titles: LockedNotificationTitles = koinInject()
    val named = remember(downloads, recordings, customize, settings, titles) {
        val locks = customize.sealedCategoryKeys(settings.activeProfileId)
        combine(
            namedIds(downloads.active.map { setOfNotNull(it?.id) }, locks) { titles.hidesDownload(it, anySession = false) },
            namedIds(recordings.active.map { it.keys }, locks) { titles.hidesRecording(it, anySession = false) },
        ) { d, r -> PillNamedTitles(d, r) }
    }
    val value by named.collectAsStateWithLifecycle(PillNamedTitles())
    return value
}
