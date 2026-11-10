package tv.own.owntv.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v56 → v57: drop every VOD table — the features are gone and the app is live-only.
 *
 * Movies, series, seasons, episodes (+ their FTS mirrors), the TMDB metadata cache and match
 * tables, the Trending showcase, DVR recordings and their standing rules, the Stalker catalog
 * backfill queue, the OpenSubtitles/local-file subtitle tables, playback_progress (resume) and
 * downloads — all of it served playback that no longer exists. `watch_history` keeps its table:
 * it stores live-channel rows too, and its VOD query methods were removed from the DAO instead.
 * `sources.liveEnginePreference` keeps its column for the same v56 reason (next schema pass).
 *
 * Plain `DROP TABLE` for each, guarded where it costs nothing to be defensive. FTS tables are
 * virtual; SQLite drops them like any other table. Last hop, so it carries
 * [OwnTVDatabase.healSchema] (standing rule).
 */
val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "movies", "series", "seasons", "episodes",
            "movies_fts", "series_fts", "episodes_fts",
            "metadata_cache", "metadata_match",
            "trending_snapshots", "trending_items",
            "recordings", "recording_rules",
            "catalog_backfill",
            "subtitle_cache", "subtitle_selection", "subtitle_link", "subtitle_timing",
            "playback_progress", "downloads", "series_sort_order",
        ).forEach { table ->
            connection.execSQL("DROP TABLE IF EXISTS `$table`")
        }
        // Shared user-data tables keep their LIVE rows and lose the VOD ones.
        for (table in listOf("favorites", "watch_history", "content_order", "custom_category_members")) {
            connection.execSQL("DELETE FROM `$table` WHERE mediaType != 'LIVE'")
        }
        OwnTVDatabase.healSchema(connection)
    }
}
