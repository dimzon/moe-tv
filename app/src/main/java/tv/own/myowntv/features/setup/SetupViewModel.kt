package tv.own.myowntv.features.setup
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.setup.SourceImporter

/**
 * Drives first-run onboarding: bring in content — attach the built-in RuTube source and sync it, or
 * take the setup from another device (local sync, handled by the wizard itself). Sources are global —
 * there are no profiles — and [finish] only ends the wizard, which is what routes the app into the shell.
 *
 * The import sequence itself is core's [SourceImporter], shared with the mobile app; this holds the
 * things that are the television's own — the semi-auto EPG prompt and the job whose lifetime
 * "Run in background" depends on.
 */
class SetupViewModel(
    private val importer: SourceImporter,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
) : ViewModel() {

    val state: StateFlow<SourceImporter.ImportState> = importer.state
    val progress = importer.progress

    // Semi-auto EPG: after the source lands, offer a one-tap guide sync (with a live count) if it
    // has a guide feed.
    private var pendingEpgSource: SourceEntity? = null
    private val _epgSync = MutableStateFlow<tv.own.myowntv.features.settings.EpgSyncUi>(tv.own.myowntv.features.settings.EpgSyncUi.Hidden)
    val epgSync: StateFlow<tv.own.myowntv.features.settings.EpgSyncUi> = _epgSync.asStateFlow()

    private var importJob: Job? = null

    fun syncPendingEpg() {
        val src = pendingEpgSource ?: return
        viewModelScope.launch {
            tv.own.myowntv.features.settings.runSemiAutoEpgSync(src, epgRepository) { _epgSync.value = it }
        }
    }

    fun dismissPendingEpg() { pendingEpgSource = null; _epgSync.value = tv.own.myowntv.features.settings.EpgSyncUi.Hidden }

    /**
     * "Run in background" for the semi-auto EPG sync: enter the app now while the guide keeps
     * downloading. The sync launched by [syncPendingEpg] runs in this activity-scoped [viewModelScope],
     * so it survives leaving the wizard — exactly like the import [continueInBackground]. We only
     * finish onboarding; the in-flight job is deliberately not cancelled.
     */
    fun syncEpgInBackground(onDone: () -> Unit = {}) {
        pendingEpgSource = null
        finish(onDone)
    }

    /** RuTube: attach the single built-in source and sync it (no credentials, no address). */
    fun startRutube() = runImport { importer.ensureRutube() }

    /**
     * Runs one import in the activity-scoped [viewModelScope] so "Run in background" can walk away
     * from it, then raises the semi-auto EPG prompt if the source that just landed has a guide feed.
     */
    private fun runImport(block: suspend () -> Unit) {
        importJob?.cancel()
        val job = viewModelScope.launch {
            block()
            val done = importer.state.value as? SourceImporter.ImportState.Success ?: return@launch
            val source = done.source ?: return@launch
            if (!importer.backgroundHandoff && epgRepository.hasGuide(source)) {
                pendingEpgSource = source
                _epgSync.value = tv.own.myowntv.features.settings.EpgSyncUi.Ask(source.name)
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    fun reset() = importer.reset()

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        importer.reset()
    }

    /**
     * "Run in background": enter the app now while the import keeps running. This ViewModel is
     * activity-scoped, so the in-flight import survives leaving the wizard — the sync continues
     * exactly as if the user had waited (success still runs ImportFinalizer + the remainder enqueue).
     * Deliberately does NOT cancel: cancelling would delete the half-imported source. The semi-auto
     * EPG prompt is skipped (its dialog lives in the wizard); EPG stays user-initiated.
     */
    fun continueInBackground(onDone: () -> Unit = {}) {
        importer.backgroundHandoff = true
        dismissPendingEpg()
        finish(onDone)
    }

    /** Completes onboarding, routing the app into the shell. */
    fun finish(onDone: () -> Unit = {}) {
        viewModelScope.launch { importer.finish(); onDone() }
    }
}
