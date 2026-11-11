package tv.own.owntv.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import tv.own.owntv.core.model.SourceType

/** [SourceEntity.livePrerollSecs] sentinel: use the global Settings value rather than a per-playlist one. */
const val FOLLOW_GLOBAL_PREROLL = -1

/** [SourceEntity.liveLatencyCustomSecs] sentinel: no per-playlist custom latency stored yet. */
const val FOLLOW_GLOBAL_LATENCY_SECS = -1

/**
 * The single built-in source: RuTube. Content rows reference their `sourceId`.
 *
 * The columns the old M3U / panel / portal / local-backup kinds needed — credentials (username,
 * password, MAC, device identity), guide URLs, HLS detection and preference, connection-limit
 * bookkeeping, per-provider engine and catch-up overrides — are gone with the providers themselves
 * (live-only redesign, phase ②). The remaining per-playlist playback overrides stay because the
 * playback path reads them for every source, including this one.
 */
@Entity(
    tableName = "sources",
    indices = [Index("type")],
)
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: SourceType,
    /** For RuTube the API base URL, written by the built-in-source helper; also the session key
     *  the playback layer recognizes this row by. */
    val url: String,
    val userAgent: String? = null,
    /**
     * enabledScope (v17). `false` = never fetch AND never show this section for this source.
     * Cache + user data are retained — Off is a sync/visibility scope, not a deletion.
     */
    val syncLive: Boolean = true,
    val syncMovies: Boolean = true,
    val syncSeries: Boolean = true,
    /**
     * Per-playlist "Pre-buffer" override in seconds (v25). `-1` = follow the global
     * Settings value; `0` = off; `N` = fill N seconds before a live channel starts and after a
     * rebuffer. Per-playlist because the problem it solves is a *provider* that hiccups on a fixed
     * period.
     */
    val livePrerollSecs: Int = FOLLOW_GLOBAL_PREROLL,
    /**
     * Per-playlist Live latency override (v34), as a [tv.own.owntv.core.settings.LiveLatency]
     * name; `null` = follow the global setting. [liveLatencyCustomSecs] carries the seconds and is only
     * read when this is `CUSTOM`, mirroring the global pair exactly.
     */
    val liveLatencyMode: String? = null,
    val liveLatencyCustomSecs: Int = FOLLOW_GLOBAL_LATENCY_SECS,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long? = null,
    /** "Give up after" for a live tune on this playlist, in seconds. */
    val liveTuneTimeoutSecs: Int? = null,
    /** An HTTP `Referer` this provider's streams require. */
    val httpReferer: String? = null,
    /** "Clean channel names" (v51): tidy this playlist's channel names at sync, see
     *  [tv.own.owntv.core.customize.ChannelNameCleanup]. The user's choice, so it is backed up. */
    @ColumnInfo(defaultValue = "0") val cleanChannelNames: Boolean = false,
    /** MANUAL [cleanChannelNames]'s own words also removed, `;`-separated (v51). Null = none. */
    val channelNameStrip: String? = null,
    /**
     * Whether this channel list is subscription-only (RuTube `is_paid`, v54). RuTube-only: the
     * play-options endpoint answers `login_required` for these, so the UI badges them and the player
     * never attempts a tune. Defaults false — every RuTube list the catalogue offers is free.
     */
    @ColumnInfo(defaultValue = "0") val isPaid: Boolean = false,
)
