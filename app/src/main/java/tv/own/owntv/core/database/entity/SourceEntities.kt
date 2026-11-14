package tv.own.owntv.core.database.entity

import tv.own.owntv.core.model.SourceType

/** [SourceEntity.livePrerollSecs] sentinel: use the global Settings value rather than a per-playlist one. */
const val FOLLOW_GLOBAL_PREROLL = -1

/** [SourceEntity.liveLatencyCustomSecs] sentinel: no per-playlist custom latency stored yet. */
const val FOLLOW_GLOBAL_LATENCY_SECS = -1

/**
 * The single built-in source: RuTube. **Not a Room table any more** (schema v5) — its row was one
 * row of constants plus a handful of per-playlist overrides that only ever made sense with several
 * playlists. The constants are hardcoded in [tv.own.owntv.core.repository.BuiltInSource]; the
 * settings-backed fields are filled by the source repository, which builds a snapshot from the
 * global Settings values (one source ⇒ its per-playlist overrides *are* the global ones).
 *
 * The DTO keeps every field the old row carried, so the ~40 call sites that read a `SourceEntity`
 * (playback overrides, sync scoping, EPG refresh, workers) keep compiling unchanged.
 *
 * [id] is a hardcoded constant (`SingleSource.id`); no foreign key references it any more, but the
 * playback `contentKey` format still carries it as a namespace.
 */
data class SourceEntity(
    val id: Long = 0,
    val name: String,
    val type: SourceType,
    /** The API base URL; playback recognises the built-in source by it. */
    val url: String,
    val userAgent: String? = null,
    /** Live scope; the app is live-only, so this is always true for the built-in source. */
    val syncLive: Boolean = true,
    /**
     * Live pre-buffer override in seconds. `-1` = follow the global Settings value; `0` = off;
     * `N` = fill N seconds before a live channel starts and after a rebuffer.
     */
    val livePrerollSecs: Int = FOLLOW_GLOBAL_PREROLL,
    /**
     * Live latency override as a [tv.own.owntv.core.settings.LiveLatency] name; `null` = follow the
     * global setting. [liveLatencyCustomSecs] carries the seconds and is only read when `CUSTOM`.
     */
    val liveLatencyMode: String? = null,
    val liveLatencyCustomSecs: Int = FOLLOW_GLOBAL_LATENCY_SECS,
    val createdAt: Long = 0L,
    /** Epoch ms of the last complete sync; `null` = never synced (the fresh-import path). */
    val lastSyncAt: Long? = null,
    /** "Give up after" for a live tune, in seconds; `null` = follow the global setting. */
    val liveTuneTimeoutSecs: Int? = null,
    /** An HTTP `Referer` this provider's streams require. RuTube needs none. */
    val httpReferer: String? = null,
    /** Whether this channel list is subscription-only. Always false for the free RuTube catalogue. */
    val isPaid: Boolean = false,
) {
    /** The built-in source's identity does not depend on any stored number. */
    val isBuiltIn: Boolean get() = type == SourceType.RUTUBE
}
