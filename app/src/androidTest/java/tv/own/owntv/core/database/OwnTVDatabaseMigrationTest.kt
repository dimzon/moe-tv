package tv.own.owntv.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-baseline migrations run against the complete exported schemas and the shipped SQLite
 * driver, then the result is opened through Room so schema validation runs too. Room's
 * MigrationTestHelper is not used: its schema-bundle parser pulls serialization 1.8 into an app
 * runtime pinned to 1.7 by lifecycle, which makes the helper fail before a migration.
 */
@RunWith(AndroidJUnit4::class)
class OwnTVDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val driver = AndroidSQLiteDriver()

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate8To10_preservesUserData_andRemapsCategoryMemberships() {
        val connection = createVersion(8)
        connection.execSQL("INSERT INTO categories (id, name, remoteId, sortOrder) VALUES (41, 'News', '5', 2)")
        connection.execSQL("INSERT INTO channels (remoteId, name, sortOrder) VALUES ('abc123', 'Channel', 0)")
        connection.execSQL("INSERT INTO channel_category (channelHexId, categoryId) VALUES ('abc123', 41)")
        connection.execSQL("INSERT INTO favorites (itemId, addedAt) VALUES ('abc123', 123)")

        migrate(connection, OwnTVDatabase.MIGRATION_8_9, OwnTVDatabase.MIGRATION_9_10)

        connection.prepare("SELECT id, name, alias, sortOrder FROM categories ORDER BY id").use { cursor ->
            assertTrue(cursor.step())
            assertEquals(5L, cursor.getLong(0))
            assertEquals("News", cursor.getText(1))
            assertTrue(cursor.isNull(2))
            assertEquals(2, cursor.getInt(3))
            assertFalse(cursor.step())
        }
        connection.prepare("SELECT channelHexId, categoryId FROM channel_category").use { cursor ->
            assertTrue(cursor.step())
            assertEquals("abc123", cursor.getText(0))
            assertEquals(5L, cursor.getLong(1))
            assertFalse(cursor.step())
        }
        connection.prepare("SELECT itemId, addedAt FROM favorites").use { cursor ->
            assertTrue(cursor.step())
            assertEquals("abc123", cursor.getText(0))
            assertEquals(123L, cursor.getLong(1))
        }
        connection.prepare("PRAGMA table_info(categories)").use { cursor ->
            val columns = mutableListOf<String>()
            val nameIndex = cursor.getColumnNames().indexOf("name")
            while (cursor.step()) columns += cursor.getText(nameIndex)
            assertFalse("v10 must not retain the surrogate remoteId column", "remoteId" in columns)
        }
        writeVersion(connection, 10)
        connection.close()
        assertRoomOpens()
    }

    @Test
    fun migrate9To10_keepsAliases_andRejectsOrphanedMembership() {
        val connection = createVersion(9)
        connection.execSQL("INSERT INTO categories (id, name, remoteId, alias, sortOrder) VALUES (7, 'Movies', '12', 'movies', 4)")

        migrate(connection, OwnTVDatabase.MIGRATION_9_10)

        connection.prepare("SELECT id, alias FROM categories").use { cursor ->
            assertTrue(cursor.step())
            assertEquals(12L, cursor.getLong(0))
            assertEquals("movies", cursor.getText(1))
        }
        writeVersion(connection, 10)
        connection.close()
        assertRoomOpens()

        val orphaned = createVersion(9)
        orphaned.execSQL("PRAGMA foreign_keys=OFF")
        orphaned.execSQL("INSERT INTO channel_category (channelHexId, categoryId) VALUES ('abc123', 999)")
        try {
            migrate(orphaned, OwnTVDatabase.MIGRATION_9_10)
            fail("an orphaned membership must abort the migration")
        } catch (_: IllegalStateException) {
            // Expected: validation happens before any DROP, and the transaction rolls back.
        }
        orphaned.prepare("PRAGMA table_info(categories)").use { cursor ->
            val nameIndex = cursor.getColumnNames().indexOf("name")
            var hasRemoteId = false
            while (cursor.step()) hasRemoteId = hasRemoteId || cursor.getText(nameIndex) == "remoteId"
            assertTrue(hasRemoteId)
        }
        orphaned.close()
    }

    @Test
    fun malformedCategoryIdentity_rollsBackTheWhole9To10Migration() {
        val connection = createVersion(9)
        connection.execSQL("INSERT INTO categories (id, name, remoteId, alias, sortOrder) VALUES (7, 'Movies', 'not-numeric', null, 0)")
        connection.execSQL("INSERT INTO channels (remoteId, name, sortOrder) VALUES ('abc123', 'Channel', 0)")
        connection.execSQL("INSERT INTO favorites (itemId, addedAt) VALUES ('abc123', 123)")

        try {
            migrate(connection, OwnTVDatabase.MIGRATION_9_10)
            fail("a category without a provider id must fail closed")
        } catch (_: IllegalStateException) {
            // Expected: the migration fails and the transaction rolls back.
        }

        connection.prepare("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.step())
            assertEquals(9, cursor.getInt(0))
        }
        connection.prepare("SELECT COUNT(*) FROM favorites").use { cursor ->
            assertTrue(cursor.step())
            assertEquals(1, cursor.getInt(0))
        }
        connection.close()
    }

    @Test
    fun version7FailsClosed_withoutDestructiveFallback() {
        createVersion(7).close()

        val db = Room.databaseBuilder(context, OwnTVDatabase::class.java, TEST_DB)
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(*OwnTVDatabase.ALL_MIGRATIONS)
            .build()
        try {
            runBlocking { db.categoryDao().ids() }
            fail("v7 is outside the supported release baseline")
        } catch (_: IllegalStateException) {
            // Expected: there is deliberately no 7 -> 8 migration or destructive fallback.
        } finally {
            db.close()
        }
        driver.open(databasePath().absolutePath).use { raw ->
            raw.prepare("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.step())
                assertEquals(7, cursor.getInt(0))
            }
        }
    }

    private fun createVersion(version: Int): SQLiteConnection {
        context.deleteDatabase(TEST_DB)
        databasePath().parentFile?.mkdirs()
        return driver.open(databasePath().absolutePath).also { connection ->
            connection.execSQL("PRAGMA foreign_keys=ON")
            val schema = schemaJson(version)
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) {
                        connection.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            schema.optJSONArray("setupQueries")?.let { queries ->
                for (i in 0 until queries.length()) connection.execSQL(queries.getString(i))
            }
            writeVersion(connection, version)
        }
    }

    private fun writeVersion(connection: SQLiteConnection, version: Int) {
        val schema = schemaJson(version)
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        connection.prepare("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)").use { statement ->
            statement.bindText(1, schema.getString("identityHash"))
            statement.step()
        }
        connection.execSQL("PRAGMA user_version=$version")
    }

    private fun assertRoomOpens() {
        val db = Room.databaseBuilder(context, OwnTVDatabase::class.java, TEST_DB)
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(*OwnTVDatabase.ALL_MIGRATIONS)
            .build()
        try {
            runBlocking { db.categoryDao().ids() }
        } finally {
            db.close()
        }
    }

    private fun schemaJson(version: Int): JSONObject {
        val path = "tv.own.owntv.core.database.OwnTVDatabase/$version.json"
        val text = InstrumentationRegistry.getInstrumentation().context.assets.open(path)
            .bufferedReader().use { it.readText() }
        return JSONObject(text).getJSONObject("database")
    }

    private fun migrate(connection: SQLiteConnection, vararg migrations: Migration) {
        connection.execSQL("BEGIN")
        try {
            migrations.forEach { it.migrate(connection) }
            connection.execSQL("COMMIT")
        } catch (error: Throwable) {
            runCatching { connection.execSQL("ROLLBACK") }
            throw error
        }
    }

    private fun databasePath() = context.getDatabasePath(TEST_DB)

    private companion object {
        const val TEST_DB = "owntv-migration-test.db"
    }
}
