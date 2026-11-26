package tv.own.owntv.core.content

import java.text.Normalizer
import java.util.Locale
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.CategoryEntity

/**
 * Conservative classifier for provider category/folder names.
 *
 * Keep this classifier category-only: applying these words to programme or movie titles would create
 * false positives such as "Sex and the City". [isAdult] remains for name-driven chips (genre colouring); the
 * kids-profile policy it used to enforce is gone with the profiles.
 */
object AdultCategoryClassifier {
    private val safeExceptions = listOf(
        Regex("\\badult\\s+swim\\b"),
    )

    private val adultMarkers = listOf(
        Regex("(?:^|\\s)(?:18\\+|\\+18|21\\+|\\+21)(?:$|\\s)"),
        Regex("\\b(?:adult|adults|xxx|porn|porno|pornography|erotic|erotica|erotik|sex|sexy|playboy|hustler|redlight)\\b"),
        Regex("\\b(?:adulte|adultes|adulto|adultos|adulti|volwassenen|vuxen|voksne|aikuisille)\\b"),
        Regex("\\b(?:dla\\s+doros.ych|pro\\s+dospele|pro\\s+dospely|fur\\s+erwachsene|para\\s+adultos)\\b"),
        Regex("(?:yetiskin|для\\s+взрослых|взрослые|للكبار|بالغين|成人|成年人|성인)"),
        Regex("\\brated\\s+r\\b"),
    )

    fun isAdult(categoryName: String?): Boolean {
        if (categoryName.isNullOrBlank()) return false
        val raw = compact(categoryName.lowercase(Locale.ROOT))
        val withoutMarks = Normalizer.normalize(categoryName, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
        val normalized = compact(withoutMarks.lowercase(Locale.ROOT))
        return listOf(raw, normalized).distinct().any { candidate ->
            val withoutSafePhrases = safeExceptions.fold(candidate) { value, exception ->
                exception.replace(value, " ")
            }
            adultMarkers.any { it.containsMatchIn(withoutSafePhrases) }
        }
    }

    /** The ids of categories the user hid in Customize. */
    fun hiddenCategoryIds(
        categories: List<CategoryEntity>,
        customizedHiddenKeys: Set<String>,
    ): Set<Long> = categories.asSequence()
        .filter { category -> CustomizeKeys.category(category) in customizedHiddenKeys }
        .mapTo(linkedSetOf()) { it.id }

    private fun compact(value: String): String = value
            .replace(Regex("[^\\p{L}\\p{N}+]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
