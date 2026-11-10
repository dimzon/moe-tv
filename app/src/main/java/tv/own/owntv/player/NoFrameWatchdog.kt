package tv.own.owntv.player

import android.os.Handler
import android.os.Looper

/**
 * The "connected, but no picture ever arrived" watchdog shared by the ExoPlayer-based engines.
 *
 * A stream that opens, buffers and even reports READY without ever rendering a frame produces no
 * error of its own, so nothing but a deadline can tell the engine that the item is not going to
 * play. Each engine used to hand-roll the same three lines — a main-looper [Handler], a one-shot
 * Runnable, and a `removeCallbacks` on first frame / error / stop / release — and the bug that shape
 * invites is a missed cancel, which fires the fallback over a stream that is playing perfectly.
 *
 * The timeout is passed in at [arm] time rather than owned here, because the budget depends on when
 * the deadline starts and on how the item decodes. The values, and why they are what they are:
 *
 * - **12 s, armed at load** — [HeroPreviewEngine], which arms before the player has connected, so the
 *   budget has to cover DNS, the HTTP open, the first segments and the decoder's own start-up before
 *   a frame can exist at all. An 8 s budget, written for a different path once upon a time, sits
 *   inside the normal opening time for a 4K stream on a slow provider, and tripping early costs a
 *   needless fallback over an item that was about to play. 12 s is the value that has been in
 *   production on the hero path.
 * - **8 s, armed at STATE_READY** — `LivePreviewEngine`'s "audio plays, no picture" check. It runs
 *   inside that engine's existing polled health watchdog rather than here: the poll is shared with
 *   four other checks and is device-tiered, so giving this one its own timer would add back a
 *   wake-up that was deliberately removed. Its 8 s starts once the stream is already READY, which is
 *   a later reference point than the 12 s above, not a shorter total.
 *
 * Main looper, so it shares ExoPlayer's application thread and holds nothing alive past [disarm].
 */
class NoFrameWatchdog(private val onExpired: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val fire = Runnable { onExpired() }

    /** Start (or restart) the deadline. Any previously armed deadline is dropped. */
    fun arm(timeoutMs: Long) {
        handler.removeCallbacks(fire)
        handler.postDelayed(fire, timeoutMs)
    }

    /** Cancel the deadline. Safe to call when nothing is armed, and from inside [onExpired]. */
    fun disarm() {
        handler.removeCallbacks(fire)
    }
}
