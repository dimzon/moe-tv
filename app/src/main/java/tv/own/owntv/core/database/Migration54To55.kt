package tv.own.owntv.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v54 → v55: per-channel subscription flag — `channels.isPaid`.
 *
 * The RuTube live catalogue returns `is_paid = false` on every row, but subscription channels
 * (Premier, SPORT, …) carry `common_subscription_product_codes`. The syncer now folds either
 * signal into this flag so the UI can badge them; without it the app tuned them and showed a
 * generic player failure.
 *
 * `sources.isPaid` (v54) stays for compatibility but is not the right home for this: one RuTube
 * source holds both free and subscription channels.
 *
 * Additive only, default 0, so every existing row stays free until the next sync rewrites it.
 * Last hop, so it carries [OwnTVDatabase.healSchema] (standing rule).
 */
val MIGRATION_54_55 = object : Migration(54, 55) {
    override fun migrate(connection: SQLiteConnection) {
        if (!OwnTVDatabase.hasColumn(connection, "channels", "isPaid")) {
            connection.execSQL("ALTER TABLE `channels` ADD COLUMN `isPaid` INTEGER NOT NULL DEFAULT 0")
        }
        OwnTVDatabase.healSchema(connection)
    }
}
