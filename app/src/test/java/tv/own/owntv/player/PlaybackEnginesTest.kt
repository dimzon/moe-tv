package tv.own.owntv.player

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.player.PlaybackEngines.Pressure

/** Which `onTrimMemory` levels count as pressure. `UI_HIDDEN` on every Home press must not shrink
 *  the live engine's buffers for the rest of the session. */
@Suppress("DEPRECATION")
class PlaybackEnginesTest {

    @Test
    fun `hiding the UI is not memory pressure`() {
        assertEquals(Pressure.NONE, PlaybackEngines.pressureOf(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertEquals(Pressure.NONE, PlaybackEngines.pressureOf(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertEquals(Pressure.NONE, PlaybackEngines.pressureOf(ComponentCallbacks2.TRIM_MEMORY_MODERATE))
        assertEquals(Pressure.NONE, PlaybackEngines.pressureOf(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
    }

    @Test
    fun `running low trims, critical and complete go further`() {
        assertEquals(Pressure.LOW, PlaybackEngines.pressureOf(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertEquals(Pressure.CRITICAL, PlaybackEngines.pressureOf(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        assertEquals(Pressure.CRITICAL, PlaybackEngines.pressureOf(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }

    @Test
    fun `routine stop after a critical episode follows policy without claiming new pressure`() {
        val mode = decoderReleaseMode(
            cause = EngineStopCause.ROUTINE,
            hasPlayer = true,
            sawUhd = false,
            memoryPressureLatched = true,
        )
        val reason = decoderReleaseReason(
            cause = EngineStopCause.ROUTINE,
            sawUhd = false,
            memoryPressureLatched = true,
        )

        assertEquals("post_critical_policy", mode)
        assertEquals("post-critical memory policy", reason)
    }

    @Test
    fun `the critical stop itself and a later UHD release have distinct reasons`() {
        assertEquals(
            "critical_pressure",
            decoderReleaseMode(EngineStopCause.CRITICAL_PRESSURE, true, false, true),
        )
        assertEquals(
            "critical memory pressure",
            decoderReleaseReason(EngineStopCause.CRITICAL_PRESSURE, false, true),
        )
        assertEquals(
            "UHD channel left",
            decoderReleaseReason(EngineStopCause.ROUTINE, true, false),
        )
        assertEquals(
            "reuse",
            decoderReleaseMode(EngineStopCause.BACKGROUND, true, false, false),
        )
    }

    @Test
    fun `android 14 memory info maps to the same two steps`() {
        val t = 200L shl 20
        assertEquals(Pressure.NONE, PlaybackEngines.pressureOf(lowMemory = false, availMem = t * 2 + 1, threshold = t))
        assertEquals(Pressure.LOW, PlaybackEngines.pressureOf(lowMemory = false, availMem = t * 2, threshold = t))
        assertEquals(Pressure.LOW, PlaybackEngines.pressureOf(lowMemory = false, availMem = t + 1, threshold = t))
        assertEquals(Pressure.CRITICAL, PlaybackEngines.pressureOf(lowMemory = false, availMem = t, threshold = t))
        assertEquals(Pressure.CRITICAL, PlaybackEngines.pressureOf(lowMemory = true, availMem = t * 4, threshold = t))
    }
}
