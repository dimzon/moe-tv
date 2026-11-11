package tv.own.owntv.core.sync.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Which EPG sync failures are worth another attempt.
 *
 * The case that prompted this: a guide feed that answered every request successfully with nothing in
 * it arrived as a plain `IOException`, which this worker read as "network trouble" and retried three
 * times with backoff — so Settings sat on "Connecting…" for six minutes before admitting a failure
 * that was certain from the first second. The RuTube guide reports "no schedule upstream" as empty
 * data, not as an error, so anything that reaches this classification as an exception is real
 * trouble: only a dropped connection or a truncated download earns a retry.
 */
class EpgSyncRetryTest {

    private val transient = IOException("unexpected end of stream on http://example.test/")

    @Test
    fun `a broken connection is still retried`() {
        assertTrue(shouldRetryEpgSync(transient, online = true, runAttemptCount = 0))
    }

    @Test
    fun `the retry budget is still respected`() {
        assertFalse(shouldRetryEpgSync(transient, online = true, runAttemptCount = MAX_EPG_RETRY_ATTEMPTS))
    }

    /** A bad URL or malformed data is terminal, exactly as before. */
    @Test
    fun `a permanent error is not retried`() {
        assertFalse(shouldRetryEpgSync(IllegalArgumentException("bad url"), online = true, runAttemptCount = 0))
    }
}
