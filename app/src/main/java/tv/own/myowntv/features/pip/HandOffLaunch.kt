package tv.own.myowntv.features.pip
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
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
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

import android.content.Intent
import android.os.Looper
import org.koin.core.context.GlobalContext

/**
 * Opens [intent] as an [AppHandOff] when leaving now would open the picture-in-picture window. Every
 * activity start OwnTV makes passes through here: the application's and the activity's
 * `startActivity`, and the few `ActivityResultLauncher`s.
 *
 * The launch is then also marked FLAG_ACTIVITY_NO_USER_ACTION, Android's own "the user is not leaving":
 * it suppresses `onUserLeaveHint` and, from Android 12, the auto-enter into the window as well.
 *
 * With no window on offer (the option off, a TV without picture-in-picture, nothing playing) the intent
 * starts untouched, exactly as it did before the option existed. Off the main thread the offer cannot
 * be read, so a launch there only gets the flag, and only while the option is on.
 */
internal fun <T> startAsHandOff(intent: Intent, start: (Intent) -> T): T {
    val koin = GlobalContext.getOrNull() ?: return start(intent)
    val pip = koin.getOrNull<PictureInPicture>() ?: return start(intent)
    val notLeaving = { Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION) }
    if (!Looper.getMainLooper().isCurrentThread) {
        return start(if (pip.supported && pip.enabled.value) notLeaving() else intent)
    }
    if (pip.windowOffer == null) return start(intent)
    return koin.get<AppHandOff>().launch { start(notLeaving()) }
}
