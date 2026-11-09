package tv.own.myowntv.di

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
import tv.own.myowntv.features.downloads.DownloadsViewModel
import tv.own.myowntv.features.recordings.RecordingsViewModel
import tv.own.myowntv.features.epg.EpgViewModel
import tv.own.myowntv.core.content.SearchReader
import tv.own.myowntv.core.home.HomeFeedReader
import tv.own.myowntv.core.live.GuideReader
import tv.own.myowntv.core.live.LiveEpgReader
import tv.own.myowntv.features.home.HomeViewModel
import tv.own.myowntv.features.live.LiveViewModel
import tv.own.myowntv.features.movies.MovieViewModel
import tv.own.myowntv.features.profiles.ProfileGateSessionViewModel
import tv.own.myowntv.features.profiles.ProfilesViewModel
import tv.own.myowntv.features.search.SearchViewModel
import tv.own.myowntv.features.series.SeriesViewModel
import tv.own.myowntv.features.settings.LocalAutoSyncViewModel
import tv.own.myowntv.features.settings.LocalSyncViewModel
import tv.own.myowntv.features.settings.BackupViewModel
import tv.own.myowntv.features.settings.ChannelNameCleanupViewModel
import tv.own.myowntv.features.settings.DeleteSubtitlesViewModel
import tv.own.myowntv.features.settings.EpgSourcesViewModel
import tv.own.myowntv.features.settings.HomeSettingsViewModel
import tv.own.myowntv.features.settings.LanguageSettingsViewModel
import tv.own.myowntv.features.settings.OpenSubtitlesViewModel
import tv.own.myowntv.features.settings.PlayerButtonsSettingsViewModel
import tv.own.myowntv.features.settings.SettingsViewModel
import tv.own.myowntv.features.setup.DisplaySizeViewModel
import tv.own.myowntv.features.setup.SetupViewModel
import tv.own.myowntv.features.shell.ShellViewModel
import tv.own.myowntv.features.subtitles.SubtitleSearchViewModel

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
    // up to 22 constructor parameters and this class now has 24. The failure is a "none of the
    // following candidates is applicable" at the call above, which says nothing about arity — hence
    // this note. Every argument is resolved by type, so the order here does not matter.
    viewModel {
        LiveViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
    viewModelOf(::MovieViewModel)
    viewModelOf(::SportsViewModel)
    viewModelOf(::SportsSettingsViewModel)
    viewModelOf(::PlayerButtonsSettingsViewModel)
    viewModelOf(::SeriesViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::ProfilesViewModel)
    // Activity-scoped session state for the profile gate (configuration-only retention, no saved
    // state — see ProfileGateSessionViewModel).
    viewModelOf(::ProfileGateSessionViewModel)
    // 24 constructor parameters — past the highest arity Koin's `*Of` DSL generates, so this one
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
            vodEngineStore = get(),
            forceMpvStore = get(),
            playbackPrefs = get(),
            connectionLimits = get(),
            player = get(),
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
    viewModelOf(::OpenSubtitlesViewModel)
    viewModelOf(::DeleteSubtitlesViewModel)
    viewModelOf(::SubtitleSearchViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::RecordingsViewModel)
    // 23 constructor parameters — past the highest arity viewModelOf generates (see LiveViewModel).
    viewModel {
        EpgViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
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
