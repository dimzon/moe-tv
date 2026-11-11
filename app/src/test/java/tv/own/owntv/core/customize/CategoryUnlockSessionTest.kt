package tv.own.owntv.core.customize

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.model.MediaType

/** What the PIN opened lasts for the session only: leaving the app locks it again. */
class CategoryUnlockSessionTest {

    @Test
    fun `an opened category stays open for that section only`() = runBlocking {
        val s = CategoryUnlockSession()
        s.unlock(MediaType.LIVE, "1:adult")
        assertTrue(s.isUnlocked(MediaType.LIVE, "1:adult"))
        assertFalse(s.isUnlocked(MediaType.MOVIE, "1:adult"))
        assertEquals(setOf("1:adult"), s.unlocked(MediaType.LIVE).first())
    }

    @Test
    fun `leaving the app locks everything again`() = runBlocking {
        val s = CategoryUnlockSession()
        s.unlock(MediaType.LIVE, "1:adult")
        s.unlock(MediaType.MOVIE, "1:films")
        s.relockAll()
        assertFalse(s.isUnlocked(MediaType.LIVE, "1:adult"))
        assertTrue(s.unlocked(MediaType.MOVIE).first().isEmpty())
    }

    @Test
    fun `locking an opened category closes just that one`() {
        val s = CategoryUnlockSession()
        s.unlock(MediaType.LIVE, "1:adult")
        s.unlock(MediaType.LIVE, "1:late")
        s.relock(MediaType.LIVE, "1:adult")
        assertFalse(s.isUnlocked(MediaType.LIVE, "1:adult"))
        assertTrue(s.isUnlocked(MediaType.LIVE, "1:late"))
    }

    @Test
    fun `a PIN checked before a relock opens nothing after it`() {
        val s = CategoryUnlockSession()
        val before = s.epoch()
        s.relockAll()
        assertFalse(s.unlock(MediaType.LIVE, "1:adult", since = before))
        assertFalse(s.isUnlocked(MediaType.LIVE, "1:adult"))
    }

    @Test
    fun `a PIN checked with no relock in between opens the category`() {
        val s = CategoryUnlockSession()
        s.relockAll()
        val before = s.epoch()
        assertTrue(s.unlock(MediaType.LIVE, "1:adult", since = before))
        assertTrue(s.isUnlocked(MediaType.LIVE, "1:adult"))
    }
}
