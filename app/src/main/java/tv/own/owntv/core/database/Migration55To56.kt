package tv.own.owntv.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v55 → v56: drop what the second playback engine left behind — `playback_quirks.enginePin` and
 * `sources.vodEnginePreference`.
 *
 * The app has exactly one playback engine now (ExoPlayer), so the per-item engine pin and the
 * per-playlist Movies & Series engine choice name an engine that no longer exists: every value
 * in those columns means "no choice" today. Dropped outright, the way v46 dropped the never
 * written `softwareDecode` column. SQLite ≥ 3.35 (the bundled engine is 3.50).
 *
 * `sources.liveEnginePreference` (v34) deliberately stays — its column removal belongs to the
 * next schema pass, and old rows still round-trip through the entity.
 *
 * Last hop, so it carries [OwnTVDatabase.healSchema] (standing rule).
 */
val MIGRATION_55_56 = object : Migration(55, 56) {
    override fun migrate(connection: SQLiteConnection) {
        if (OwnTVDatabase.hasColumn(connection, "playback_quirks", "enginePin")) {
            connection.execSQL("ALTER TABLE `playback_quirks` DROP COLUMN `enginePin`")
        }
        if (OwnTVDatabase.hasColumn(connection, "sources", "vodEnginePreference")) {
            connection.execSQL("ALTER TABLE `sources` DROP COLUMN `vodEnginePreference`")
        }
        OwnTVDatabase.healSchema(connection)
    }
}
