package tv.own.owntv.core.database

import androidx.room.Database
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.PlaybackPrefsDao
import tv.own.owntv.core.database.dao.TvProviderProgramDao
import tv.own.owntv.core.database.dao.TombstoneDao
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ChannelFtsEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.CustomCategoryMemberEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.PlaybackPrefsEntity
import tv.own.owntv.core.database.entity.PlaybackQuirkEntity
import tv.own.owntv.core.database.entity.UserDataTombstoneEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.database.entity.TvProviderProgramEntity
import tv.own.owntv.core.database.dao.CustomCategoryDao

@Database(
    entities = [
        // Content
        CategoryEntity::class,
        ChannelEntity::class,
        // Many-to-many: a channel belongs to every RuTube genre it is broadcast in (redesign §2).
        ChannelCategoryEntity::class,
        // Curated renames (hex videoId → name), no FK — survives re-syncs and deletions (redesign §2).
        ChannelRenameEntity::class,
        // User data
        FavoriteEntity::class,
        WatchHistoryEntity::class,
        ContentOrderEntity::class,
        CustomCategoryMemberEntity::class,
        PlaybackPrefsEntity::class,
        // What a stream needs, shared by everyone (v44)
        PlaybackQuirkEntity::class,
        UserDataTombstoneEntity::class,
        // Programme reminders. Never synced, never backed up — they last hours or days.
        tv.own.owntv.core.database.entity.ReminderEntity::class,
        // Android TV home-screen bookkeeping
        TvProviderProgramEntity::class,
        // EPG
        EpgProgrammeEntity::class,
        // FTS (search)
        ChannelFtsEntity::class,
    ],
    // The live-only redesign reset the schema to v1: profiles are gone from every table and the
    // migration history (1 → 57) was dropped with them — migrating the old tables toward a schema
    // that no longer has a `profiles` table vouched for nothing. There are zero installed
    // instances, so the database is simply recreated (see DatabaseModule's
    // fallbackToDestructiveMigration). v2 dropped `channels.categoryId` — the M:N junction
    // `channel_category` owns channel↔category membership, and the in-memory visibility rules
    // read ChannelMembership's snapshot. v3 adds `channel_rename` — curated hex→name renames
    // without an FK, mirrored from ChannelRenameSeeds on every sync. v4 slims `channels` to the
    // natural key: remoteId IS the PK and the wide row (row id, sourceId, stream marker, EPG id,
    // catch-up/DRM/headers/hash) is gone — one built-in source publishes only what it has, the
    // removed fields live on as code-level synonyms over remoteId. v5 removes the `sources` table:
    // the one built-in source is a hardcoded DTO (BuiltInSource), its user-adjustable values are
    // global Settings, and every `sourceId` column (categories, EPG, playback prefs/quirks) is gone.
    // v6 removes the last "multi-kind" columns: `mediaType` is gone from every table (the app is
    // live-only), watch_history is keyed (itemId, deviceId) with no surrogate id, reminders drop the
    // stored channelName/epgChannelId, and the legacy playback_prefs.audioDelayMs column is gone.
    // v7 drops favorites.id — the hex itemId is the primary key.
    // v8 slims epg_programmes to what RuTube publishes: subtitle/category/ageRating/iconUrl in,
    // year/episode/lengthMin/contentHash out (the hash-diff machinery went with XMLTV), and
    // epg_channels is gone — with one source the guide key IS channels.remoteId, so the table only
    // duplicated the catalogue with worse (pre-rename, pre-disambiguation) names.
    // v9 stores the provider's category slug (`categories.alias`); nothing reads it yet.
    // v10 makes the provider's numeric category id the primary key — `categories.id` loses the
    // surrogate AUTOINCREMENT row id and `remoteId` becomes a computed string view of it.
    // The old per-version log lives in git history and in CHANGELOG.md.
    version = 10,

    exportSchema = true,
)
@TypeConverters(Converters::class)
/** Room entry point; its generated `_Impl` is loaded by name and retained by release keep rules. */
abstract class OwnTVDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun channelCategoryDao(): ChannelCategoryDao
    abstract fun channelRenameDao(): ChannelRenameDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun contentOrderDao(): ContentOrderDao
    abstract fun playbackPrefsDao(): PlaybackPrefsDao
    abstract fun playbackQuirkDao(): tv.own.owntv.core.database.dao.PlaybackQuirkDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun tombstoneDao(): TombstoneDao
    abstract fun tvProviderProgramDao(): TvProviderProgramDao
    abstract fun reminderDao(): tv.own.owntv.core.database.dao.ReminderDao
    abstract fun epgDao(): EpgDao

    companion object {
        const val NAME = "owntv.db"

        /**
         * Canonical CREATE statements for every NON-unique index Room expects on the bulk-synced
         * tables, keyed by table (must stay in sync with the current schema JSON).
         * BulkInsertHelper drops exactly these during eligible fresh imports; restore, the
         * post-import ensure* passes, and [healSchema] all recreate from this one list so a gap
         * can't survive anywhere. Unique indexes are deliberately absent: no code path ever drops
         * them, and re-creating a unique index on unexpected data could itself fail.
         */
        val EXPECTED_NON_UNIQUE_INDEXES: Map<String, List<String>> = mapOf(
            "channels" to listOf(
                "CREATE INDEX IF NOT EXISTS `index_channels_name` ON `channels` (`name`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_sortOrder_name` ON `channels` (`sortOrder`, `name`)",
                "CREATE INDEX IF NOT EXISTS `index_channels_number` ON `channels` (`number`)",
            ),
            "epg_programmes" to listOf(
                "CREATE INDEX IF NOT EXISTS `index_epg_programmes_stopMs` ON `epg_programmes` (`stopMs`)",
                "CREATE INDEX IF NOT EXISTS `index_epg_programmes_startMs_stopMs` ON `epg_programmes` (`startMs`, `stopMs`)",
            ),
        )

        /**
         * Room's exact generated DDL for the external-content FTS tables. Only `createAllTables`
         * (fresh install) ever creates them, so keep the strings verbatim from the generated
         * OwnTVDatabase_Impl or validation will reject the healed table.
         */
        internal val EXPECTED_FTS_TABLES: Map<String, String> = mapOf(
            "channels_fts" to "CREATE VIRTUAL TABLE IF NOT EXISTS `channels_fts` USING FTS4(`name` TEXT NOT NULL, content=`channels`)",
        )

        /**
         * Bring a live database back to the schema Room expects, idempotently (pure no-op on a
         * healthy DB). Recreates the non-unique indexes on the bulk-synced tables and any missing
         * FTS table (rebuilt from its content table when it had to be created).
         *
         * STANDING RULE: every future final schema change must keep this running from `onOpen`.
         * Room validates the whole schema once at open — so an index dropped by an interrupted
         * bulk import otherwise fails validation the next time the schema is checked.
         */
        fun healSchema(db: SQLiteConnection) {
            EXPECTED_FTS_TABLES.forEach { (name, createSql) ->
                // step() returning true IS the row — the driver API has no moveToFirst().
                val exists = db.prepare(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='$name'",
                ).use { it.step() }
                if (!exists) {
                    db.execSQL(createSql)
                    db.execSQL("INSERT INTO `$name`(`$name`) VALUES('rebuild')")
                }
            }
            EXPECTED_NON_UNIQUE_INDEXES.values.forEach { statements ->
                statements.forEach { if (indexColumnsExist(db, it)) db.execSQL(it) }
            }
        }

        /**
         * True when every column an index statement references already exists.
         *
         * Needed because healSchema may run against a table whose newer columns are missing —
         * an install restored from a pre-redesign database, for instance — and "no such column"
         * would abort the heal. Skipping such an index is safe: fresh installs get it from
         * `createAllTables`, and the next heal — by which point the column exists — restores it if
         * it is ever missing.
         */
        private fun indexColumnsExist(
            db: SQLiteConnection,
            createIndexSql: String,
        ): Boolean {
            val match = INDEX_TARGET.find(createIndexSql) ?: return true
            val table = match.groupValues[1]
            return match.groupValues[2].split(',')
                .map { it.trim().trim('`') }
                .all { it.isEmpty() || hasColumn(db, table, it) }
        }

        /** Pulls the table name and column list out of a canonical CREATE INDEX statement. */
        private val INDEX_TARGET = Regex("ON\\s+`([^`]+)`\\s*\\(([^)]*)\\)", RegexOption.IGNORE_CASE)

        /**
         * Every schema object [healSchema] guarantees: the FTS tables plus the name of each
         * non-unique index, parsed once from the canonical CREATE statements above so the two
         * lists can never drift apart.
         */
        private val EXPECTED_SCHEMA_OBJECTS: List<String> by lazy {
            EXPECTED_FTS_TABLES.keys.toList() +
                EXPECTED_NON_UNIQUE_INDEXES.values.flatten()
                    .map { it.substringAfter("IF NOT EXISTS `").substringBefore('`') }
        }

        /**
         * The cheap drift probe that gates the heal on the app-open path. One `sqlite_master`
         * count answers "is anything missing?"; on a healthy database — the normal case — that is a
         * single index lookup instead of ~30 `CREATE INDEX IF NOT EXISTS` statements landing on
         * whichever thread issues the first query, at exactly the moment the first grid wants data.
         *
         * Deliberately still inside `onOpen`: a query that ran before the heal finished would hit a
         * missing index, which is the slow path the heal exists to prevent. Returns true if it healed.
         */
        fun healSchemaIfDrifted(db: SQLiteConnection): Boolean {
            val expected = EXPECTED_SCHEMA_OBJECTS
            val placeholders = expected.joinToString(",") { "?" }
            val present = db.prepare(
                "SELECT COUNT(*) FROM sqlite_master WHERE name IN ($placeholders)",
            ).use { statement ->
                // Binds are 1-based, column getters are 0-based. Both appear in this one block.
                expected.forEachIndexed { i, name -> statement.bindText(i + 1, name) }
                if (statement.step()) statement.getInt(0) else 0
            }
            if (present >= expected.size) return false
            healSchema(db)
            return true
        }

        internal fun hasColumn(db: SQLiteConnection, table: String, column: String): Boolean {
            db.prepare("PRAGMA table_info(`$table`)").use { statement ->
                // No getColumnIndex(name) on the driver API — resolve it from getColumnNames(),
                // which is the same lookup Cursor did internally. PRAGMA table_info has always put
                // `name` at index 1, but resolving it keeps this honest if that ever changes.
                val nameIndex = statement.getColumnNames().indexOf("name")
                if (nameIndex < 0) return false
                while (statement.step()) {
                    if (statement.getText(nameIndex) == column) return true
                }
                return false
            }
        }
    }
}
