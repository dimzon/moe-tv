package tv.own.myowntv

import android.view.KeyEvent
import android.view.Window

/**
 * Keys whose framework fallback handler closes system dialogs when a press falls through.
 *
 * Every window of the app — each activity's and every Compose dialog's — ends its key dispatch
 * with ViewRootImpl's fallback event policy: `PhoneFallbackEventHandler`. Its CALL, CAMERA and
 * SEARCH branches call `PhoneWindow.sendCloseSystemWindows()` →
 * `ActivityManager.closeSystemDialogs()`, and since API 31 that call requires
 * `BROADCAST_CLOSE_SYSTEM_DIALOGS`, whose protection level (`signature|privileged|recents`) no
 * sideloaded app can hold. A press that falls through therefore kills the process with a
 * SecurityException on the main thread (field report: API 31 emulator, CALL while a popup was
 * open).
 *
 * OwnTV has no use for these keys, so every window consumes them instead:
 *
 * - [MainActivity.dispatchKeyEvent] guards the main window;
 * - [MyOwnTVApp.onCreate] wraps every activity's window centrally, before the per-process early
 *   returns, so even the restart process's [AppRestartActivity] is covered;
 * - [MyOwnTVPopup] wraps each Compose dialog window — a dialog is a window of its own, with its
 *   own ViewRootImpl and fallback handler, which the activity-level guard never sees.
 *
 * The activity-level guard alone shipped in 1.7 and still crashed on exactly that dialog path.
 * Kept pure so the policy is unit-testable without a Window.
 */
internal fun swallowsSystemDialogKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_CALL ||
        keyCode == KeyEvent.KEYCODE_CAMERA ||
        keyCode == KeyEvent.KEYCODE_SEARCH

/**
 * A [Window.Callback] that consumes the system-dialog keys after the wrapped callback declined
 * them. Everything else — the whole [Window.Callback] surface — is delegated to the wrapped
 * callback unchanged, so an Activity or Compose's DialogWrapper keeps its full behavior.
 */
internal class SystemDialogKeyCallback(private val inner: Window.Callback) :
    Window.Callback by inner {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        inner.dispatchKeyEvent(event) || swallowsSystemDialogKey(event.keyCode)
}

/**
 * Guarantee that this window never hands CALL/CAMERA/SEARCH to the fallback handler. Idempotent
 * (a wrapped callback is left alone) and a no-op on a null window or a null callback, so call
 * sites can stay unconditional.
 */
internal fun Window?.consumeSystemDialogKeys() {
    this ?: return
    val inner = callback ?: return
    if (inner is SystemDialogKeyCallback) return
    callback = SystemDialogKeyCallback(inner)
}
