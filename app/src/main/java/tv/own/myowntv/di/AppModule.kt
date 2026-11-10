package tv.own.myowntv.di
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

import tv.own.myowntv.features.sports.SportsPlayback
import tv.own.myowntv.features.pip.PictureInPicture
import tv.own.myowntv.features.sports.SportsSettingsViewModel
import tv.own.myowntv.features.sports.SportsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import tv.own.myowntv.features.more.MoreCountsViewModel
import tv.own.myowntv.features.shell.RailCountsViewModel
import org.koin.dsl.module
import tv.own.myowntv.features.categorylock.CategoryLockViewModel
import tv.own.myowntv.features.customize.CustomizeItemsViewModel
import tv.own.myowntv.features.customize.CustomizeViewModel
import tv.own.myowntv.features.settings.lock.SettingsPinViewModel
import tv.own.myowntv.features.epg.EpgViewModel
import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.core.home.HomeFeedReader
import tv.own.owntv.core.live.GuideReader
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.myowntv.features.home.HomeViewModel
import tv.own.myowntv.features.live.LiveViewModel
import tv.own.myowntv.features.profiles.ProfileGateSessionViewModel
import tv.own.myowntv.features.profiles.ProfilesViewModel
import tv.own.myowntv.features.search.SearchViewModel
import tv.own.myowntv.features.settings.LocalAutoSyncViewModel
import tv.own.myowntv.features.settings.LocalSyncViewModel
import tv.own.myowntv.features.settings.BackupViewModel
import tv.own.myowntv.features.settings.ChannelNameCleanupViewModel
import tv.own.myowntv.features.settings.EpgSourcesViewModel
import tv.own.myowntv.features.settings.HomeSettingsViewModel
import tv.own.myowntv.features.settings.LanguageSettingsViewModel
import tv.own.myowntv.features.settings.PlayerButtonsSettingsViewModel
import tv.own.myowntv.features.settings.SettingsViewModel
import tv.own.myowntv.features.setup.DisplaySizeViewModel
import tv.own.myowntv.features.setup.SetupViewModel
import tv.own.myowntv.features.shell.ShellViewModel

/**
 * Root Koin module. Each feature will contribute its own bindings as the app grows;
 * for now this wires settings persistence and the shell view model.
 *
 * ViewModels are bound with `viewModelOf(::X)` constructor references, never positional `get()`
 * lists: with 15-23 parameters, a positional list only has to *count* right, so inserting or
 * reordering two same-typed parameters would silently swap two dependencies at runtime with no
 * compile error and no Koin error. A constructor reference binds by the declared constructor, so
 * reordering is safe and a missing binding fails immediately, naming the type.
 */
