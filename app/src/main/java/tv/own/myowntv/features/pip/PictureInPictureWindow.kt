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

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * The activity side of picture-in-picture. Android 12+ moves the activity into the window by itself
 * on Home (auto-enter, kept in step with [PictureInPicture.windowOffer]); older versions are asked to
 * in `onUserLeaveHint`, the callback for "the user is leaving", which a dialog or a screen of our own
 * does not trigger.
 *
 * Nothing here stops playback. In the window the activity is only paused, so the player keeps going;
 * closing the window stops the activity, and `MainActivity.onStop` releases the players exactly as it
 * does for Home without picture-in-picture.
 */
internal object PictureInPictureWindow {

    fun attach(activity: ComponentActivity, pip: PictureInPicture) {
        pip.onWindowChanged(activity.isInPictureInPictureMode)
        if (!pip.supported) return
        activity.addOnPictureInPictureModeChangedListener { info -> pip.onWindowChanged(info.isInPictureInPictureMode) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            activity.addOnUserLeaveHintListener {
                val aspect = pip.windowOffer ?: return@addOnUserLeaveHintListener
                if (activity.isInPictureInPictureMode) return@addOnUserLeaveHintListener
                // A TV that lists the feature but refuses the call simply stops playback as before.
                runCatching { activity.enterPictureInPictureMode(params(aspect, pip.playerBounds)) }
            }
        }
        val apply: (PipAspect?, PipBounds?) -> Unit = { aspect, bounds ->
            runCatching { activity.setPictureInPictureParams(params(aspect, bounds)) }
        }
        pip.bindWindow(apply)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = pip.unbindWindow(apply)
        })
    }

    private fun params(aspect: PipAspect?, playerBounds: PipBounds?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (aspect != null) {
            builder.setAspectRatio(Rational(aspect.width, aspect.height))
            playerBounds?.let { PipPolicy.sourceRect(it, aspect) }?.let { r ->
                builder.setSourceRectHint(Rect(r.left, r.top, r.right, r.bottom))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(aspect != null)
        return builder.build()
    }
}
