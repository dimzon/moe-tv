package tv.own.owntv.core.database

/**
 * The row id of the one built-in RuTube source. The channels row no longer stores a sourceId —
 * there is only ever one source — but playback and a few sorters still ask a channel "whose are
 * you?", so the answer is process-global instead of per-row. Kept current by the source repository
 * (the wizard's attach, a restore, every app start).
 */
object SingleSource {
    @Volatile
    var id: Long = 0L
}
