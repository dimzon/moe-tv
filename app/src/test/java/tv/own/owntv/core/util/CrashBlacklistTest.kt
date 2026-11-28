package tv.own.owntv.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashBlacklistTest {

    private val franklinReport = """
        МОЁ ТВ 1.15 (148)
        build Droidlogic/franklin/franklin:11/RD2A.211001.002/eng.ugoos.20241111.140057:userdebug/test-keys
        signature 77d89d3e
        java.lang.RuntimeException: android.os.DeadSystemException
    """.trimIndent()

    @Test
    fun `the shipped blacklist suppresses the diagnosed Franklin crash`() {
        val entries = CrashBlacklist.load()
        val entry = entries.single { it.signature == "77d89d3e" }
        assertTrue(entry.reason.isNotBlank())
        assertTrue(CrashBlacklist.reasonFor(franklinReport, entries)?.isNotBlank() == true)
    }

    @Test
    fun `another signature is still uploaded`() {
        val entries = CrashBlacklist.parse(
            """[{"signature":"77d89d3e","reason":"diagnosed platform crash"}]""",
        )
        val report = franklinReport.replace("signature 77d89d3e", "signature 0123abcd")
        assertNull(CrashBlacklist.reasonFor(report, entries))
    }

    @Test
    fun `a report without a signature is still uploaded`() {
        val entries = CrashBlacklist.parse(
            """[{"signature":"77d89d3e","reason":"diagnosed platform crash"}]""",
        )
        val report = franklinReport.replace("signature 77d89d3e\n", "")
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
              {"signature":"77d89d3e"},
              {"reason":"no signature"},
              {"signature":" ","reason":"blank signature"},
              {"signature":"0123abcd","reason":" "}
            ]
            """.trimIndent(),
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `one incomplete row does not disable valid rows`() {
        val entries = CrashBlacklist.parse(
            """[{"signature":"77d89d3e"},{"signature":"0123abcd","reason":"known"}]""",
        )
        assertEquals(1, entries.size)
        assertEquals("0123abcd", entries.single().signature)
        assertFalse(entries.single().reason.isBlank())
    }
}
