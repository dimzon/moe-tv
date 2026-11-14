package tv.own.myowntv.ui

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Whether Fullscreen mode (Settings › Appearance) is currently on. Provided by MainActivity from
 * the collected setting; [tv.own.myowntv.ui.components.MyOwnTVPopup] mirrors it onto dialog
 * windows, whose own insets controller would otherwise bring the system bars back over every
 * modal. Defaults to true — the setting's own default.
 */
val LocalFullscreenMode = staticCompositionLocalOf { true }

/**
 * Immersive fullscreen: hide or show the system status + navigation bars on THIS window — the
 * activity's or a dialog's (SmartRuTube's makeActivityFullscreen2 through the androidx compat
 * wrappers, so one branch serves both the API 26–29 legacy SYSTEM_UI_FLAG_* mapping and the
 * API 30+ WindowInsetsController). On a television there are no bars, so the call is a no-op there.
 */
fun Window.applyFullscreen(enabled: Boolean) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    if (enabled) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // Transient bars: a swipe from the edge reveals them briefly, then they hide themselves.
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (Build.VERSION.SDK_INT >= 28) {
            // Draw under the display cutout: without this a notched phone letterboxes the strip
            // where the hidden status bar used to sit.
            attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        if (Build.VERSION.SDK_INT >= 28) {
            attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
    }
}
