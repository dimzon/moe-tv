package tv.own.owntv.core.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.core.database.OwnTVDatabase

/**
 * Provides the Room database (WAL journal mode for fast concurrent reads during large imports) and
 * each DAO. Foreign-key enforcement is on by default in Room.
 */
val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), OwnTVDatabase::class.java, OwnTVDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // The system SQLite, not the engine bundled in the APK — this drops libsqliteJni.so
            // (~2.6 MB). minSdk 26 means an Android 8 device runs SQLite 3.18, which has no UPSERT
            // (3.24) or window functions (3.25); the two places that used them were rewritten to be
            // version-independent (the manual-order sort moved to Kotlin — see ManualOrdering — and
            // the tombstone upsert to UPDATE + INSERT OR IGNORE). So the platform engine suffices.
            .setDriver(AndroidSQLiteDriver())
            // Redesign period, zero installed apps: the schema was reset to v1 without migrations,
            // so any pre-existing database (dev builds) is simply dropped and recreated.
            // DELETE THIS LINE before a public release — a wipe-on-mismatch fallback silently
            // deletes real users' favorites and history.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(object : RoomDatabase.Callback() {
                // Self-heal index/FTS drift on every open (no-op when healthy): an interrupted bulk
                // import can leave BulkInsertHelper's dropped indexes missing, which is invisible
                // now but fails Room's full-schema validation at the next open. Healing here
                // repairs drift immediately.
                //
                // Gated behind a single sqlite_master count so a healthy open pays one index
                // lookup instead of ~30 DDL statements on the thread issuing the first query. The
                // heal itself stays in onOpen — moving it off would let a query beat it to a missing
                // index. The OwnTVPerf timeline reports both the cost and whether it healed.
                override fun onOpen(connection: SQLiteConnection) {
                    // Connection tuning, applied before anything queries. Both settings are per-open,
                    // so they belong here rather than in a migration.
                    //
                    // synchronous=NORMAL is the documented companion to WAL: FULL fsyncs the WAL on
                    // every single commit, which on a TV's eMMC is the dominant cost of a sync writing
                    // tens of thousands of rows. NORMAL still fsyncs at checkpoints, so the durability
                    // it trades away is only "the last few committed transactions survive a sudden
                    // power cut" — not database integrity, which WAL still guarantees. A cut power
                    // cable can cost the tail of a catalogue import, and that re-syncs.
                    //
                    // The page cache is per-connection and defaults to ~2MB. Home alone opens a dozen
                    // user-data queries over the same handful of index pages, and every screen
                    // after it re-reads them; a negative value is SQLite's "this many KiB" form.
                    runCatching {
                        connection.execSQL("PRAGMA synchronous=NORMAL")
                        connection.execSQL("PRAGMA cache_size=-8000")
                    }
                    // Answered in code rather than by reading a log off a device.
                    // `setJournalMode(WRITE_AHEAD_LOGGING)` is a Support-path builder API and it is
                    // undocumented whether it still applies once a driver is configured. Losing WAL
                    // silently would look like a general slowdown with no cause, so this reads the
                    // mode back and sets it if the builder did not. Both are cheap and idempotent.
                    val journal = runCatching {
                        connection.prepare("PRAGMA journal_mode").use { if (it.step()) it.getText(0) else "" }
                    }.getOrDefault("")
                    val journalFixed = if (!journal.equals("wal", ignoreCase = true)) {
                        runCatching {
                            connection.prepare("PRAGMA journal_mode=WAL").use { it.step() }
                        }.isSuccess
                    } else {
                        false
                    }
                    // Record which engine actually ran, so a release logcat answers it
                    // without guesswork. Release builds are obfuscated — ask for mapping.txt
                    // alongside any crash trace from this phase.
                    val engineVersion = runCatching {
                        connection.prepare("SELECT sqlite_version()").use { if (it.step()) it.getText(0) else "?" }
                    }.getOrDefault("?")
                    val healed = runCatching { OwnTVDatabase.healSchemaIfDrifted(connection) }.getOrDefault(false)
                    // The stamp is not optional. Under a driver, a Callback.onOpen written against
                    // the old Support type is silently skipped — no exception, nothing in CI, and
                    // the PRAGMAs and the schema self-heal simply stop running. This line is the
                    // only thing that makes that failure visible, so it reports what actually ran.
                    tv.own.owntv.core.util.Perf.stamp(
                        "db-open(sqlite=" + engineVersion + ", journal=" + journal.lowercase() +
                            (if (journalFixed) " forced-wal" else "") +
                            ", heal=" + (if (healed) "repaired" else "clean") + ")",
                    )
                }
            })
            .build()
    }

    single { get<OwnTVDatabase>().sourceDao() }
    single { get<OwnTVDatabase>().categoryDao() }
    single { get<OwnTVDatabase>().channelDao() }
    single { get<OwnTVDatabase>().channelCategoryDao() }
    single { get<OwnTVDatabase>().favoriteDao() }
    single { get<OwnTVDatabase>().historyDao() }
    single { get<OwnTVDatabase>().contentOrderDao() }
    single { get<OwnTVDatabase>().playbackPrefsDao() }
    single { get<OwnTVDatabase>().playbackQuirkDao() }
    single { get<OwnTVDatabase>().customCategoryDao() }
    single { get<OwnTVDatabase>().tombstoneDao() }
    single { get<OwnTVDatabase>().tvProviderProgramDao() }
    single { get<OwnTVDatabase>().reminderDao() }
    single { get<OwnTVDatabase>().epgDao() }
}
