package tv.own.myowntv.features.home

/** What a Keep watching card shows in its 16:9 frame. */
sealed interface HomeStill {
    /** A picture filling the frame, cropped to 16:9 (a poster is cropped too). */
    data class Picture(val url: String) : HomeStill

    /** A channel logo, fitted in the middle of the frame. */
    data class Logo(val url: String) : HomeStill

    data object None : HomeStill
}

/**
 * The still for a Keep watching card. The cards are the last-watched channels, so it is the channel
 * logo on the plate backdrop; nothing else is left with the VOD rails gone.
 */
fun homeStill(channelLogo: String?): HomeStill {
    fun String?.usable() = this?.takeIf { it.isNotBlank() }
    return channelLogo.usable()?.let(HomeStill::Logo) ?: HomeStill.None
}
