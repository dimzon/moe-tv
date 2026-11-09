package tv.own.owntv.core.sync

/** Parsed shapes of RuTube's public API (no auth needed). */
data class RutubeChannel(
    val videoId: String,
    val name: String,
    val title: String?,
    val logoUrl: String?,
    val categoryIds: List<Int>,
    val orderNumber: Int?,
    val isPaid: Boolean,
)

data class RutubeCategory(val id: Int, val name: String, val alias: String)

data class RutubeProgramme(
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val iconUrl: String? = null,
)

data class RutubeChannelPage(
    val results: List<RutubeChannel>,
    val nextUrl: String?,
)

data class RutubeBriefPage(
    val channels: List<RutubeChannel>,
    val schedules: Map<String, List<RutubeProgramme>>,
    val nextUrl: String?,
)

sealed interface RutubePlayback {
    data class Ready(val url: String) : RutubePlayback

    data class Unavailable(
        val reason: UnavailableReason,
        val detail: String? = null,
    ) : RutubePlayback
}

enum class UnavailableReason {
    LOGIN_REQUIRED,
    IFRAME,
    NO_STREAM,
    NETWORK,
}
