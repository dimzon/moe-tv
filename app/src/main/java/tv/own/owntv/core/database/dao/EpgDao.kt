package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.EpgHashProjection
import tv.own.owntv.core.database.entity.EpgChannelEntity
import tv.own.owntv.core.database.entity.EpgChannelIcon
import tv.own.owntv.core.database.entity.EpgChannelName
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.EpgProgrammeText

/**
 * EPG storage + now/next lookups. Programmes are kept to a rolling window and pruned.
 * Schema v5 dropped `sourceId`: one guide store, one catalogue.
 */
@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<EpgChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgrammes(programmes: List<EpgProgrammeEntity>)

    /** Drop every stored programme (the guide store is single). */
    @Query("DELETE FROM epg_programmes")
    suspend fun clearProgrammes()

    /** Hash snapshot for one channel — an indexed prefix query on the natural key, loaded lazily per channel. */
    @Query("SELECT id, epgChannelId, startMs, contentHash FROM epg_programmes WHERE epgChannelId = :epgChannelId")
    suspend fun epgHashesForChannel(epgChannelId: String): List<EpgHashProjection>

    @Query("DELETE FROM epg_programmes WHERE id IN (:ids)")
    suspend fun deleteProgrammesByIds(ids: List<Long>)

    @Query("DELETE FROM epg_programmes WHERE epgChannelId IN (:epgChannelIds)")
    suspend fun deleteProgrammesForChannels(epgChannelIds: List<String>)

    /** Drop programmes that have already finished, to bound storage. */
    @Query("DELETE FROM epg_programmes WHERE stopMs < :before")
    suspend fun prune(before: Long)

    /** Drop programmes beyond the stored horizon — what lowering "Guide days to keep" frees. */
    @Query("DELETE FROM epg_programmes WHERE startMs > :after")
    suspend fun pruneFuture(after: Long)

    /** Rows outside the window a full re-crawl just served — i.e. ones it did not replace. */
    @Query("DELETE FROM epg_programmes WHERE (stopMs <= :from OR startMs >= :to)")
    suspend fun pruneOutsideWindow(from: Long, to: Long)

    /**
     * Drop programmes for one EPG channel id. Used by a re-fill so only what is about to be
     * repopulated is deleted — an empty answer would otherwise leave the channel blank.
     */
    @Query("DELETE FROM epg_programmes WHERE epgChannelId = :epgChannelId")
    suspend fun clearChannel(epgChannelId: String)

    /** The most recently FINISHED programme on a channel (the "Before" slot in the player overlay). */
    @Query("SELECT * FROM epg_programmes WHERE epgChannelId = :epgChannelId AND stopMs <= :now ORDER BY stopMs DESC LIMIT 1")
    suspend fun previousProgramme(epgChannelId: String, now: Long): EpgProgrammeEntity?

    /** Now + upcoming programmes for a channel (now/next and a short guide); Now is read from these too. */
    @Query("SELECT * FROM epg_programmes WHERE epgChannelId = :epgChannelId AND stopMs > :now ORDER BY startMs ASC LIMIT :limit")
    fun upcoming(epgChannelId: String, now: Long, limit: Int): Flow<List<EpgProgrammeEntity>>

    /** Total guide coverage for a channel, in whole days (latest stop − earliest start). Null when there
     *  is no stored guide for the channel. Used for the "EPG · Nd" hint in the Live preview metadata. */
    @Query("SELECT (MAX(stopMs) - MIN(startMs)) / 86400000 FROM epg_programmes WHERE epgChannelId = :epgChannelId")
    suspend fun coverageDays(epgChannelId: String): Int?

    // --- Guide reads: keyed by epgChannelId ALONE ------------------------------------------------
    //
    // A channel's guide is identified by its `epgChannelId`; which feed happened to deliver a row is
    // not part of that identity. Schema v5 removes the column outright, so these can no longer drift
    // apart from the now/next reads above.

    // Every windowed read below carries `startMs > :from - 86400000` as well as the obvious
    // `stopMs > :from`. Without it there is no lower bound on startMs, so SQLite cannot seek into the
    // (epgChannelId, startMs) index — it walks every row the channel has ever had and tests each one.
    // With it the read becomes a range seek over exactly the span asked for.
    //
    // 86400000 is twenty-four hours, and it is an assumption, stated here so it can be found: a
    // programme that started MORE than a day before the window and is still running would be missed.
    // Nothing in a television schedule runs that long; a feed that claims otherwise is malformed.

    /** One programme's synopsis, loaded on demand for the detail dialog (the grid load drops it). */
    @Query("SELECT description FROM epg_programmes WHERE id = :programmeId LIMIT 1")
    suspend fun programmeDescription(programmeId: Long): String?

    /** One whole programme — synopsis and XMLTV details — for the TV Guide's programme block. */
    @Query("SELECT * FROM epg_programmes WHERE id = :programmeId LIMIT 1")
    suspend fun programmeById(programmeId: Long): EpgProgrammeEntity?

    /**
     * One guide row's programmes, loaded lazily when the row scrolls into view. [epgKey] must be the
     * normalized (trim+lowercase) id — programmes are stored normalized, so this hits the
     * (epgChannelId, startMs) index and stays instant even with 100k+ stored programmes.
     */
    @Query("SELECT * FROM epg_programmes WHERE epgChannelId = :epgKey " +
            "AND startMs > :from - 86400000 AND stopMs > :from AND startMs < :to ORDER BY startMs ASC")
    suspend fun programmesForChannel(epgKey: String, from: Long, to: Long): List<EpgProgrammeEntity>

    /** Lightweight version for Guide row rendering; avoids CursorWindow pressure from descriptions. */
    @Query(
        "SELECT id, epgChannelId, startMs, stopMs, title, NULL AS description, 0 AS contentHash, " +
            "NULL AS categories, NULL AS year, NULL AS rating, NULL AS lengthMin, NULL AS episode " +
            "FROM epg_programmes WHERE epgChannelId = :epgKey " +
            "AND startMs > :from - 86400000 AND stopMs > :from AND startMs < :to ORDER BY startMs ASC",
    )
    suspend fun programmeSummariesForChannel(epgKey: String, from: Long, to: Long): List<EpgProgrammeEntity>

    /** Lightweight rows for several Home On Now channels at once. */
    @Query(
        "SELECT id, epgChannelId, startMs, stopMs, title, NULL AS description, 0 AS contentHash, " +
            "NULL AS categories, NULL AS year, NULL AS rating, NULL AS lengthMin, NULL AS episode " +
            "FROM epg_programmes WHERE epgChannelId IN (:epgKeys) " +
            "AND startMs > :from - 86400000 AND stopMs > :from AND startMs < :to ORDER BY epgChannelId ASC, startMs ASC",
    )
    suspend fun programmeSummariesForChannels(epgKeys: List<String>, from: Long, to: Long): List<EpgProgrammeEntity>

    /**
     * Programmes whose title contains [query] and that are on between [from] and [to] — what Search's
     * "On TV" group is drawn from. Bounded by start time, so it reads through the `(startMs, stopMs)`
     * index rather than the whole guide.
     */
    @Query(
        "SELECT id, epgChannelId, startMs, stopMs, title, NULL AS description, 0 AS contentHash, " +
            "NULL AS categories, NULL AS year, NULL AS rating, NULL AS lengthMin, NULL AS episode " +
            "FROM epg_programmes WHERE startMs > :from - 86400000 AND startMs < :to AND stopMs > :from " +
            "AND title LIKE '%' || :query || '%' ORDER BY startMs ASC LIMIT :limit",
    )
    suspend fun searchTitles(query: String, from: Long, to: Long, limit: Int): List<EpgProgrammeEntity>

    /** The synopses of [ids] (by primary key) — at most a few hundred, chunked by the caller. */
    @Query("SELECT id, description FROM epg_programmes WHERE id IN (:ids) AND description IS NOT NULL")
    suspend fun programmeDescriptions(ids: List<Long>): List<EpgProgrammeText>

    /** How many programmes are stored (to tell "no guide yet" from "empty window"). */
    @Query("SELECT COUNT(*) FROM epg_programmes")
    suspend fun countForSources(): Int

    /** Live programme count — drives the guide status line. */
    @Query("SELECT COUNT(*) FROM epg_programmes")
    fun countFlow(): Flow<Int>

    /** Current/upcoming programmes for one (normalized) EPG channel id — 0 means the feed has nothing
     *  scheduled from now on, so a freshly-matched channel would show an empty guide row. */
    @Query("SELECT COUNT(*) FROM epg_programmes WHERE epgChannelId = :epgChannelId AND stopMs > :now")
    suspend fun countUpcomingForChannel(epgChannelId: String, now: Long): Int

    /** How many distinct channels actually have guide data (for the Guide's status line). */
    @Query("SELECT COUNT(DISTINCT epgChannelId) FROM epg_programmes")
    suspend fun countGuideChannels(): Int

    // --- Guide candidates: also keyed by epgChannelId alone -------------------------------------
    //
    // The two queries below are the picker's and auto-match's half of the same decision the block
    // above records, and they are read together by [tv.own.owntv.core.epg.GuideCandidates].
    // `epg_channels` is empty for a feed that carries `<programme>` without `<channel>`, and
    // `epg_programmes` has no display name, so a candidate list needs both.
    //
    // No ORDER BY. The caller ranks by name similarity, which is the order that matters, and an
    // `ORDER BY displayName` here would make [limit] truncate the end of the alphabet — silently
    // losing every candidate from some letter onwards.

    /** Every named guide channel. Duplicates are possible: two names for one id. */
    @Query("SELECT epgChannelId, displayName, normName, normId FROM epg_channels LIMIT :limit")
    suspend fun guideChannelNames(limit: Int): List<EpgChannelName>

    /** Every guide id that actually has programmes stored — including ids no `<channel>` entry names. */
    @Query("SELECT DISTINCT epgChannelId FROM epg_programmes LIMIT :limit")
    suspend fun guideProgrammeChannelIds(limit: Int): List<String>

    /**
     * Channel `<icon src>` logos from the guide. Only rows with an icon are returned, so a feed
     * without icons costs nothing. Ids are already stored normalized (trim+lowercase), which is the
     * key the override map is looked up by.
     */
    @Query("SELECT epgChannelId, iconUrl FROM epg_channels WHERE iconUrl IS NOT NULL AND iconUrl != ''")
    fun observeChannelIcons(): Flow<List<EpgChannelIcon>>

    @Query("SELECT epgChannelId FROM epg_channels")
    suspend fun epgChannelIds(): List<String>

    @Query("DELETE FROM epg_channels WHERE epgChannelId IN (:epgChannelIds)")
    suspend fun deleteChannelsByEpgIds(epgChannelIds: List<String>)

    @Query("DELETE FROM epg_channels")
    suspend fun clearChannels()
}
