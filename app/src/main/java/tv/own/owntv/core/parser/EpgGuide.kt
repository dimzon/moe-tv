package tv.own.owntv.core.parser

/**
 * A schedule row shown by the guide readers ([tv.own.owntv.core.live.EpgNowNext] and the Live
 * screens). [programmeId] is the stored programme's id, or 0 when the row came from no stored
 * source at all.
 *
 * Survives the provider-era purge of this package (like [XtCategory]) because the untouched
 * guide readers and screens import it under this exact package and name.
 */
data class XtEpgEntry(
    val title: String,
    val description: String?,
    val startMs: Long,
    val stopMs: Long,
    val programmeId: Long = 0,
)

/**
 * The optional parts of a stored guide programme behind the details line
 * ("Сериал · Сезон 2 Серия 17 · 16+ · 95 min"). Every field is null when the feed does not
 * carry it — the screens show only what is there, never a guessed value.
 *
 * All three are RuTube's own fields: `ext_categories[0].name`, `ageRating` and `subtitle`.
 * The duration in that line is derived from the programme's own times, not stored.
 *
 * Kept in this package for the same reason as [XtEpgEntry]: existing importers reference it by its
 * original path.
 */
data class EpgDetails(
    val category: String? = null,
    val ageRating: String? = null,
    val subtitle: String? = null,
) {
    /** The categories as a list, in feed order. RuTube files a programme under exactly one. */
    val categoryList: List<String>
        get() = listOfNotNull(category)
}
