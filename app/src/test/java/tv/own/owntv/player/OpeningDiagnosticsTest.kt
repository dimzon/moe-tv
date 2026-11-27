package tv.own.owntv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningDiagnosticsTest {

    @Test
    fun `resolver null is distinct from a network timeout`() {
        val resolve = OpeningDiagnostics(
            outcome = "timeout",
            stage = "resolve",
            elapsedMs = 30_000L,
            budgetMs = 30_000L,
            postponedMs = 0L,
            resolveOutcome = "null",
            resolveMs = 812L,
        ).line()
        val network = OpeningDiagnostics(
            outcome = "timeout",
            stage = "network",
            elapsedMs = 30_000L,
            budgetMs = 30_000L,
            postponedMs = 0L,
            playerState = "BUFFERING",
            route = "hls",
            lastBytesAgoMs = 29_000L,
        ).line()

        assertTrue(resolve.contains("stage=resolve"))
        assertTrue(resolve.contains("resolveOutcome=null"))
        assertTrue(network.contains("stage=network"))
        assertTrue(network.contains("lastBytesAgoMs=29000"))
        assertFalse(network.contains("resolveOutcome=null"))
    }

    @Test
    fun `ready without a frame is not reported as success`() {
        val line = OpeningDiagnostics(
            outcome = "no_fallback",
            stage = "ready_no_frame",
            elapsedMs = 38_000L,
            budgetMs = 30_000L,
            postponedMs = 8_000L,
            playerState = "READY",
            ready = true,
            firstFrame = false,
            decoder = "OMX.example.avc",
            decoderHardware = true,
            bufferedMs = 9_000L,
        ).line()

        assertTrue(line.contains("ready=true"))
        assertTrue(line.contains("firstFrame=false"))
        assertTrue(line.contains("postponedMs=8000"))
    }

    @Test
    fun `snapshot has no field that can carry a signed URL`() {
        val line = OpeningDiagnostics(
            outcome = "engine_error",
            stage = "network",
            elapsedMs = 4_000L,
            budgetMs = 30_000L,
            postponedMs = 0L,
            errorCode = "ERROR_CODE_IO_BAD_HTTP_STATUS",
        ).line()

        assertFalse(line.contains("token="))
        assertFalse(line.contains("url="))
        assertFalse(line.contains("signed"))
    }
}
