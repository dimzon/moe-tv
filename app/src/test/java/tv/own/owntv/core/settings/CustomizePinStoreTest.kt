package tv.own.owntv.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.util.Pin

/** The category lock PIN and the Settings lock PIN: global, separate, and each with its own job. */
class CustomizePinStoreTest {

    /**
     * In memory: a file-backed DataStore replaces its file by renaming over it, which Windows refuses
     * while the old file exists, so every second write would fail on a Windows build machine.
     */
    private class MemoryDataStore : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        private val lock = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    private val memory = MemoryDataStore()
    private val store = CustomizePinStore(memory)

    @Test
    fun `the Settings lock is off until a PIN is set`() = runBlocking {
        assertNull(store.settingsLockPin().first())
        store.setSettingsLockPin("1234")
        assertTrue(Pin.verify("1234", store.settingsLockPin().first()))
        store.setSettingsLockPin(null)
        assertNull(store.settingsLockPin().first())
    }

    @Test
    fun `the two PINs are separate`() = runBlocking {
        store.setCustomizePin("1111")
        store.setSettingsLockPin("2222")
        assertFalse(Pin.verifyCustomize("1111", store.settingsLockPin().first()))
        assertFalse(Pin.verifyCustomize("2222", store.customizePin().first()))
        store.setCustomizePin(null)
        assertTrue(Pin.verify("2222", store.settingsLockPin().first()))
        store.setCustomizePin("3333")
        store.setSettingsLockPin(null)
        assertTrue(Pin.verify("3333", store.customizePin().first()))
    }

    @Test
    fun `turning the lock off clears its long-press switch`() = runBlocking {
        store.setSettingsLockPin("1234")
        store.setSettingsLockMenus(true)
        assertTrue(store.settingsLockMenus().first())
        store.setSettingsLockPin(null)
        assertFalse(store.settingsLockMenus().first())
    }
}
