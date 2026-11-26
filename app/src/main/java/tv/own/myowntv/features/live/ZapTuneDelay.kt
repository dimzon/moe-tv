package tv.own.myowntv.features.live

/**
 * How long a CH+/- step waits before it actually opens the stream. Comfortably longer than the ~170 ms
 * D-pad repeat, so a held key resolves to one tune, and short enough that a step in a run still feels
 * immediate.
 */
internal const val ZAP_TUNE_DELAY_MS = 500L

/**
 * A CH+/- press with no other zap key for this long is "isolated" and opens at once instead of waiting
 * [ZAP_TUNE_DELAY_MS].
 *
 * Tuning on every step of a held key opened thirteen streams in 3.6 s on a real remote. The delay
 * coalesces a held key or quick run of taps into at most two opens — the first step and the channel
 * the user stops on — never one per step.
 */
internal const val ZAP_ISOLATED_PRESS_MS = 1_500L

/**
 * The wait before a CH+/- step opens its stream: 0 for an isolated press, else [ZAP_TUNE_DELAY_MS].
 * The same for every source (see [ZAP_ISOLATED_PRESS_MS]). A clock that reads earlier than the last press
 * counts as isolated rather than wedging every step on the wait.
 */
internal fun zapTuneDelayMs(lastPressMs: Long?, nowMs: Long): Long {
    val isolated = lastPressMs == null || nowMs - lastPressMs !in 0 until ZAP_ISOLATED_PRESS_MS
    return if (isolated) 0L else ZAP_TUNE_DELAY_MS
}

/**
 * Why a CH+/- step waited (or didn't), for the diagnostics log next to the [zapTuneDelayMs] decision.
 * [singleConnection]: the host was flagged one-session.
 */
internal fun zapWaitReason(lastPressMs: Long?, nowMs: Long, singleConnection: Boolean): String {
    val panel = " (single-connection)".takeIf { singleConnection }.orEmpty()
    return if (zapTuneDelayMs(lastPressMs, nowMs) == 0L) "isolated$panel"
    else "rapid (previous press ${nowMs - (lastPressMs ?: nowMs)}ms ago)$panel"
}
