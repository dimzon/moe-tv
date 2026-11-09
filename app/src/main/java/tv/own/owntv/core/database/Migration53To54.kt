package tv.own.owntv.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v53 → v54: built-in RuTube source support.
 *
 * Two additive changes:
 * - `SourceType.RUTUBE`: `sources.type` stores the enum name as TEXT, so no DDL is needed
 *   for the new value — existing rows keep M3U/XTREAM/STALKER/LOCAL_BACKUP.
 * - `sources.isPaid`: `INTEGER NOT NULL DEFAULT 0` — RuTube `is_paid` on the built-in source.
 *   False on every existing row and every other source type, which are all free. Additive,
 *   with a default, so [healSchema] is the only other step (standing rule: the final hop
 *   must run it).
 */
val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(connection: SQLiteConnection) {
        if (!OwnTVDatabase.hasColumn(connection, "sources", "isPaid")) {
            connection.execSQL("ALTER TABLE `sources` ADD COLUMN `isPaid` INTEGER NOT NULL DEFAULT 0")
        }
        OwnTVDatabase.healSchema(connection)
    }
}
