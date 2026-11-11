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
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
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

import kotlinx.coroutines.CancellationException
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.util.classifySyncFailure

/**
 * Shared semi-auto EPG sync used by both onboarding and Settings. The RuTube guide is not an XMLTV
 * feed: it comes from the API and is written straight into the guide tables
 * ([EpgRepository.refreshRutube]), so there is no EPG source row and no feed download — this just
 * runs the refresh while reporting a live programme count through [setState].
 */
suspend fun runSemiAutoEpgSync(
    source: SourceEntity,
    epgRepository: EpgRepository,
    setState: (EpgSyncUi) -> Unit,
) {
    setState(EpgSyncUi.Syncing(0))
    try {
        epgRepository.refresh(source) { _, count -> setState(EpgSyncUi.Syncing(count)) }
        setState(EpgSyncUi.Done)
    } catch (c: CancellationException) {
        throw c
    } catch (e: Exception) {
        setState(EpgSyncUi.Failed(classifySyncFailure(e.message, online = true)))
    }
}
