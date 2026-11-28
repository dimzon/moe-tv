package tv.own.owntv.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashBlacklistTest {

    private val sampleReport = """
        МОЁ ТВ 1.15 (148)
        build example/device:11/test-keys
        signature 0123abcd
        java.lang.RuntimeException: example
    """.trimIndent()

    @Test
    fun `another signature is still uploaded`() {
        val entries = CrashBlacklist.parse(
            """[{"signature":"0123abcd","reason":"diagnosed platform crash"}]""",
        )
        val report = sampleReport.replace("signature 0123abcd", "signature fedcba98")
        assertNull(CrashBlacklist.reasonFor(report, entries))
    }

    @Test
    fun `a report without a signature is still uploaded`() {
        val entries = CrashBlacklist.parse(
            """[{"signature":"0123abcd","reason":"diagnosed platform crash"}]""",
        )
        val report = sampleReport.replace("signature 0123abcd\n", "")
        assertNull(CrashBlacklist.reasonFor(report, entries))
    }

    @Test
    fun `malformed json suppresses nothing`() {
        assertTrue(CrashBlacklist.parse("{not json").isEmpty())
    }

    @Test
    fun `entries missing a signature or reason are ignored`() {
        val entries = CrashBlacklist.parse(
            """
            [
              {"signature":"0123abcd"},
              {"reason":"no signature"},
              {"signature":" ","reason":"blank signature"},
              {"signature":"fedcba98","reason":" "}
            ]
            """.trimIndent(),
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `one incomplete row does not disable valid rows`() {
        val entries = CrashBlacklist.parse(
            """[{"signature":"0123abcd"},{"signature":"fedcba98","reason":"known"}]""",
        )
        assertEquals(1, entries.size)
        assertEquals("fedcba98", entries.single().signature)
        assertFalse(entries.single().reason.isBlank())
    }
}
