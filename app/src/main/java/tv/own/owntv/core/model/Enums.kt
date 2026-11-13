package tv.own.owntv.core.model

/** The one content kind the app has: live channels. Kept as the scoping key for customizations. */
enum class MediaType { LIVE }

/**
 * How a source delivers its content. The app has a single built-in provider since the
 * live-only redesign (phase ②): the free public RuTube catalogue. The old M3U / panel /
 * portal / local-backup kinds — and the per-kind columns, syncers and editors that fed them —
 * are gone.
 */
enum class SourceType { RUTUBE }
