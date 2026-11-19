package tv.own.owntv.core.di

import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.BulkInsertHelper
import tv.own.owntv.core.backup.UserDataResolver
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.core.sync.SyncManager
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncScheduler
import tv.own.owntv.core.weather.WeatherRepository
import java.util.concurrent.TimeUnit

/** Networking, parsers, sync engine, and repositories. */
val dataModule = module {
    // Live snapshot of the global proxy. Backs OkHttp's ProxySelector/Authenticator,
    // so the proxy can be toggled at runtime without rebuilding the singleton OkHttpClient below.
    single { tv.own.owntv.core.network.ProxyConfigHolder(get<tv.own.owntv.core.settings.SettingsRepository>().proxyConfig) }
    // Live snapshot of the global custom DNS (plain UDP or DoH). Same pattern as proxy — reads the
    // live DataStore snapshot so DNS can be toggled without rebuilding the OkHttpClient singleton.
    single { tv.own.owntv.core.network.DnsConfigHolder(get<tv.own.owntv.core.settings.SettingsRepository>().dnsConfig) }
    single {
        val proxyHolder = get<tv.own.owntv.core.network.ProxyConfigHolder>()
        val dnsHolder = get<tv.own.owntv.core.network.DnsConfigHolder>()
        val trust = tv.own.owntv.core.network.ExtraTrustAnchors(
            androidContext().resources.openRawResource(tv.own.myowntv.R.raw.isrg_extra_roots),
        )
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)  // fast fail on dead host
            .readTimeout(20, TimeUnit.SECONDS)    // detect mid-sync disconnect quickly
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)       // let SyncManager handle retries, not OkHttp
            // Global proxy (Approach 1): a ProxySelector/Authenticator that read the live snapshot, so
            // enabling/disabling the proxy takes effect immediately. Proxy off = DIRECT = exact prior
            // behavior. Credentials are never logged.
            .proxySelector(proxyHolder.proxySelector)
            .proxyAuthenticator(proxyHolder.proxyAuthenticator)
            // Global custom DNS: a Dns that reads the live snapshot so the DNS server can be changed
            // at runtime without rebuilding this singleton client. Off = system DNS = exact prior behavior.
            .dns(dnsHolder.dns)
            // Platform trust plus the Let's Encrypt roots older/current devices lack. Clients
            // derived with newBuilder() (streaming, players) inherit it.
            .sslSocketFactory(trust.sslSocketFactory, trust.trustManager)
            // Force HTTP/1.1. Several IPTV panels / EPG hosts (and their CDNs) have flaky HTTP/2 stacks
            // that send RST_STREAM(PROTOCOL_ERROR) on large/slow responses — e.g. big EPG XML downloads
            // which OkHttp surfaces as "stream was reset: PROTOCOL_ERROR". HTTP/1.1 sidesteps it
            // with no real downside for our mostly-single-stream downloads.
            .protocols(listOf(Protocol.HTTP_1_1))
            // Default a player-style UA for any request that didn't set one (e.g. Coil image loads),
            // since some IPTV panels reject the stock OkHttp UA. Per-source UAs still override this.
            .addInterceptor { chain ->
                val req = chain.request()
                val out = if (req.header("User-Agent").isNullOrBlank()) {
                    req.newBuilder().header("User-Agent", HttpClient.DEFAULT_USER_AGENT).build()
                } else {
                    req
                }
                chain.proceed(out)
            }
            .build()
    }
    // The playback client: same proxy/DNS/UA/protocol configuration as the singleton above,
    // but its own connection pool, so a live stop can evict *stream* sockets without dropping keep-alive
    // for EPG, panel API and image traffic.
    single { tv.own.owntv.core.network.StreamingHttpClient(get()) }
    single { HttpClient(get()) }
    // RuTube's API must never go through a proxy (false blocking_rule 404s), so it gets its own
    // no-proxy OkHttpClient under a name, wrapped by a named HttpClient for the RuTube stack.
    single(org.koin.core.qualifier.named("rutubeOkHttp")) {
        val trust = tv.own.owntv.core.network.ExtraTrustAnchors(
            androidContext().resources.openRawResource(tv.own.myowntv.R.raw.isrg_extra_roots),
        )
        okhttp3.OkHttpClient.Builder()
            .proxy(java.net.Proxy.NO_PROXY)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .sslSocketFactory(trust.sslSocketFactory, trust.trustManager)
            .build()
    }
    single(org.koin.core.qualifier.named("rutube")) {
        tv.own.owntv.core.network.HttpClient(get(org.koin.core.qualifier.named("rutubeOkHttp")))
    }
    single { tv.own.owntv.core.sync.RutubeApi(get(org.koin.core.qualifier.named("rutube"))) }
    single { tv.own.owntv.core.sync.RutubePlaybackResolver(get()) }
    single { ConnectivityObserver(androidContext()) }
    // Category locks: the in-memory unlocks of this session, and the view that applies them (settings,
    // session, categoryDao).
    single { tv.own.owntv.core.customize.CategoryUnlockSession() }
    single { tv.own.owntv.core.categorylock.MenuChangeGuard(get(), get(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)) }
    single { tv.own.owntv.core.customize.CategoryLockView(get(), get(), get()) }
    // One count of wrong PINs per lock (profile, category, Settings) for the whole app, so moving to
    // another category or section does not start a fresh run of guesses, and guessing at one lock never
    // makes another wait. Monotonic clock: changing the time cannot end a wait.
    single { tv.own.owntv.core.categorylock.PinAttemptLimiters(android.os.SystemClock::elapsedRealtime) }
    // Which locked category a file the player is on belongs to, for the relock.
    single { tv.own.owntv.core.categorylock.PlayingFileCategory() }
    single { CustomizationStore(androidContext(), get()) }
    // RuTube's stream resolver: it alone decides whether a playback URL needs a playback session
    // (needsResolve = RuTube) and how captions resolve.
    single { tv.own.owntv.core.stalker.StreamUrlResolver(get()) }
    // Per-channel "watched without a picture" memory, for the mobile audio-only mode.
    single { tv.own.owntv.core.player.AudioOnlyStore(get()) }
    // Per-item zoom/volume the player remembers (playbackPrefsDao, settings).
    single { tv.own.owntv.core.player.PlaybackPrefsStore(dao = get(), quirks = get()) }
    single { tv.own.owntv.core.player.ExternalPlayerLauncher(androidContext()) }
    single { WeatherRepository(get(), get()) }
    // Remote (companion) LAN server — one shared instance for the Settings screens that host it.
    single { tv.own.owntv.core.companion.CompanionController(androidContext(), get()) }
    // Local sync between two OwnTV devices on the same Wi-Fi. The payload is a
    // backup container, the listener is the companion server, and the merge is the restore path —
    // what is new here is the client, the pairing and the discovery.
    single { tv.own.owntv.core.sync.local.PairedDeviceStore(androidContext()) }
    single { tv.own.owntv.core.sync.local.LocalSyncClient() }
    single { tv.own.owntv.core.sync.local.LocalSyncDiscovery(androidContext()) }
    single {
        tv.own.owntv.core.sync.local.LocalSyncManager(
            context = androidContext(),
            companion = get(),
            backups = get(),
            paired = get(),
            client = get(),
            discovery = get(),
            autoSync = get(),
            autoServer = get(),
            settings = get(),
        )
    }
    // Automatic sync with paired devices: its agreements, the paired-only listener on its own port,
    // the passes, and the moments they are tried. The listener and the runner each announce or look
    // for the automatic-sync service, never the manual one.
    single { tv.own.owntv.core.sync.local.LocalAutoSyncStore(androidContext(), get()) }
    single { tv.own.owntv.core.sync.local.AutoSyncLocks() }
    single { tv.own.owntv.core.sync.local.LocalAutoSyncClient() }
    single { tv.own.owntv.core.sync.local.AutoSyncTransfer(get(), get()) { androidContext().cacheDir } }
    single {
        tv.own.owntv.core.sync.local.LocalAutoSyncServer(
            store = get(),
            paired = get(),
            transfer = get(),
            locks = get(),
            discovery = tv.own.owntv.core.sync.local.LocalSyncDiscovery(androidContext(), tv.own.owntv.core.sync.local.LocalSyncDiscovery.AUTO_SERVICE_TYPE),
            deviceName = { tv.own.owntv.core.sync.local.localDeviceName(androidContext()) },
        )
    }
    single {
        tv.own.owntv.core.sync.local.LocalAutoSyncRunner(
            store = get(),
            paired = get(),
            client = get(),
            transfer = get(),
            locks = get(),
            discovery = tv.own.owntv.core.sync.local.LocalSyncDiscovery(androidContext(), tv.own.owntv.core.sync.local.LocalSyncDiscovery.AUTO_SERVICE_TYPE),
        )
    }
    single { tv.own.owntv.core.sync.work.LocalAutoSyncScheduler(androidContext()) }
    single { tv.own.owntv.core.sync.work.LocalAutoSyncStarter(androidContext(), get(), get(), get()) }
    single { BulkInsertHelper(get()) }
    // Live `hex → categories` map from channel_category, for the M:N visibility filters (redesign §2).
    single { tv.own.owntv.core.database.ChannelMembershipStore(get()) }
    single {
        tv.own.owntv.core.sync.ImportFinalizer(
            channelDao = get(),
            db = get(),
            bulkInsertHelper = get(),
        )
    }
    // context, channelDao, favoriteDao, historyDao, contentOrderDao, customCategoryDao,
    // tombstoneDao, db
    single { UserDataResolver(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    // The one place a user deletion is written: it records the tombstone local sync needs (gated by
    // the sync matrix's "give" flags) and does the delete in the same transaction.
    // db, favoriteDao, historyDao, customCategoryDao, userData, settings
    single { tv.own.owntv.core.backup.UserDataWriter(get(), get(), get(), get(), get(), get()) }
    // settings, syncManager, userDataResolver, channelDao, categoryDao (the DTO adapter, no DAO)
    single { SourceRepository(get(), get(), get(), get(), get()) }
    // settings, sourceRepository, channelDao
    single { tv.own.owntv.core.nav.NavVisibility(get(), get(), get()) }
    single {
        SyncManager(
            categoryDao = get(),
            channelDao = get(),
            channelCategoryDao = get(),
            channelRenameDao = get(),
            bulkInsertHelper = get(),
            activityTracker = get(),
            customize = get(),
            settings = get(),
            rutubeApi = get(),
            renameSeeds = tv.own.owntv.core.sync.ChannelRenameSeeds.load(androidContext()),
        )
    }
    single { tv.own.owntv.core.sync.RuTubeEpgSyncer(get(), get()) }
    // App-wide "sync running" signal for the shell status pill (every sync funnels through SyncManager).
    single { tv.own.owntv.core.sync.SyncActivityTracker() }
    // Same idea for EPG: EpgSyncWorker reports started/progress/finished here so the pill also reflects
    // guide/EPG downloads (manual resync from Settings, auto startup refresh, …).
    single { tv.own.owntv.core.sync.EpgActivityTracker() }
    // epgDao, channelDao, rutubeApi, settings — the one provider's guide, written by RuTubeEpgSyncer.
    single {
        EpgRepository(
            epgDao = get(),
            channelDao = get(),
            rutube = get(),
            settings = get(),
        )
    }
    // Who currently holds a stream on which playlist. Multiview tiles and the main playback spend the
    // same provider connections, so both count against one register.
    single { tv.own.owntv.core.live.OpenStreamRegistry() }
    // What the user is actually watching. Separate from the register above on purpose — see the
    // class doc: that one is the connection budget, this one is a playback signal.
    single { tv.own.owntv.core.live.WatchSession() }
    // Programme reminders: context, reminderDao — then reminderDao, scheduler. Not eager: the app
    // warms this on an IO scope at launch so every launch still re-arms, without opening the DB and
    // AlarmManager during startKoin on the main thread (see MyOwnTVApp).
    single { tv.own.owntv.core.reminder.ReminderScheduler(androidContext(), get()) }
    single { tv.own.owntv.core.reminder.ReminderManager(get(), get()) }
    // settings, userDataResolver — the four-section backup container (favorites / history / manual
    // reorder / reminders). The customizations, the settings block and the wallpaper left the
    // payload with the sync matrix; sources are not backed up: the one RuTube row is recreated on demand.
    single {
        BackupManager(
            get(), get(),
        )
    }
    // context, okHttpClient, settings — in-app updates from GitHub Releases
    single { UpdateManager(androidContext(), get(), get()) }
    single { CatalogSyncScheduler(androidContext()) }
    single { EpgSyncScheduler(androidContext()) }
    // settings, sourceRepository, catalogSyncScheduler, epgSyncScheduler, channelDao, epgDao — the
    // startup/resume maintenance for the catalogue and the guide, run by the application process.
    single { tv.own.owntv.core.sync.AutoRefresh(get(), get(), get(), get(), get(), get()) }
    // sourceRepository, backup, connectivity, importFinalizer, catalogSyncScheduler — onboarding:
    // attach the built-in RuTube source (a DTO now), sync it, undo on failure. Factory, not single:
    // each wizard run owns its own state machine.
    factory { tv.own.owntv.core.setup.SourceImporter(get(), get(), get(), get(), get()) }
    // Android TV launcher cards for pinned live channels. One singleton so the menu, Settings and the
    // startup refresh all share the same pin set and the same mutex around the platform provider.
    single { tv.own.owntv.core.tv.TvHomePublisher(androidContext(), get(), get(), get(), get(), get(), get()) }
}
