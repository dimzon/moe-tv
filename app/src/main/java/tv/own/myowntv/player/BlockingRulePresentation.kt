package tv.own.myowntv.player

import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tv.own.owntv.core.player.TrackLanguages
import tv.own.owntv.core.sync.RutubeBlockingRule
import tv.own.owntv.core.sync.RutubeBlockingRuleLanguage

internal fun selectBlockingRuleLanguage(
    rule: RutubeBlockingRule,
    locale: Locale,
): RutubeBlockingRuleLanguage? {
    val usable = rule.languages.filter { !it.title.isNullOrBlank() || !it.description.isNullOrBlank() }
    val wanted = locale.language.lowercase(Locale.ROOT)
    return usable.firstOrNull { normalizeLanguage(it.lang) == wanted }
        ?: usable.firstOrNull { normalizeLanguage(it.lang) == "en" }
        ?: usable.firstOrNull()
}

private fun normalizeLanguage(code: String?): String? {
    val value = code?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    return TrackLanguages.twoLetter(value) ?: value.take(2)
}

/** Provider-controlled image URL: only HTTPS RuTube-owned hosts are allowed. */
internal fun safeBlockingRuleImageUrl(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val candidate = if (value.startsWith("//")) "https:$value" else value
    val url = candidate.toHttpUrlOrNull() ?: return null
    if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    val host = url.host.lowercase(Locale.ROOT)
    val allowed = host == "rutube.ru" || host.endsWith(".rutube.ru") ||
        host == "rtbcdn.ru" || host.endsWith(".rtbcdn.ru")
    return url.toString().takeIf { allowed }
}
