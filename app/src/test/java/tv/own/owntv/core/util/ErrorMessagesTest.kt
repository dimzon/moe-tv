package tv.own.owntv.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessagesTest {
    @Test
    fun `known host resolution failure is classified for localized presentation`() {
        assertEquals(
            FriendlySyncFailure.Unreachable,
            classifySyncFailure("Unable to resolve host example.test", online = true),
        )
    }

    @Test
    fun `a provider answering 513 refused the login`() {
        assertEquals(
            FriendlySyncFailure.AuthenticationRejected,
            classifySyncFailure("HTTP 513 for http://provider.test/api?username=***&password=***", online = true),
        )
    }

    /** A guide download whose gzip was cut off is a dropped connection: told so, and retried. */
    @Test
    fun `a cut gzip download is an interrupted download, worth a retry`() {
        val raw = "Unexpected end of ZLIB input stream"
        assertEquals(FriendlySyncFailure.StreamInterrupted, classifySyncFailure(raw, online = true))
        assertEquals(true, isTransientSyncError(raw, online = true))
    }

    @Test
    fun `unmapped external failure preserves its raw text`() {
        val raw = "Provider-specific failure code X17"

        assertEquals(
            FriendlySyncFailure.Unknown(raw),
            classifySyncFailure(raw, online = true),
        )
    }
}
