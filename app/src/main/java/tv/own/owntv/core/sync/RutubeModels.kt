package tv.own.owntv.core.sync

import java.util.Locale

/** Parsed shapes of RuTube's public API (no auth needed). */
data class RutubeChannel(
    val videoId: String,
    val name: String,
    val title: String?,
    val logoUrl: String?,
    val categoryIds: List<Int>,
    val orderNumber: Int?,
    val isPaid: Boolean,
    val originType: String? = null,
    val authorId: Long? = null,
)

data class RutubeCategory(val id: Int, val name: String, val alias: String)

/**
 * A usable catalogue plus whether every requested page/bucket was read. Callers may upsert the rows
 * they got, but only a complete catalogue authorizes pruning or rebuilding memberships.
 */
data class RutubeCatalogue(val channels: List<RutubeChannel>, val isComplete: Boolean)

/** One row of the official TV feed (`pangolin/api/tv/feeds/live_smarttv/`): its group name and list URL. */
data class RutubeLiveRow(val name: String, val url: String)

private const val TAIL_ORDER = 1_000_000

/** RuTube's `order_number` "no slot" markers: the plain tail and the wide one six rows carry. */
private const val NO_NUMBER = 999
private const val NO_NUMBER_WIDE = 99999

/** The provider number when RuTube carries one; null for the rows it leaves unnumbered. */
internal fun RutubeChannel.providerNumber(): Int? =
    orderNumber?.takeUnless { it <= 0 || it == NO_NUMBER || it == NO_NUMBER_WIDE }

/**
 * Numbers for the whole catalogue. The first channel for a real `order_number` keeps it; a duplicate
 * gets the first free number after it (so RuTube's two channel-1 rows become 1 and 2). Rows RuTube
 * leaves unnumbered (ТВ «Известия», the regional tail, feed-only channels) get an app-owned number
 * after the highest provider number. Every number is unique, so the column never shows two channels
 * under one number and direct tune has one target. Fallback values move with the catalogue.
 */
internal fun rutubeChannelNumbers(channels: List<RutubeChannel>): Map<String, Int> {
    val providerNumbers = channels.mapNotNull { it.providerNumber() }.toSet()
    var tail = (providerNumbers.maxOrNull() ?: 0) + 1
    val used = HashSet<Int>(channels.size)
    val numbers = HashMap<String, Int>(channels.size)
    for (channel in channels) {
        val provider = channel.providerNumber()
        val number = when {
            provider != null && provider !in used -> provider
            provider != null -> {
                var candidate = provider + 1
                while (candidate in providerNumbers || candidate in used) candidate++
                candidate
            }
            else -> {
                while (tail in used) tail++
                tail++
            }
        }
        numbers[channel.videoId] = number
        used += number
    }
    return numbers
}


/**
 * Merges the curated `tvbroadcastinfo` catalogue with the official TV feed.
 *
 * Curated rows carry RuTube's `order_number`; feed rows fill the gaps but never replace the
 * editorial order. A playable feed twin of a filtered iframe placeholder inherits the
 * placeholder's number (Пятый, ТВ Центр, РЕН, СТС); genuinely feed-only channels go to the tail
 * in feed-row order. Genres are unioned and a paid flag from either source wins.
 */
internal fun mergeRutubeChannels(
    primary: List<RutubeChannel>,
    extra: List<RutubeChannel>,
): List<RutubeChannel> {
    val merged = LinkedHashMap<String, RutubeChannel>()
    val order = HashMap<String, Int>()

    // Only filtered guide rows are match targets: their playable feed twin should occupy the
    // editorial slot without exposing the iframe/paid placeholder itself.
    val filteredByAuthor = HashMap<Long, RutubeChannel>()
    val filteredByName = HashMap<String, RutubeChannel>()
    val filteredByBaseName = HashMap<String, RutubeChannel>()

    for (channel in primary) {
        if (!isCataloguePlayable(channel)) {
            rememberMatch(channel, filteredByAuthor, filteredByName, filteredByBaseName)
        }
    }

    fun matchFor(channel: RutubeChannel): RutubeChannel? {
        channel.authorId?.takeIf { it > 0 }?.let { filteredByAuthor[it] }?.let { return it }

        val name = normalizeChannelName(channel.name)
        if (name.isEmpty()) return null
        filteredByName[name]?.let { return it }
        return filteredByBaseName[baseChannelName(channel.name)]
    }

    fun effectiveOrder(channel: RutubeChannel): Int {
        var key = channel.orderNumber?.takeIf { it > 0 } ?: TAIL_ORDER
        if (isCataloguePlayable(channel)) {
            matchFor(channel)?.let { twin ->
                key = minOf(key, twin.orderNumber?.takeIf { it > 0 } ?: TAIL_ORDER)
            }
        }
        return key
    }

    for (channel in primary) {
        merged[channel.videoId] = channel
        order[channel.videoId] = effectiveOrder(channel)
    }

    var tail = TAIL_ORDER + 1

    for (channel in extra) {
        val existing = merged[channel.videoId]

        if (existing != null) {
            merged[channel.videoId] = existing.copy(
                categoryIds = (existing.categoryIds + channel.categoryIds).distinct(),
                isPaid = existing.isPaid || channel.isPaid,
            )
            continue
        }

        val twin = matchFor(channel)
        order[channel.videoId] = twin?.orderNumber?.takeIf { it > 0 } ?: tail++
        merged[channel.videoId] = if (twin == null) {
            channel
        } else {
            channel.copy(categoryIds = (channel.categoryIds + twin.categoryIds).distinct())
        }
    }

    return merged.values.sortedBy { order[it.videoId] ?: TAIL_ORDER }
}

