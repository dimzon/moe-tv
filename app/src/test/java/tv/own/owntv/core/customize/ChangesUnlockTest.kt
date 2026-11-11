package tv.own.owntv.core.customize

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangesUnlockTest {

    @Test
    fun `the long-press changes open when the PIN is entered`() {
        val session = CategoryUnlockSession()
        assertFalse(session.changesUnlocked())
        assertTrue(session.unlockChanges())
        assertTrue(session.changesUnlocked())
    }

    @Test
    fun `leaving the app closes them again`() {
        val session = CategoryUnlockSession()
        session.unlockChanges()
        session.relockAll()
        assertFalse(session.changesUnlocked())
    }

    @Test
    fun `a PIN checked across a relock opens nothing`() {
        val session = CategoryUnlockSession()
        val before = session.epoch()
        session.relockAll()
        assertFalse(session.unlockChanges(since = before))
        assertFalse(session.changesUnlocked())
    }
}
