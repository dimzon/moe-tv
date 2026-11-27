package tv.own.owntv.player

/**
 * Whether a burst of `READY ↔ BUFFERING` transitions is a real micro-freeze.
 *
 * Transition count alone is not enough: a stream may stutter badly while its playback clock still
 * advances almost in real time. The old absolute 3-second progress threshold therefore killed a
 * measured 632 ms burst that advanced 597 ms. Progress is judged relative to the burst's duration.
 */
internal object RebufferFlapPolicy {
    const val WINDOW_MS = 6_000L
    const val LIMIT = 12
    private const val REQUIRED_PROGRESS_PERCENT = 80

    data class Decision(
        val windowExpired: Boolean,
        val starved: Boolean,
        val elapsedMs: Long,
        val advancedMs: Long,
        val requiredProgressMs: Long,
    )

    fun requiredProgressMs(elapsedMs: Long): Long {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        // ceil(elapsed * 80 / 100), without floating point on the playback thread.
        return (elapsed * REQUIRED_PROGRESS_PERCENT + 99L) / 100L
    }

    fun evaluate(
        nowMs: Long,
        positionMs: Long,
        windowStartMs: Long,
        transitionCount: Int,
        windowStartPosMs: Long,
    ): Decision {
        val elapsedMs = if (windowStartMs == 0L) WINDOW_MS + 1L else (nowMs - windowStartMs).coerceAtLeast(0L)
        val advancedMs = if (windowStartMs == 0L) 0L else positionMs - windowStartPosMs
        val required = requiredProgressMs(elapsedMs)
        val expired = windowStartMs == 0L || elapsedMs > WINDOW_MS
        val starved = !expired && transitionCount >= LIMIT && advancedMs < required
        return Decision(expired, starved, elapsedMs, advancedMs, required)
    }
}