private fun isCataloguePlayable(channel: RutubeChannel): Boolean =
    !channel.isPaid && channel.originType != "ifrm"

private fun rememberMatch(
    channel: RutubeChannel,
    byAuthor: MutableMap<Long, RutubeChannel>,
    byName: MutableMap<String, RutubeChannel>,
    byBaseName: MutableMap<String, RutubeChannel>,
) {
    channel.authorId?.takeIf { it > 0 }?.let {
        if (!byAuthor.containsKey(it)) byAuthor[it] = channel
    }

    val name = normalizeChannelName(channel.name)
    if (name.isEmpty()) return

    if (!byName.containsKey(name)) byName[name] = channel
    baseChannelName(channel.name).takeIf { it.isNotEmpty() }?.let {
        if (!byBaseName.containsKey(it)) byBaseName[it] = channel
    }
}

private fun normalizeChannelName(value: String?): String {
    if (value.isNullOrBlank()) return ""

    val lower = value.lowercase(Locale.ROOT).replace('ё', 'е')
    val out = StringBuilder(lower.length)
    var space = false

    for (ch in lower) {
        if (ch.isLetterOrDigit()) {
            if (space && out.isNotEmpty()) out.append(' ')
            out.append(ch)
            space = false
        } else {
            space = true
        }
    }

    return out.toString()
}

private fun baseChannelName(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val cut = value.indexOfAny(charArrayOf('.', '|', ':', '('))
    return normalizeChannelName(if (cut >= 0) value.substring(0, cut) else value)
}

data class RutubeProgramme(
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    /** Age restriction as the feed writes it ("16+", "12+", "0+"); `age_rating` in the mobile payload. */
    val ageRating: String? = null,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val iconUrl: String? = null,
)

data class RutubeChannelPage(
    val results: List<RutubeChannel>,
    val nextUrl: String?,
    /** False for malformed JSON or rows that could not be parsed, not merely an empty valid page. */
    val isValid: Boolean = true,
)

data class RutubeBriefPage(
    val channels: List<RutubeChannel>,
    val schedules: Map<String, List<RutubeProgramme>>,
    val nextUrl: String?,
)

sealed interface RutubePlayback {
    data class Ready(val url: String, val captions: List<RutubeCaption> = emptyList()) : RutubePlayback

    data class Unavailable(
        val reason: UnavailableReason,
        val detail: String? = null,
        val blockingRule: RutubeBlockingRule? = null,
    ) : RutubePlayback
}

data class RutubeBlockingRule(
    val imageUrl: String? = null,
    val languages: List<RutubeBlockingRuleLanguage> = emptyList(),
)

data class RutubeBlockingRuleLanguage(
    val lang: String? = null,
    val title: String? = null,
    val description: String? = null,
)

/** One sidecar subtitle advertised beside a RuTube stream — `play/options`' `captions[]` entry:
 *  `format` is `srt`/`vtt`, `file` a ready URL on `pic.rtbcdn.ru` (no auth), `langTitle` the menu
 *  label, `isAutogenerated` informational. */
data class RutubeCaption(
    val subId: Long,
    val format: String,
    val file: String,
    val langTitle: String? = null,
    val isAutogenerated: Boolean = false,
)

enum class UnavailableReason {
    LOGIN_REQUIRED,
    BLOCKING_RULE,
    IFRAME,
    NO_STREAM,
    NETWORK,
}
