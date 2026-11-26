package tv.own.owntv.core.player

/**
 * Core's one-way channel to the playback engine for a source's concurrent-session limit.
 *
 * The engine keeps the flag and decides whether a second tune is worth attempting. Core must not
 * reach into the player to set it, so the player registers [report] once at startup and core simply
 * announces the fact.
 *
 * Unset, every announcement is a no-op.
 */
object LiveSessionLimit {

    /** Set once at startup by whoever owns the playback engine. */
    var report: ((url: String) -> Unit)? = null

    /** Announce that the provider serving [url] permits a single concurrent session. */
    fun singleSession(url: String) {
        report?.invoke(url)
    }
}
