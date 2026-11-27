package tv.own.owntv.player

/** Why an engine is being stopped. Kept separate from the latched low-memory policy: after the first
 *  critical signal every later full release follows that policy, but it is not a new pressure event. */
internal enum class EngineStopCause(val logName: String) {
    ROUTINE("routine_stop"),
    BACKGROUND("background"),
    CRITICAL_PRESSURE("critical_pressure"),
    RELEASE("release"),
}

internal fun decoderReleaseMode(
    cause: EngineStopCause,
    hasPlayer: Boolean,
    sawUhd: Boolean,
    memoryPressureLatched: Boolean,
): String = when {
    !hasPlayer -> "none"
    cause == EngineStopCause.CRITICAL_PRESSURE -> "critical_pressure"
    memoryPressureLatched -> "post_critical_policy"
    sawUhd -> "uhd"
    else -> "reuse"
}

internal fun decoderReleaseReason(
    cause: EngineStopCause,
    sawUhd: Boolean,
    memoryPressureLatched: Boolean,
): String? = when {
    cause == EngineStopCause.CRITICAL_PRESSURE -> "critical memory pressure"
    memoryPressureLatched -> "post-critical memory policy"
    sawUhd -> "UHD channel left"
    else -> null
}
