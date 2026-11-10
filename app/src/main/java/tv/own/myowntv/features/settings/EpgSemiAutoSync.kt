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

import kotlinx.coroutines.CancellationException
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.util.classifySyncFailure

/**
 * Shared semi-auto EPG sync used by both onboarding and Settings → Playlists. Registers the playlist's own
 * guide feed as an [EpgSource][tv.own.owntv.core.epg.EpgSource] in the [store] (re-using an existing entry
 * with the same URL) so it appears in Settings → EPG Sources, then downloads it keyed by that source id while
 * reporting a live programme count through [setState].
 */
suspend fun runSemiAutoEpgSync(
    source: SourceEntity,
    epgRepository: EpgRepository,
    store: EpgSourceStore,
    setState: (EpgSyncUi) -> Unit,
) {
    // The built-in RuTube guide is not an XMLTV feed: it comes from the API and is written straight
    // into the guide tables. No EPG source row, no download.
    if (source.type == tv.own.owntv.core.model.SourceType.RUTUBE) {
        setState(EpgSyncUi.Syncing(0))
        try {
            val written = epgRepository.refresh(source) { _, count -> setState(EpgSyncUi.Syncing(count)) }
            setState(EpgSyncUi.Done)
            return
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            setState(EpgSyncUi.Failed(tv.own.owntv.core.util.classifySyncFailure(e.message, online = true)))
            return
        }
    }
    // A playlist header may advertise several feeds in one comma-separated url-tvg; each is its own
    // EPG source, since only one address can be downloaded at a time.
    val urls = epgRepository.guideUrls(source)
    if (urls.isEmpty()) { setState(EpgSyncUi.Done); return }
    setState(EpgSyncUi.Syncing(0))
    var total = 0
    var anySucceeded = false
    var failure: Exception? = null
    for (url in urls) {
        // Show up in Settings → EPG Sources like any other feed (so the user can re-sync / delete it there).
        // Two feeds of one playlist share its name and are told apart by the URL shown beneath it.
        val epgSource = store.getAll().firstOrNull { it.url == url } ?: store.add(source.name, url, source.userAgent)
        val now = System.currentTimeMillis()
        try {
            val written = epgRepository.refreshUrl(epgSource.id, epgSource.url, epgSource.userAgent) { _, count ->
                setState(EpgSyncUi.Syncing(total + count))
            }
            store.setSynced(epgSource.id, now, null)
            total += written
            anySucceeded = true
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            // One dead feed must not cost the user the ones that do work — remember it and carry on.
            store.setSynced(epgSource.id, now, e.message)
            failure = e
        }
    }
    // Only report failure when nothing came through at all; one dead feed beside a working one is
    // already visible as that feed's error line in Settings → EPG Sources.
    val e = failure
    setState(if (e != null && !anySucceeded) EpgSyncUi.Failed(classifySyncFailure(e.message, online = true)) else EpgSyncUi.Done)
}
