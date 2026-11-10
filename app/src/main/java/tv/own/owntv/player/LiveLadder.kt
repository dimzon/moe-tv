package tv.own.owntv.player

/**
 * The fallback ladder for one live tune: which stream formats are still worth trying for the channel
 * currently on screen, in what order, and which have already been spent.
 *
 * Extracted from `LiveViewModel` so the sequencing — the most consequential logic in playback — can be
 * pinned by unit tests without a ViewModel, a database or a player.
 *
 * The caller keeps everything with a side effect on the outside world: logging, the failure record,
 * the actual re-tune, and the `.ts` pin the engine needs. This class only decides.
 */
class LiveLadder {

    /**
     * One stream format for the live engine. A live tune walks these in order, each **at most once**,
     * and that finiteness is the whole safety property: without it a failure that re-tuned the same
     * format could keep a channel spinning forever.
     */
    enum class Rung(val isHls: Boolean) {
        EXO_HLS(isHls = true),
        EXO_TS(isHls = false),
    }

    /** The stream URL this ladder was armed for; a newer tune of another channel supersedes it. */
    var url: String? = null
        private set

    private var order: List<Rung> = emptyList()
    private val spent = mutableSetOf<Rung>()

    /** The rung playback is on right now. */
    private var current: Rung? = null

    /**
     * The rungs this tune will walk, after filtering — exposed for tests and diagnostics.
     *
     * Order follows the "Prefer HLS" setting: the `.m3u8` sibling is tried first, the plain `.ts`
     * endpoint only as a fallback — the order every live player ships.
     */
    val plan: List<Rung> get() = order

    /**
     * When this tune runs out of time, or null when it has no budget.
     *
     * Every rung already had its own timeout, and nothing bounded their sum: two rungs at 20-30 s each
     * is a minute of black screen on a channel that has been removed from the panel. The budget is for
     * the WHOLE tune, so however the rungs are spent the user gets an answer at a predictable moment.
     *
     * Time the app deliberately asked the user to wait does not count against it — see [postponeDeadline].
     */
    private var deadlineAtMs: Long? = null

    fun isSpent(rung: Rung): Boolean = rung in spent

    /**
     * Reset the ladder for a fresh tune of [streamUrl]. Rungs already climbed are forgotten — a new tune
     * is a new chance, including for a channel that ended the last one on its final rung.
     *
     * The HLS rungs are dropped entirely when this channel has no HLS/TS distinction — "Prefer HLS" off
     * for the playlist, a Stalker cmd, or a URL that is natively `.m3u8` — leaving the plain `.ts`
     * attempt, which is what happened before any of this existed.
     *
     * Why per-format rungs at all: a channel's `.m3u8` and its `.ts` are different muxes, and a stream
     * that chokes on one can play as the other.
     *
     * [hasHlsAlternative] is suspended deliberately: resolving it hits the source row, and it must run
     * at the same point in the sequence it always did — after this ladder has claimed [streamUrl].
     */
    suspend fun arm(
        streamUrl: String,
        /** Whole-tune budget; [NO_BUDGET] (the default) leaves the tune unbounded, as it always was. */
        budgetMs: Long = NO_BUDGET,
        /** The caller's clock, passed in so the sequencing stays testable without one. */
        nowMs: Long = 0L,
        hasHlsAlternative: suspend () -> Boolean,
    ) {
        url = streamUrl
        spent.clear()
        deadlineAtMs = if (budgetMs <= NO_BUDGET) null else nowMs + budgetMs
        // The `.ts` rung is dropped when this session already learned this channel's `.m3u8` does not
        // work (there is nothing better to swap to), and the HLS rung when the channel has no HLS
        // alternative, or was learned as lacking one.
        val hasHls = hasHlsAlternative()
        order = listOf(Rung.EXO_HLS, Rung.EXO_TS).filterNot {
            it.isHls && (!hasHls || LiveStreamQuirks.lacksHlsVariant(streamUrl))
        }
        // The rung we are on right now is the first one of that order.
        current = order.firstOrNull()
        current?.let { spent += it }
    }

