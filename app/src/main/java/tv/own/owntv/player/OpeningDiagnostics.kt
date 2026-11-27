package tv.own.owntv.player

/**
 * One terminal opening snapshot for the exported playback report. Deliberately contains no URL, host,
 * query, token or header: those must stay in the existing redacted network diagnostics only.
 */
internal data class OpeningDiagnostics(
    val outcome: String,
    val stage: String,
    val elapsedMs: Long,
    val budgetMs: Long,
    val postponedMs: Long,
    val resolveOutcome: String? = null,
    val resolveMs: Long? = null,
    val playerState: String? = null,
    val route: String? = null,
    val bufferedMs: Long? = null,
    val ready: Boolean = false,
    val firstFrame: Boolean = false,
    val decoder: String? = null,
    val decoderHardware: Boolean? = null,
    val decoderRebuilds: Int = 0,
    val lastBytesAgoMs: Long? = null,
    val errorCode: String? = null,
) {
    fun line(): String = buildString {
        append("event=open_terminal outcome=").append(outcome)
        append(" stage=").append(stage)
        append(" elapsedMs=").append(elapsedMs)
        append(" budgetMs=").append(budgetMs)
        append(" postponedMs=").append(postponedMs)
        append(" resolveOutcome=").append(resolveOutcome ?: "none")
        append(" resolveMs=").append(resolveMs ?: -1L)
        append(" playerState=").append(playerState ?: "none")
        append(" route=").append(route ?: "none")
        append(" bufferedMs=").append(bufferedMs ?: -1L)
        append(" ready=").append(ready)
        append(" firstFrame=").append(firstFrame)
        append(" decoder=").append(decoder ?: "none")
        append(" decoderHardware=").append(decoderHardware ?: "unknown")
        append(" decoderRebuilds=").append(decoderRebuilds)
        append(" lastBytesAgoMs=").append(lastBytesAgoMs ?: -1L)
        append(" errorCode=").append(errorCode ?: "none")
    }
}
