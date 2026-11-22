package tv.own.owntv.core.util

/**
 * The one platform crash the app is allowed to survive instead of dying to.
 *
 * Early Android 14 releases (`ViewRootImpl.getAccessibilityFocusedRect`, guarded in later 14 QPRs
 * and Android 15) dereference the window root without a null check:
 *
 * ```java
 * final AccessibilityManager manager = AccessibilityManager.getInstance(mView.mContext);
 * // mView == null → NPE
 * ```
 *
 * The path runs when the system turns accessibility off — TalkBack disabled, its service crashed
 * or restarted: every ViewRootImpl receives a `MSG_CLEAR_ACCESSIBILITY_FOCUS_HOST` cleanup
 * message (`setAccessibilityFocus(null, null)` → `updateKeepClearForAccessibilityFocusRect()` →
 * `getAccessibilityFocusedRect()`). If one of the app's windows was torn down while that message
 * was still queued, the cleanup runs against a dead window and the process dies. The dispatch only
 * executes on TV-family firmware — it is gated by
 * `ViewConfiguration.isPreferKeepClearForFocusEnabled()`, the `config_preferKeepClearForFocus`
 * resource that is true on Android TV — and it needs no accessibility focus inside our views and
 * no accessibility code of ours, nothing but the unlucky timing. AOSP added exactly the missing
 * `mView == null` guard between android-14.0.0_r1 and r30 (its `Slog.w` reads "calling
 * getAccessibilityFocusedRect() while the mView is null"), so boxes whose firmware never reaches
 * those QPRs keep crashing. First seen 2026-11-21 on a Google-certified X11 box, Android 14
 * (API 34), ISSUETRACKER 515072441.
 *
 * Surviving it is safe: the interrupted dispatch is pure focus bookkeeping of a window that is
 * already gone, and the statement that crashed is the dispatch's first side effect. [CrashRecorder]
 * caps the recoveries per process, so a misjudged signature can never loop.
 */

/**
 * True when [error] is exactly the Android 14 TV `ViewRootImpl` cleanup NPE described above:
 * a `NullPointerException` whose top frames are the focus-rect computation reached through the
 * framework message handler, with no frame of ours anywhere in the stack. Deliberately strict —
 * anything that does not match dies the normal way.
 */
fun isRecoverablePlatformCrash(error: Throwable): Boolean {
    if (error !is NullPointerException) return false
    val st = error.stackTrace
    if (st.size < 4) return false
    if (st[0].className != "android.view.ViewRootImpl" ||
        st[0].methodName != "getAccessibilityFocusedRect"
    ) return false
    if (st[1].className != "android.view.ViewRootImpl" ||
        st[1].methodName != "updateKeepClearForAccessibilityFocusRect"
    ) return false
    if (st[2].className != "android.view.ViewRootImpl" ||
        st[2].methodName != "setAccessibilityFocus"
    ) return false
    // The message handler is an inner class, so its binary name carries the `$ViewRootHandler` suffix.
    if (!st[3].className.startsWith("android.view.ViewRootImpl") ||
        st[3].methodName != "handleMessageImpl"
    ) return false
    // The whole dispatch is framework-owned: a frame of ours would mean a different crash.
    return st.none { it.className.startsWith("tv.own") }
}
