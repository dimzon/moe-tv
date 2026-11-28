package tv.own.owntv.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashSignatureTest {

    @Test
    fun `wrapper uses the root cause signature`() {
        val root = errorWithFrames(IllegalStateException("root"), frame("Root", "fail"))
        val wrapper = RuntimeException("Unable to start activity", root)

        assertEquals(CrashSignature.of(root), CrashSignature.of(wrapper))
    }

    @Test
    fun `real SmartRuTube report vectors stay stable`() {
        assertEquals(
            "cbc6205d",
            CrashSignature.of(
                "java.lang.NullPointerException",
                arrayOf(
                    frame("android.view.ViewConfiguration", "get"),
                    frame("android.view.View", "<init>"),
                    frame("android.view.View", "<init>"),
                    frame("android.view.ViewGroup", "<init>"),
                    frame("android.view.ViewGroup", "<init>"),
                    frame("androidx.recyclerview.widget.RecyclerView", "<init>"),
                    frame("androidx.leanback.widget.BaseGridView", "<init>"),
                    frame("androidx.leanback.widget.VerticalGridView", "<init>"),
                ),
            ),
        )
        assertEquals(
            "9a0bbbae",
            CrashSignature.of(
                "java.lang.NullPointerException",
                arrayOf(
                    frame("com.jakewharton.processphoenix.PhoenixActivity", "onCreate"),
                    frame("android.app.Activity", "performCreate"),
                    frame("android.app.Activity", "performCreate"),
                    frame("android.app.Instrumentation", "callActivityOnCreate"),
                    frame("android.app.ActivityThread", "performLaunchActivity"),
                ),
            ),
        )
        assertEquals(
            "0aa360c3",
            CrashSignature.of(
                "java.io.FileNotFoundException",
                arrayOf(
                    frame("android.content.res.AssetManager", "nativeOpenXmlAsset"),
                    frame("android.content.res.AssetManager", "openXmlBlockAsset"),
                    frame("android.content.res.ResourcesImpl", "loadXmlResourceParser"),
                ),
            ),
        )
    }

    @Test
    fun `message file and line changes do not affect the signature`() {
        val first = frame("Example", "crash", "First.kt", 10)
        val second = frame("Example", "crash", "Second.kt", 999)

        assertEquals(
            CrashSignature.of("java.lang.IllegalStateException", arrayOf(first)),
            CrashSignature.of("java.lang.IllegalStateException", arrayOf(second)),
        )
    }

    @Test
    fun `frames after the first eight do not affect the signature`() {
        val frames = (0..8).map { frame("Example", "method$it") }.toTypedArray()
        val changed = frames.copyOf().also { it[8] = frame("Other", "method") }

        assertEquals(
            CrashSignature.of("java.lang.IllegalStateException", frames),
            CrashSignature.of("java.lang.IllegalStateException", changed),
        )
    }

    @Test
    fun `cause traversal stops after ten links`() {
        val chain = mutableListOf(errorWithFrames(IllegalStateException("depth0"), frame("Root", "fail")))
        repeat(12) { chain += RuntimeException("wrapper", chain.last()) }

        val expectedRoot = chain[10]
        assertEquals(
            CrashSignature.of(expectedRoot.javaClass.name, expectedRoot.stackTrace),
            CrashSignature.of(chain.last()),
        )
    }

    @Test
    fun `cyclic causes terminate and still produce a signature`() {
        val first = IllegalStateException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)

        assertTrue(CrashSignature.of(first).matches(Regex("[0-9a-f]{8}")))
    }

    private fun errorWithFrames(error: Throwable, vararg frames: StackTraceElement): Throwable =
        error.apply { stackTrace = frames.toList().toTypedArray() }

    private fun frame(className: String, methodName: String, fileName: String = "Source.kt", line: Int = 1) =
        StackTraceElement(className, methodName, fileName, line)
}
