package tv.own.owntv.core.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure parse/auth logic of the companion server (no socket bound): PIN comparison, query
 * parsing and the bounded-body reader. The remote source-editing endpoints are gone (RuTube is the
 * only source), so what remains is the pairing/security surface.
 */
class CompanionHttpServerTest {

    private val protocol = CompanionHttpProtocol

    // ---- PIN comparison ----

    @Test
    fun `pin matches only when equal and same length`() {
        assertTrue(protocol.pinEquals("123456", "123456"))
        assertFalse(protocol.pinEquals("123456", "123457"))
        assertFalse(protocol.pinEquals("12345", "123456"))
        assertFalse(protocol.pinEquals("", "123456"))
    }

    // ---- query / form parsing ----

    @Test
    fun `parseQuery decodes pairs and plus-as-space`() {
        val map = protocol.parseQuery("a=1&b=hello+world&c=")
        assertEquals("1", map["a"])
        assertEquals("hello world", map["b"])
        assertEquals("", map["c"])
    }

    // ---- body cap ----

    private fun read(body: String, contentLength: String, limit: Int): String? =
        protocol.readBody(
            java.io.BufferedInputStream(java.io.ByteArrayInputStream(body.toByteArray())),
            mapOf("content-length" to contentLength),
            limit,
        )

    @Test
    fun `uploads and the sync import get the large cap, everything else the small one`() {
        assertEquals(CompanionHttpProtocol.UPLOAD_BODY_LIMIT, protocol.maxBodyBytes("/backup"))
        assertEquals(CompanionHttpProtocol.UPLOAD_BODY_LIMIT, protocol.maxBodyBytes("/background"))
        assertEquals(CompanionHttpProtocol.UPLOAD_BODY_LIMIT, protocol.maxBodyBytes(CompanionHttpProtocol.AUTO_SYNC_IMPORT_PATH))
        assertEquals(CompanionHttpProtocol.FORM_BODY_LIMIT, protocol.maxBodyBytes("/"))
    }

    @Test
    fun `a body within the cap is read whole`() {
        assertEquals("pin=123456", read("pin=123456", "10", CompanionHttpProtocol.FORM_BODY_LIMIT))
        assertEquals("", read("", "0", CompanionHttpProtocol.FORM_BODY_LIMIT))
    }

    @Test
    fun `an oversized Content-Length is rejected without allocating it`() {
        // The pre-fix code did ByteArray(2_000_000_000) here — an unauthenticated OOM from the LAN.
        assertNull(read("", "2000000000", CompanionHttpProtocol.FORM_BODY_LIMIT))
        assertNull(read("", (CompanionHttpProtocol.UPLOAD_BODY_LIMIT + 1).toString(), CompanionHttpProtocol.UPLOAD_BODY_LIMIT))
    }

    @Test
    fun `a body longer than the cap is cut off even when the header lies small`() {
        assertNull(read("x".repeat(200), "200", 64))
    }

    /** It must not hang either; the server catches the throw and drops the request unanswered. */
    @Test(expected = java.io.EOFException::class)
    fun `a body that stops short of its Content-Length is refused, not kept in part`() {
        read("abc", "500", CompanionHttpProtocol.FORM_BODY_LIMIT)
    }
}
