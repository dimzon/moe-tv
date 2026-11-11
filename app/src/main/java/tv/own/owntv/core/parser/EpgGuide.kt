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
 * The optional parts of a stored guide programme behind the guide's details line
 * ("Animation · Family · 2023 · FSK 0 · 95 min"). Every field is null when the guide does not
 * carry it — the screens show only what is there, never a guessed value.
 *
 * [categories] holds up to [MAX_CATEGORIES] distinct category values joined by [CATEGORY_SEPARATOR];
 * a category name can itself contain a comma, so the join uses a character no feed writes.
 *
 * Kept in this package for the same reason as [XtEpgEntry]: existing importers reference it by its
 * original path. The RuTube guide fills it from its own payload.
 */
data class EpgDetails(
    val categories: String? = null,
    val year: Int? = null,
    val rating: String? = null,
    val lengthMin: Int? = null,
    val episode: String? = null,
) {
    /** The categories as a list, in feed order. */
    val categoryList: List<String>
        get() = categories?.split(CATEGORY_SEPARATOR).orEmpty()

    companion object {
        const val MAX_CATEGORIES = 3
        /** U+001F (unit separator): a character no guide feed ever writes inside a category name. */
        val CATEGORY_SEPARATOR: Char = 31.toChar()
        val NONE = EpgDetails()
    }
}