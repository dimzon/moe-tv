package tv.own.owntv.core.model

/** What kind of media an item is. Used to scope favorites/history/progress generically. */
enum class MediaType { LIVE, MOVIE, SERIES, EPISODE }

/**
 * How a source delivers its content. The app has a single built-in provider since the
 * live-only redesign (phase ②): the free public RuTube catalogue. The old M3U / panel /
 * portal / local-backup kinds — and the per-kind columns, syncers and editors that fed them —
 * are gone.
 */
enum class SourceType { RUTUBE }
