package tv.own.owntv.core.settings

/**
 * The settings-backed half of the one built-in source's DTO. A source used to be a database row;
 * with the row gone, the values that were genuinely user-adjustable live in Settings (global, since
 * there is one source) and are snapshotted into the DTO by
 * [tv.own.owntv.core.repository.SourceRepository].
 *
 * The constant identity (name, type, API URL, User-Agent) is not here — it is hardcoded in
 * [tv.own.owntv.core.repository.BuiltInSource].
 */
data class SourcePreferences(
    /** Global "Pre-buffer" seconds; 0 = Off. */
    val livePrerollSecs: Int,
    /** Global live latency preset name ([LiveLatency]). */
    val liveLatencyMode: String,
    /** Global custom buffer seconds, used when [liveLatencyMode] is CUSTOM. */
    val liveLatencyCustomSecs: Int,
    /** Global "Give up after" seconds; 0 = Never. */
    val liveTuneTimeoutSecs: Int,
    /** Epoch ms of the last complete catalogue sync; null = never synced. */
    val lastSyncAt: Long?,
)
