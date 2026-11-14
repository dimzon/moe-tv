package tv.own.myowntv.di
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

import tv.own.myowntv.features.pip.PictureInPicture
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
import tv.own.myowntv.features.search.SearchViewModel
import tv.own.myowntv.features.settings.LocalAutoSyncViewModel
import tv.own.myowntv.features.settings.LocalSyncViewModel
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
    // LiveEpgReader keeps the preview pane's now/next cache; the guide grid reads the stored table
    // directly. One instance, so the Live rows and the preview pane share the cache.
    singleOf(::LiveEpgReader)
    singleOf(::GuideReader)
    singleOf(::SearchReader)
    singleOf(::HomeFeedReader)
    // Picture-in-picture when leaving the app: one instance shared by the activity and the shell.
    singleOf(::PictureInPicture)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SetupViewModel)
    viewModelOf(::DisplaySizeViewModel)
    // Takes a Context first; Koin resolves it from androidContext().
    //
    // Spelled out rather than `viewModelOf(::LiveViewModel)`: that reflective helper is generated for
    // up to 22 constructor parameters and this class is near the ceiling. The failure is a "none of the
    // following candidates is applicable" at the call above, which says nothing about arity — hence
    // this note. Every argument is resolved by type, in declared order. Profiles are gone: no
    // profileDao argument any more.
    viewModel {
        LiveViewModel(
            get(), // appContext
            get(), // channelDao
            get(), // categoryDao
            get(), // favoriteDao
            get(), // historyDao
            get(), // userDataWriter
            get(), // settings
            get(), // customize
            get(), // epgDao
            get(), // epgReader
            get(), // previewEngine
            get(), // contentOrderDao
            get(), // customCategoryDao
            get(), // streamUrlResolver
            get(), // epgRepository
            get(), // externalPlayerLauncher
        )
    }
    viewModelOf(::PlayerButtonsSettingsViewModel)
    viewModelOf(::SearchViewModel)
    // Past the highest arity Koin's `*Of` DSL generates, so this binding stays explicit. Named
    // arguments give it the same guarantee viewModelOf gives the rest: reordering the constructor is
    // safe, and adding a parameter is a compile error here, not a silently mis-wired dependency at
    // runtime. Profiles are gone: no profileDao argument any more.
    viewModel {
        SettingsViewModel(
            sourceRepository = get(),
            settings = get(),
            channelDao = get(),
            categoryDao = get(),
            customizationStore = get(),
            navVisibility = get(),
            userDataWriter = get(),
            okHttpClient = get(),
            companion = get(),
            playbackPrefs = get(),
            sourceImporter = get(),
        )
    }
    viewModelOf(::LocalSyncViewModel)
    viewModelOf(::LocalAutoSyncViewModel)
    viewModelOf(::HomeSettingsViewModel)
    viewModelOf(::LanguageSettingsViewModel)
    // Past the highest arity viewModelOf generates (see LiveViewModel). Profiles are gone: no
    // profileDao argument any more.
    viewModel {
        EpgViewModel(
            get(), // settings
            get(), // channelDao
            get(), // epgDao
            get(), // epgRepository
            get(), // customize
            get(), // favoriteDao
            get(), // userDataWriter
            get(), // categoryDao
            get(), // customCategoryDao
            get(), // epgActivity
            get(), // historyDao
            get(), // reminderManager
        )
    }
    viewModelOf(::CustomizeViewModel)
    viewModelOf(::SettingsPinViewModel)
    viewModelOf(::CustomizeItemsViewModel)
    // The counts the More rows and the Favourites / History tabs carry.
    viewModelOf(::MoreCountsViewModel)
    // The counts beside the sections in the open Stage rail.
    viewModelOf(::RailCountsViewModel)
    viewModelOf(::CategoryLockViewModel)
    single { tv.own.myowntv.features.settings.lock.MenuChangeGate(get()) }
}
