package tv.own.myowntv.features.live
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

/**
 * How long a CH+/- step waits before it actually opens the stream. Comfortably longer than the ~170 ms
 * D-pad repeat, so a held key resolves to one tune, and short enough that a step in a run still feels
 * immediate.
 */
internal const val ZAP_TUNE_DELAY_MS = 500L

/**
 * A CH+/- press with no other zap key for this long is "isolated" and opens at once instead of waiting
 * [ZAP_TUNE_DELAY_MS], on every panel, single-connection ones included.
 *
 * Why the wait exists at all: tuning on every step of a held key opened thirteen streams in 3.6 s on a
 * real remote, and providers read that as abuse — a one-session panel locked the account for
 * about two minutes (HTTP 458 on every channel) and an API portal answered 429. That was a burst of
 * opens, not one open: 1.5 s is several times the key repeat, so a held key or a quick run of taps still
 * waits and costs at most two opens — the first step and the channel the user stops on — never one per
 * step.
 *
 * One-connection panels used to wait on every press as well. The wait never protected their single
 * session: the previous channel keeps playing through it, and its socket closes when the new stream
 * starts, either way. So an isolated press opens at once there too (tested on a max_connections=1
 * panel without a 458); the burst rule above is what keeps those accounts safe.
 */
internal const val ZAP_ISOLATED_PRESS_MS = 1_500L

/**
 * The wait before a CH+/- step opens its stream: 0 for an isolated press, else [ZAP_TUNE_DELAY_MS].
 * The same on every panel (see [ZAP_ISOLATED_PRESS_MS]). A clock that reads earlier than the last press
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
