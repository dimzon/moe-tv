package tv.own.owntv.player

/**
 * Identity of the focus preview currently being resolved. A request is not promotable until its URL
 * and captions have resolved and the channel has actually been handed to the playback engine.
 */
internal data class PreviewRequestState(
    val generation: Long = 0L,
    val channelId: String? = null,
    val committed: Boolean = false,
) {
    fun begin(channelId: String): PreviewRequestState =
        copy(generation = generation + 1L, channelId = channelId, committed = false)

    fun isCurrent(generation: Long, channelId: String): Boolean =
        this.generation == generation && this.channelId == channelId

    fun isCommitted(channelId: String): Boolean = committed && this.channelId == channelId

    fun commit(): PreviewRequestState = copy(committed = true)

    fun invalidate(): PreviewRequestState =
        copy(generation = generation + 1L, channelId = null, committed = false)
}
