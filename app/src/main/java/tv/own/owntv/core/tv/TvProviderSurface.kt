package tv.own.owntv.core.tv

/** Which Android TV surface a provider row belongs to. */
enum class TvProviderSurface {
    WATCH_NEXT,
    RECENT_LIVE,
    /** Cards the user explicitly pinned to the Android TV launcher (see TvHomePublisher). */
    PINNED_LIVE,
    /** Launcher row (PreviewChannel) for one TV category — SmartRuTube-style category publishing. */
    CATEGORY_ROW,
    /** A channel card (PreviewProgram) inside a category row. */
    CATEGORY_PROGRAM,
}
