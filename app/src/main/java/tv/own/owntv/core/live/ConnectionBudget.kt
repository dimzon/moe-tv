package tv.own.owntv.core.live

import android.content.res.Resources
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.SourceEntity

/** The tile counts the grid offers. Never reduced behind the user's back — see [connectionBudget]. */
const val MIN_MULTIVIEW_TILES = 1
const val MAX_MULTIVIEW_TILES = 4
const val DEFAULT_MULTIVIEW_TILES = 2

/** What a stream is wanted for. Recordings are gone; the single value keeps the call shapes stable. */
enum class StreamPurpose { WATCHING }

/** How many of one playlist's connections are already spoken for. */
data class OpenStreams(val watching: Int = 0) {
    val total: Int get() = watching
}

/** Why one more stream from this playlist cannot start. Each maps to one sentence for the user. */
enum class StreamRefusal {
    /** The playlist allows exactly one stream, and something already has it. */
    SINGLE_CONNECTION,

    /** Every connection this playlist allows is in use. */
    ALL_IN_USE,

    /** There is a connection free, but it is being kept so the user can still watch something. */
    RESERVED_FOR_WATCHING,
}

sealed interface StreamGrant {
    data object Allowed : StreamGrant

    data class Refused(
        val reason: StreamRefusal,
        /** What the playlist allows, for the sentence. 0 when unknown — never refused on that. */
        val maxConnections: Int,
        val open: OpenStreams,
    ) : StreamGrant
}

/**
 * May one more stream start on this playlist?
 *
 * The feature is never capped: the tile-count setting always offers up to four and nothing reduces
 * it behind the user's back. An individual stream is checked before it tunes, and if there is
 * nothing spare that one tile is told why, in a sentence, instead of failing into a spinner. The
 * provider's own HTTP 458 remains the backstop for a number that was wrong or stale.
 *
 * Everything here is arithmetic over [SourceEntity.maxConnections]: no Android, no I/O, no clock.
 *
 * @param source the playlist the stream would come from; null or an unknown limit allows everything.
 * @param open the streams that playlist already has running.
 * @param reserveOneForWatching kept for API compatibility; recordings are gone, so it no longer
 *   changes anything.
 */
fun connectionBudget(
    source: SourceEntity?,
    open: OpenStreams,
    purpose: StreamPurpose,
    reserveOneForWatching: Boolean = true,
): StreamGrant {
    // "Ignore connection limit" on the playlist reads as an unknown limit: every stream may try, and
    // the provider's own refusal is what stops one.
    val max = source?.takeIf { !it.ignoreConnectionLimit }?.maxConnections ?: 0
    // 0 = the playlist never said. Allow everything and let the provider answer; warning text
    // elsewhere tells the user that is what happened if a tile does fail.
    if (max <= 0) return StreamGrant.Allowed

    fun refuse(reason: StreamRefusal) = StreamGrant.Refused(reason, max, open)

    return when (purpose) {
        StreamPurpose.WATCHING -> when {
            open.total < max -> StreamGrant.Allowed
            max == 1 -> refuse(StreamRefusal.SINGLE_CONNECTION)
            else -> refuse(StreamRefusal.ALL_IN_USE)
        }
    }
}

/**
 * The sentence a refused tile shows in place of a picture. Says what the playlist allows and what is
 * currently using it.
 */
fun StreamGrant.Refused.displayText(res: Resources): String = when (reason) {
    StreamRefusal.SINGLE_CONNECTION -> res.getString(R.string.multiview_refused_single)
    // Plurals keyed on the limit. This reason only arises when the playlist allows two or more, so
    // the singular forms are never seen in practice — but a sentence that agrees with its number is
    // the difference between a translation and a template, and Android lint is right to insist.
    StreamRefusal.ALL_IN_USE -> res.getQuantityString(
        R.plurals.multiview_refused_all_in_use,
        maxConnections,
        maxConnections,
    )
    StreamRefusal.RESERVED_FOR_WATCHING -> res.getString(R.string.multiview_refused_reserved)
}
