package tv.own.owntv.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HttpStatusBodyTest {

    @Test
    fun `error body capture is opt in`() = runBlocking {
        val plain = canned(404, "blocked").client
        val plainError = runCatching { plain.getText("https://example.test/options") }.exceptionOrNull()
        assertTrue(plainError is HttpClient.HttpStatusException)
        assertNull((plainError as HttpClient.HttpStatusException).body)

        val captured = canned(404, "blocked").client
        val capturedError = runCatching {
            captured.getText("https://example.test/options", captureErrorBody = true)
        }.exceptionOrNull()
        assertTrue(capturedError is HttpClient.HttpStatusException)
        assertEquals("blocked", (capturedError as HttpClient.HttpStatusException).body)
    }

    @Test
    fun `captured error body is bounded`() = runBlocking {
        val body = "x".repeat(70_000)
        val error = runCatching {
            canned(404, body).client.getText("https://example.test/options", captureErrorBody = true)
        }.exceptionOrNull() as HttpClient.HttpStatusException

        assertEquals(64_000, error.body?.length)
    }

    @Test
    fun `non success never invokes the success block`() = runBlocking {
        var called = false
        val error = runCatching {
            canned(404, "blocked").client.get("https://example.test/options", captureErrorBody = true) {
                called = true
                "unused"
            }
        }.exceptionOrNull()

        assertTrue(error is HttpClient.HttpStatusException)
        assertFalse(called)
    }

    @Test
    fun `244 follows the success body path and is not retried`() = runBlocking {
        val canned = canned(244, "blocked")
        val result = canned.client.get("https://example.test/options", maxAttempts = 3) {
            it.readBytes().decodeToString()
        }

        assertEquals("blocked", result)
        assertEquals(1, canned.calls.get())
    }

    private class Canned(val calls: AtomicInteger, val client: HttpClient)

    private fun canned(code: Int, body: String): Canned {
        val calls = AtomicInteger()
        val okhttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                calls.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return Canned(calls, HttpClient(okhttp))
    }
}
