package tv.own.owntv.core.launcher

import android.net.Uri
import tv.own.owntv.core.database.entity.ChannelEntity

/** Internal deep links used by launcher rows and external launch intents. */
sealed interface LauncherDeepLink {
    fun toUri(): Uri

    data object OpenLiveSection : LauncherDeepLink {
        override fun toUri(): Uri = Uri.parse("owntv://open/live")
    }

    data class Live(
        val sourceId: Long? = null,
        val remoteId: String? = null,
        val name: String? = null,
        val itemId: Long? = null,
    ) : LauncherDeepLink {
        override fun toUri(): Uri = Uri.parse("owntv://play/live").buildUpon()
            .appendOptionalQueryParameter("sourceId", sourceId?.toString())
            .appendOptionalQueryParameter("remoteId", remoteId)
            .appendOptionalQueryParameter("name", name)
            .appendOptionalQueryParameter("itemId", itemId?.toString())
            .build()
    }

    companion object {
        fun parse(uri: Uri?): LauncherDeepLink? {
            if (uri == null || uri.scheme != "owntv") return null
            return when (uri.host) {
                "open" -> when (uri.pathSegments.firstOrNull()) {
                    "live" -> OpenLiveSection
                    else -> null
                }
                "play" -> when (uri.pathSegments.firstOrNull()) {
                    "live" -> Live(
                        sourceId = uri.queryLong("sourceId"),
                        remoteId = uri.queryString("remoteId"),
                        name = uri.queryString("name"),
                        itemId = uri.queryLong("itemId"),
                    )
                    else -> null
                }
                else -> null
            }
        }

        private fun Uri.queryString(name: String): String? =
            getQueryParameter(name)?.trim()?.takeIf { it.isNotEmpty() }

        private fun Uri.queryLong(name: String): Long? =
            getQueryParameter(name)?.toLongOrNull()
    }
}

private fun Uri.Builder.appendOptionalQueryParameter(name: String, value: String?): Uri.Builder = apply {
    if (!value.isNullOrBlank()) appendQueryParameter(name, value)
}

sealed interface LauncherLaunch {
    data class Live(val channel: ChannelEntity) : LauncherLaunch
}
