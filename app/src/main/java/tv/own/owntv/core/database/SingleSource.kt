package tv.own.owntv.core.database

/**
 * The id of the one built-in RuTube source. Nothing stores it in the database any more (schema v5
 * dropped both the `sources` table and every `sourceId` column), but two code-level things still
 * use it as a namespace: the playback `contentKey` (`"<id>:<mediaType>:<remoteId>"`, which must stay
 * stable for a stream's saved zoom/audio choices) and `ChannelEntity.sourceId`, the "whose are you?"
 * synonym. A constant, never written.
 */
object SingleSource {
    const val id: Long = 1L
}