val appModule = module {
    viewModelOf(::ShellViewModel)
    // Home's rails are core's, shared with the phone app; the view model only decorates them.
    // LiveEpgReader is registered because GuideReader now needs one: a guide row whose stored data
    // has run out falls back to the provider's short-EPG, which is this reader's to fetch. One
    // instance, so the grid, the Live rows and the preview pane share its cache instead of each
    // asking the provider the same question.
    singleOf(::LiveEpgReader)
    singleOf(::GuideReader)
    singleOf(::SearchReader)
    singleOf(::HomeFeedReader)
    // Sports Hub: which match the playing channel came from. Lazy; nothing runs until it is asked.
    singleOf(::SportsPlayback)
    // Picture-in-picture when leaving the app: one instance shared by the activity and the shell.
    singleOf(::PictureInPicture)
    // Process-long scope for work that must outlive the screen that started it (a first-run restore).
    single<kotlinx.coroutines.CoroutineScope> {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::SetupViewModel)
    viewModelOf(::DisplaySizeViewModel)
    // Takes a Context first; Koin resolves it from androidContext().
    //
    // Spelled out rather than `viewModelOf(::LiveViewModel)`: that reflective helper is generated for
    // up to 22 constructor parameters and this class has 20. The failure is a "none of the
    // following candidates is applicable" at the call above, which says nothing about arity — hence
    // this note. Every argument is resolved by type, in declared order.
    viewModel {
        LiveViewModel(
            get(), // appContext
            get(), // channelDao
            get(), // categoryDao
            get(), // favoriteDao
            get(), // historyDao
            get(), // userDataWriter
            get(), // profileDao
            get(), // sourceDao
            get(), // settings
            get(), // customize
            get(), // launcherIntegrationRepository
            get(), // epgDao
            get(), // epgReader
            get(), // previewEngine
            get(), // contentOrderDao
            get(), // customCategoryDao
            get(), // streamUrlResolver
            get(), // epgRepository
            get(), // externalPlayerLauncher
            get(), // downloadWatchGuard
        )
    }
    viewModelOf(::SportsViewModel)
    viewModelOf(::SportsSettingsViewModel)
    viewModelOf(::PlayerButtonsSettingsViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::ProfilesViewModel)
    // Activity-scoped session state for the profile gate (configuration-only retention, no saved
    // state — see ProfileGateSessionViewModel).
    viewModelOf(::ProfileGateSessionViewModel)
    // 29 constructor parameters — past the highest arity Koin's `*Of` DSL generates, so this one
    // binding stays explicit. Named arguments give it the same guarantee viewModelOf gives the rest:
    // reordering the constructor is safe, and adding a parameter is a compile error here, not a
    // silently mis-wired dependency at runtime.
    viewModel {
        SettingsViewModel(
            profileDao = get(),
            sourceDao = get(),
            sourceRepository = get(),
            settings = get(),
            importFinalizer = get(),
            channelDao = get(),
            categoryDao = get(),
            customizationStore = get(),
            navVisibility = get(),
            userDataWriter = get(),
            epgRepository = get(),
            epgSourceStore = get(),
            launcherIntegrationRepository = get(),
            catalogSyncScheduler = get(),
            okHttpClient = get(),
            metadataProvider = get(),
            metadataRepository = get(),
            metadataBudget = get(),
            stalkerAuth = get(),
            stalkerClient = get(),
            xtreamClient = get(),
            sourceTester = get(),
            companion = get(),
            playbackPrefs = get(),
            connectionLimits = get(),
            livePreview = get(),
            enginePool = get(),
            importer = get(),
            sourceEditor = get(),
        )
    }
    viewModelOf(::LocalSyncViewModel)
    viewModelOf(::LocalAutoSyncViewModel)
    viewModelOf(::HomeSettingsViewModel)
    viewModelOf(::LanguageSettingsViewModel)
    // 18 constructor parameters — past the highest arity viewModelOf generates (see LiveViewModel).
    viewModel {
        EpgViewModel(
            get(), // settings
            get(), // sourceRepository
            get(), // channelDao
            get(), // epgDao
            get(), // profileDao
            get(), // epgRepository
            get(), // epgSourceStore
            get(), // connectivity
            get(), // customize
            get(), // sourceDao
            get(), // favoriteDao
            get(), // userDataWriter
            get(), // categoryDao
            get(), // customCategoryDao
            get(), // liveEpgReader
            get(), // epgActivity
            get(), // historyDao
            get(), // reminderManager
        )
    }
    viewModelOf(::CustomizeViewModel)
    viewModelOf(::SettingsPinViewModel)
    viewModelOf(::CustomizeItemsViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::EpgSourcesViewModel)
    viewModelOf(::ChannelNameCleanupViewModel)
    // The counts the More rows and the Favourites / History tabs carry.
    viewModelOf(::MoreCountsViewModel)
    // The counts beside the sections in the open Stage rail.
    viewModelOf(::RailCountsViewModel)
    viewModelOf(::CategoryLockViewModel)
    single { tv.own.myowntv.features.settings.lock.MenuChangeGate(get()) }
}
