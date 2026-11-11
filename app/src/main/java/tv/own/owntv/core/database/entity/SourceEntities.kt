package tv.own.owntv.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import tv.own.owntv.core.model.HlsSupport
import tv.own.owntv.core.model.SourceType

/** [SourceEntity.livePrerollSecs] sentinel: use the global Settings value rather than a per-playlist one. */
const val FOLLOW_GLOBAL_PREROLL = -1

/** [SourceEntity.liveLatencyCustomSecs] sentinel: no per-playlist custom latency stored yet. */
const val FOLLOW_GLOBAL_LATENCY_SECS = -1

/** An IPTV source (M3U file/URL, Xtream account, or Stalker portal). Content rows reference their `sourceId`. */
@Entity(
    tableName = "sources",
    indices = [Index("type")],
)
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: SourceType,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    /** Stalker portal MAC in canonical `AA:BB:CC:DD:EE:FF` form; null for M3U/Xtream. */
    val mac: String? = null,
    /** Optional Stalker/Ministra second-step device identity fields; null for other source types. */
    val stalkerSerialNumber: String? = null,
    val stalkerDeviceId: String? = null,
    val stalkerDeviceId2: String? = null,
    val stalkerSignature: String? = null,
    val userAgent: String? = null,
    /** XMLTV guide URL (M3U `url-tvg` or manually entered); Xtream EPG comes from the API. */
    val epgUrl: String? = null,
    /**
     * enabledScope (v17). `false` = never fetch AND never show this section for this source.
     * Cache + user data are retained — Off is a sync/visibility scope, not a deletion.
     */
    val syncLive: Boolean = true,
    val syncMovies: Boolean = true,
    val syncSeries: Boolean = true,
    /**
     * Whether a Stalker portal's **own** guide may be imported (v37). On by default: a portal that
     * publishes no XMLTV feed has no other way to fill the Guide, and without it catch-up cannot be
     * used at all. Turning it off removes the portal's guide entry from Settings → EPG Sources and stops it
     * being offered again.
     *
     * Only meaningful for [SourceType.STALKER]; a portal that advertises an XMLTV feed uses that
     * instead, and this is ignored.
     */
    @ColumnInfo(defaultValue = "1") val importPortalEpg: Boolean = true,
    /**
     * Whether the provider explicitly lists m3u8 in user_info.allowed_output_formats (v23), read at the
     * start of every Xtream sync. Detection hint only — it refines the playlist wording, it does NOT
     * gate [preferHls]: a panel that under-reports its formats must not stop the user asking for HLS.
     *
     * Tri-state ([HlsSupport]) since the answer is unknown until the first sync, and "unknown" must not
     * read as "unsupported". Stored in the same INTEGER column the old boolean used — see [HlsSupport].
     */
    val hlsSupported: HlsSupport = HlsSupport.UNKNOWN,
    /**
     * User preference (v23): prioritize .m3u8 streams over .ts for **Live TV only**. Catch-up is
     * deliberately excluded
     * in front of it, so asking it for `.m3u8` breaks archives on accounts whose live TV is fine.
     */
    val preferHls: Boolean = false,
    /**
     * Per-playlist "Pre-buffer" override in seconds (v25). `-1` = follow the global
     * Settings value; `0` = off; `N` = fill N seconds before a live channel starts and after a
     * rebuffer. Per-playlist because the problem it solves is a *provider* that hiccups on a fixed
     * period — a user with one bad panel and three good ones should not have to slow all four down.
     */
    val livePrerollSecs: Int = FOLLOW_GLOBAL_PREROLL,
    /**
     * Legacy (v34): a per-playlist Live TV engine override name from the time the app had two
     * playback engines. The app ships one engine now, so the stored value changes nothing; the
     * column and property stay until a later schema pass drops them. `null` — every existing row —
     * meant "follow the global setting".
     */
    val liveEnginePreference: String? = null,
    /**
     * Per-playlist Live latency override (v34), as a [tv.own.owntv.core.settings.LiveLatency]
     * name; `null` = follow the global setting. [liveLatencyCustomSecs] carries the seconds and is only
     * read when this is `CUSTOM`, mirroring the global pair exactly.
     */
    val liveLatencyMode: String? = null,
    val liveLatencyCustomSecs: Int = FOLLOW_GLOBAL_LATENCY_SECS,
    /**
     * How many simultaneous streams the provider allows (v27), from Xtream's
     * `user_info.max_connections`. `0` = unknown (M3U/Stalker, an older row, or a panel that doesn't
     * report it).
     *
     * `1` is the interesting value: playback streams must never overlap on such an account, and
     * until now the app only found that out by failing a tune and reading the 458 back. Knowing it at
     * sync time means the very first zap already behaves.
     */
    val maxConnections: Int = 0,
    /**
     * When [maxConnections] was measured by opening streams, or 0 if it never was.
     *
     * Separates the three states that matter and used to be indistinguishable: never looked into
     * (0 here), measured and answered (a timestamp and a number), and measured without a conclusion
     * (a timestamp and no number — every channel tried was broken, or the provider took too long).
     * The last of those must not be retried on every sync, which is the whole reason for the column.
     *
     * Only the first sync after a playlist is added sets this, plus an explicit Re-test. A provider
     * that publishes its own limit is never measured and leaves this at 0.
     */
    val maxConnectionsProbedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long? = null,
    // --- v44: the per-playlist overrides added then (the VOD-engine one was dropped at v56). Each
    // `null` = follow the global setting, which is what every existing row reads as after the upgrade.
    /** Catch-up time zone for this provider's archive, as a `SettingsRepository.CatchupTimezone` name. */
    val catchupTimezone: String? = null,
    /** MANUAL [catchupTimezone]'s offset from UTC in minutes (unused for DEVICE). */
    val catchupOffsetMin: Int? = null,
    /** "Give up after" for a live tune on this playlist, in seconds. */
    val liveTuneTimeoutSecs: Int? = null,
    /** An HTTP `Referer` this provider's streams require. */
    val httpReferer: String? = null,
    /**
     * The time zone an Xtream panel reports for itself (v49), as a `java.util.TimeZone` ID: its
     * `server_info.timezone`, or a `GMT±hh:mm` worked out from `time_now` vs `timestamp_now` when the
     * name is missing or unknown. Re-read at every sync and never backed up — like [maxConnections] it
     * is the provider's fact, not the user's. Null = not known (M3U/Stalker, or not synced since v49).
     * Catch-up's Automatic time zone uses it.
     */
    val serverTimeZone: String? = null,
    /** "Clean channel names" (v51): tidy this playlist's channel names at sync, see
     *  [tv.own.owntv.core.customize.ChannelNameCleanup]. The user's choice, so it is backed up. */
    @ColumnInfo(defaultValue = "0") val cleanChannelNames: Boolean = false,
    /** The user's own words [cleanChannelNames] also removes, `;`-separated (v51). Null = none. */
    val channelNameStrip: String? = null,
    /**
     * "Ignore connection limit" (v53): Multiview and recordings no longer refuse a stream because of
     * [maxConnections], for a provider whose stated limit is lower than what it really allows. The
     * provider's own refusal (HTTP 458) still stops a stream and says so. The user's choice, so it is
     * backed up. Live tuning's single-session handover still follows [maxConnections].
     */
    @ColumnInfo(defaultValue = "0") val ignoreConnectionLimit: Boolean = false,
    /**
     * Whether this channel list is subscription-only (RuTube `is_paid`, v54). RuTube-only: the
     * play-options endpoint answers `login_required` for these, so the UI badges them and the player
     * never attempts a tune. Defaults false for every other source type, which are all free.
     */
    @ColumnInfo(defaultValue = "0") val isPaid: Boolean = false,
)
