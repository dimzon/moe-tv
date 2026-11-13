package tv.own.myowntv.features.live
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

/** What OK on a channel row does in Live TV and the TV Guide. */
enum class OkAction { PREVIEW, WATCH }

/**
 * The "OK previews first" rule both screens share. With the screen's switch on and a preview pane that
 * can play, OK on a channel brings it into the pane; OK on the channel already there opens it full
 * screen. With the switch off, or no pane to play in, OK always opens full screen — a first OK that
 * previews into nothing would look like a dead key.
 *
 * [paneChannelId] is the channel the pane was last asked to play, whether it plays or not: on a panel
 * that allows one stream the pane stays blank and says so, and the second OK must still go full screen.
 */
fun okAction(twoStep: Boolean, paneCanPlay: Boolean, paneChannelId: String?, okChannelId: String): OkAction =
    if (twoStep && paneCanPlay && paneChannelId != okChannelId) OkAction.PREVIEW else OkAction.WATCH
