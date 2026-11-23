package tv.own.owntv.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class InstallFailureTest {

    @Test
    fun `installer permission refusal is not reported as a network error`() {
        val error = InstallPermissionException(SecurityException("install unknown apps not granted"))
        assertEquals(UpdateManager.Failure.InstallPermission, installFailureFor(error))
    }

    @Test
    fun `other installer failures are still install failures`() {
        val error = InstallException(IOException("session could not be opened"))
        assertEquals(UpdateManager.Failure.Install, installFailureFor(error))
    }

    @Test
    fun `download failures are left to the caller's mapping`() {
        assertNull(installFailureFor(IOException("network down")))
        assertNull(installFailureFor(SecurityException("unrelated")))
    }
}
