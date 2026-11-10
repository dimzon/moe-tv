package tv.own.owntv.player

/**
 * Play after a long pause on a live channel goes back to the live edge instead of resuming the old buffer.
 *
 * A paused live engine keeps whatever it had buffered, and the panel's stream has moved on: resuming it
 * played minutes-old pictures, or froze on data the server had long dropped. A short pause still resumes
 * where it was. Every pause on a live channel is this case now — no saved-copy stream exists any more.
 */
internal object LiveResume {
    const val LIVE_EDGE_AFTER_PAUSE_MS = 60_000L

    /** [pausedAtMs] is 0 when nothing paused this load. [fromSavedCopy] is always false now; the
     *  parameter stays so call sites read the same way. */
    fun backToLiveEdge(live: Boolean, fromSavedCopy: Boolean, pausedAtMs: Long, nowMs: Long): Boolean =
        live && !fromSavedCopy && pausedAtMs > 0 && nowMs - pausedAtMs > LIVE_EDGE_AFTER_PAUSE_MS
}
