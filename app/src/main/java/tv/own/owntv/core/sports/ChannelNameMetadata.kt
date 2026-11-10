package tv.own.owntv.core.sports

/**
 * Live-only survivor of the removed VOD catalog-title parser: the language and picture-quality
 * extraction from an IPTV channel name ("DE|Sky Sport FHD"), with every VOD-only part
 * (title normalization, year, capability strings) dropped.
 *
 * The pattern gating is kept verbatim: each regex runs only when the name holds the literal text
 * that pattern needs — on a TV every regex call is a trip into ICU through JNI.
 */
internal object ChannelNameMetadata {

    data class Facets(val language: String?, val qualityRank: Int)

    fun parse(rawName: String): Facets {
        val t = Title(rawName)
        return Facets(language = extractLanguage(t), qualityRank = extractQualityRank(t))
    }

    /**
     * A title plus what the gates test: [folded] is the title upper-cased the way a case-insensitive
     * pattern compares it. The Kelvin sign and the long s are the only characters outside ASCII that
     * fold onto an ASCII letter (k, s), so they become K and S here; everything else that is not ASCII
     * matches no letter in these patterns.
     */
    private class Title(val raw: String) {
        val folded: String = buildString(raw.length) {
            for (c in raw) append(
                when (c) {
                    in 'a'..'z' -> c - 32
                    'K' -> 'K'
                    'S' -> 'S'
                    else -> c
                },
            )
        }

        fun has(vararg texts: String): Boolean = texts.any { folded.contains(it) }

        /** The case-sensitive language patterns need one of their separator characters. */
        val mayHaveExplicitLanguage = raw.any { it == '|' || it == '[' || it == '(' || it == '{' }
        val mayHaveLeadingLanguage = raw.any { it == '|' || it == ':' || it == '_' || it == '-' }

        val mayHaveResolution = has("8K", "4K", "UHD", "2160P", "3840P", "FHD", "1080P", "HD", "720P", "SD")
    }

    private fun extractLanguage(t: Title): String? {
        if (t.mayHaveExplicitLanguage) EXPLICIT_LANGUAGE.find(t.raw)?.groupValues?.get(1)?.let { return it.uppercase() }
        if (t.mayHaveLeadingLanguage) LEADING_LANGUAGE.find(t.raw)?.groupValues?.get(1)?.let { return it.uppercase() }
        return TRAILING_LANGUAGE.find(t.raw)?.groupValues?.get(1)?.uppercase()
    }

    private fun extractQualityRank(t: Title): Int = when {
        !t.mayHaveResolution -> 0
        t.has("8K") && EIGHT_K.containsMatchIn(t.raw) -> 5
        t.has("4K", "UHD", "2160P", "3840P") && UHD.containsMatchIn(t.raw) -> 4
        t.has("FHD", "1080P") && FHD.containsMatchIn(t.raw) -> 3
        t.has("HD", "720P") && HD.containsMatchIn(t.raw) -> 2
        t.has("SD") && SD.containsMatchIn(t.raw) -> 1
        else -> 0
    }

    private const val LANGUAGE_CODES = "DE|EN|FR|ES|PT|AR|NL|PL|TR|RU|BG|ZH|HR|CS|DA|ET|FI|EL|HE|HI|HU|ID|JA|KO|LV|LT|MS|NO|FA|RO|SR|SK|SL|SV|TH|UK|VI"
    private val EXPLICIT_LANGUAGE = Regex("""(?i)[|\[({]\s*($LANGUAGE_CODES)\s*[|\])}]""")
    private val LEADING_LANGUAGE = Regex("""^\s*($LANGUAGE_CODES)(?=\s*[|:_-])""")
    private val TRAILING_LANGUAGE = Regex("""\s($LANGUAGE_CODES)\s*$""")
    private val EIGHT_K = Regex("""(?i)\b8K\b""")
    private val UHD = Regex("""(?i)\b(?:4K|UHD|2160P|3840P)\b""")
    private val FHD = Regex("""(?i)\b(?:FHD|1080P)\b""")
    private val HD = Regex("""(?i)\b(?:HD|720P)\b""")
    private val SD = Regex("""(?i)\bSD\b""")
}
