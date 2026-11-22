package tv.own.owntv.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one platform crash the recorder is allowed to survive is recognized, and nothing else is. */
class PlatformCrashFilterTest {

    /** Verbatim shape of the 2026-11-21 crash: Android 14 TV, ViewRootImpl cleanup on a dead window. */
    private fun platformCrash(): Throwable = NullPointerException(
        "Attempt to read from field 'android.content.Context android.view.View.mContext' " +
            "on a null object reference in method 'boolean android.view.ViewRootImpl.getAccessibilityFocusedRect(android.graphics.Rect)'",
    ).apply {
        stackTrace = arrayOf(
            StackTraceElement("android.view.ViewRootImpl", "getAccessibilityFocusedRect", "ViewRootImpl.java", 5290),
            StackTraceElement("android.view.ViewRootImpl", "updateKeepClearForAccessibilityFocusRect", "ViewRootImpl.java", 5394),
            StackTraceElement("android.view.ViewRootImpl", "setAccessibilityFocus", "ViewRootImpl.java", 5634),
            StackTraceElement("android.view.ViewRootImpl\$ViewRootHandler", "handleMessageImpl", "ViewRootImpl.java", 6235),
            StackTraceElement("android.view.ViewRootImpl\$ViewRootHandler", "handleMessage", "ViewRootImpl.java", 6068),
            StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 106),
            StackTraceElement("android.os.Looper", "loop", "Looper.java", 294),
            StackTraceElement("android.app.ActivityThread", "main", "ActivityThread.java", 8248),
        )
    }

    @Test
    fun `the android 14 tv focus-rect cleanup crash is recognized`() {
        assertTrue(isRecoverablePlatformCrash(platformCrash()))
    }

    @Test
    fun `a crash that passes through app code is not survived`() {
        val error = platformCrash().apply {
            stackTrace = stackTrace.copyOf(stackTrace.size + 1).also { copy ->
                copy[copy.size - 1] = StackTraceElement("tv.own.owntv.player.PlaybackEngine", "stop", "PlaybackEngine.kt", 88)
            }
        }
        assertFalse(isRecoverablePlatformCrash(error))
    }

    @Test
    fun `a different npe is not survived`() {
        val error = NullPointerException("view.mContext must not be null").apply {
            stackTrace = arrayOf(
                StackTraceElement("tv.own.owntv.player.PlaybackEngine", "attach", "PlaybackEngine.kt", 42),
            )
        }
        assertFalse(isRecoverablePlatformCrash(error))
    }

    @Test
    fun `a non-npe in the same framework path is not survived`() {
        val error = IllegalStateException("check failed").apply {
            stackTrace = platformCrash().stackTrace
        }
        assertFalse(isRecoverablePlatformCrash(error))
    }

    @Test
    fun `the cleanup path with a frame missing is not survived`() {
        val error = platformCrash().apply {
            // The focus-rect call reached through some other route: not the known cleanup race.
            stackTrace = arrayOf(
                stackTrace[0],
                StackTraceElement("android.view.ViewRootImpl", "drawAccessibilityFocusedDrawableIfNeeded", "ViewRootImpl.java", 5073),
                StackTraceElement("android.view.ViewRootImpl", "performTraversals", "ViewRootImpl.java", 3191),
                StackTraceElement("android.view.ViewRootImpl\$ViewRootHandler", "handleMessageImpl", "ViewRootImpl.java", 6235),
            )
        }
        assertFalse(isRecoverablePlatformCrash(error))
    }

    @Test
    fun `a truncated stack is not survived`() {
        val error = platformCrash().apply {
            stackTrace = stackTrace.copyOf(2)
        }
        assertFalse(isRecoverablePlatformCrash(error))
    }
}
