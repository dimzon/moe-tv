package tv.own.myowntv.features.home
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*
import tv.own.owntv.player.ffmpeg.*

/** What a Keep watching card shows in its 16:9 frame. */
sealed interface HomeStill {
    /** A picture filling the frame, cropped to 16:9 (a poster is cropped too). */
    data class Picture(val url: String) : HomeStill

    /** A channel logo, fitted in the middle of the frame. */
    data class Logo(val url: String) : HomeStill

    data object None : HomeStill
}

/**
 * The still for a Keep watching card, best first: the TMDB backdrop (for an episode, the show's backdrop
 * and then the episode's own still — what [HomeHeroMetadata.backdropUrl] already resolves to), then the
 * provider's backdrop, then its poster, cropped. A channel has none of those, so it shows its logo.
 */
fun homeStill(
    tmdbBackdrop: String?,
    providerBackdrop: String?,
    poster: String?,
    channelLogo: String?,
): HomeStill {
    fun String?.usable() = this?.takeIf { it.isNotBlank() }
    return tmdbBackdrop.usable()?.let(HomeStill::Picture)
        ?: providerBackdrop.usable()?.let(HomeStill::Picture)
        ?: poster.usable()?.let(HomeStill::Picture)
        ?: channelLogo.usable()?.let(HomeStill::Logo)
        ?: HomeStill.None
}