    /** False once a newer tune owns the ladder, so a late failure from the old one cannot advance it. */
    fun owns(streamUrl: String): Boolean = url == streamUrl

    /** True once this tune has spent its whole budget; [advance] then refuses to climb any further. */
    fun expired(nowMs: Long): Boolean = deadlineAtMs?.let { nowMs >= it } == true

    /** The moment this tune runs out, on the caller's clock, or null when it is unbounded. Re-read rather
     *  than cached by the caller's timer, so a [postponeDeadline] moves the alarm with it. */
    fun deadlineAt(): Long? = deadlineAtMs

    /**
     * Give the tune [byMs] more time, because the wait that just happened was one OwnTV asked for.
     *
     * The case this exists for is a provider back-off: an HTTP 429 with a `Retry-After` is the panel
     * naming the second at which the channel frees up, and the engine is counting it down behind the
     * spinner. Charging that wait to the budget would abandon a perfectly good channel over a delay the
     * app itself agreed to.
     */
    fun postponeDeadline(byMs: Long) {
        deadlineAtMs = deadlineAtMs?.plus(byMs)
    }

    /**
     * Give the tune a fresh [budgetMs] from [nowMs] (none when it has no budget). For a channel that DID
     * open and later turned out unplayable: the budget bounds how long a tune may take to show a
     * picture, and this one did, so the next rung must not be refused for time spent watching it.
     */
    fun restartDeadline(nowMs: Long, budgetMs: Long) {
        deadlineAtMs = if (budgetMs <= NO_BUDGET) null else nowMs + budgetMs
    }

    /**
     * The next untried rung, marked spent, or null when the ladder is exhausted (the caller then leaves
     * the failure on screen — there is genuinely nothing left).
     *
     * Advancing onto the `.ts` rung after the `.m3u8` rung was tried records the format lesson, so the
     * NEXT tune of this channel skips the dead rung instead of paying for it again. Never recorded when
     * there was no swap to blame.
     *
     * [failureWasAboutFormat] is the guard on that lesson. A panel that refuses the *request* — an
     * account-busy 458, a 403, a rate limit — says nothing whatsoever about whether its `.m3u8` works,
     * so nothing may be learned from it: recording a format lesson there teaches the app a fact it had
     * no evidence for, and the lesson would outlive the lockout that produced it.
     *
     * A tune that has spent its whole budget stops here whatever rungs are left ([expired]): the point
     * of the budget is that the total is bounded, which a "one more rung, it's nearly free" exception
     * would immediately undo.
     */
    fun advance(failureWasAboutFormat: Boolean = true, nowMs: Long = 0L): Rung? {
        if (expired(nowMs)) return null
        val next = order.firstOrNull { it !in spent } ?: return null
        spent += next
        current = next
        val streamUrl = url
        if (failureWasAboutFormat && streamUrl != null && !next.isHls && Rung.EXO_HLS in spent) {
            LiveStreamQuirks.rememberNoHlsVariant(streamUrl)
        }
        return next
    }

    /** Log label for a rung — Logcat and the playback error log only, never shown to a user. */
    fun label(rung: Rung): String = when (rung) {
        Rung.EXO_HLS -> "ExoPlayer + HLS"
        Rung.EXO_TS -> "ExoPlayer + TS"
    }

    companion object {
        /** An unbounded tune — every rung gets its own timeout and nothing bounds their sum. */
        const val NO_BUDGET = 0L

        /** Settings → "Give up on a channel after", in seconds. Declared in core, because the settings store has
         *  to know the default before any engine exists. */
        const val DEFAULT_BUDGET_SECS = tv.own.owntv.core.player.PlaybackDefaults.LIVE_TUNE_BUDGET_SECS

        /** The values offered in Settings; 0 is "Never", the unbounded behaviour that shipped before. */
        val BUDGET_CHOICES_SECS = listOf(15, 30, 60, 0)
    }
}
