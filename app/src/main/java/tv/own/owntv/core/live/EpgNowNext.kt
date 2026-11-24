package tv.own.owntv.core.live

import androidx.compose.runtime.Immutable
import tv.own.owntv.core.parser.XtEpgEntry

/** Now-playing + up-next EPG for the focused channel (null entries when the guide is unavailable). */
@Immutable
data class EpgNowNext(
    val now: XtEpgEntry?,
    val next: XtEpgEntry?,
    val upcoming: List<XtEpgEntry> = emptyList(),
    val previous: XtEpgEntry? = null,
    /** Whole days of stored guide coverage for this channel (latest stop − earliest start).
     *  Null when unknown/short-EPG only. Drives the "EPG · Nd" hint in the preview metadata. */
    val coverageDays: Int? = null,
    /** The airing programme's stored details (category, age rating, subtitle) — stored guide only;
     *  null on the short-EPG path, which carries none. */
    val nowDetails: tv.own.owntv.core.parser.EpgDetails? = null,
)

/**
 * The now/next answer only when it was looked up for [channelId]. The focused-channel read is
 * debounced, so while the highlight moves on the key still names the previous channel: showing
 * that answer under the new channel is what made the row briefly name the wrong programme.
 */
internal fun nowNextForChannel(keyed: Pair<String?, EpgNowNext?>, channelId: String?): EpgNowNext? =
    keyed.second.takeIf { channelId != null && keyed.first == channelId }

/**
 * What a channel row prints: the fresh answer for that channel when it has arrived, otherwise the
 * batched per-channel guide. The keyed guard keeps a stale answer from ever reaching another row.
 */
internal fun rowProgramme(
    keyed: Pair<String?, EpgNowNext?>,
    channelId: String,
    perChannel: Map<String, XtEpgEntry>,
): XtEpgEntry? =
    nowNextForChannel(keyed, channelId)?.now ?: perChannel[channelId]
