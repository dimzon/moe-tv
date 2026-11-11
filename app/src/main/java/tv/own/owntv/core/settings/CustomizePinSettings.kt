package tv.own.owntv.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.util.Pin

/**
 * The two global PINs kept in the settings file — the app has one user — each with its own job:
 *
 * - the **category lock PIN** ([customizePin]) opens locked categories;
 * - the **Settings lock PIN** ([settingsLockPin]) opens Settings, Backup & Restore and Local sync,
 *   and — when [settingsLockMenus] is on — the long-press changes (hide, move, rename, lock).
 *
 * Neither opens the other's door. Both are stored as salted hashes under the fixed keys
 * "customizePinHash" / "settingsPinHash". They are device-local: the file-backup sections that
 * carried per-profile PIN maps are gone with the profiles.
 *
 * [SettingsRepository] implements this by delegation, so callers keep using `settings.customizePin()`.
 */
interface CustomizePinSettings {
    fun customizePin(): Flow<String?>

    /** null/blank clears the category lock PIN. The Settings lock is not touched. */
    suspend fun setCustomizePin(pin: String?)

    /** The Settings lock PIN, a salted hash; null when the lock is off. */
    fun settingsLockPin(): Flow<String?>

    /** A PIN turns the Settings lock on; null/blank turns it off, with its long-press switch. */
    suspend fun setSettingsLockPin(pin: String?)

    /** Whether the long-press changes also ask for the Settings PIN. Off by default; means nothing while the lock is off. */
    fun settingsLockMenus(): Flow<Boolean>

    suspend fun setSettingsLockMenus(enabled: Boolean)
}

/**
 * [CustomizePinSettings] over the settings file, under one fixed key each: "customizePinHash"
 * (category lock PIN), "settingsPinHash" (Settings lock PIN) and "settingsLockMenus" (the
 * long-press switch). The Settings lock is simply on while its PIN is stored.
 */
internal class CustomizePinStore(private val dataStore: DataStore<Preferences>) : CustomizePinSettings {

    override fun customizePin(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[CUSTOMIZE_KEY]?.takeIf { it.isNotBlank() }
    }.distinctUntilChanged()

    override suspend fun setCustomizePin(pin: String?) {
        dataStore.edit { prefs ->
            if (pin.isNullOrBlank()) prefs.remove(CUSTOMIZE_KEY) else prefs[CUSTOMIZE_KEY] = Pin.hash(pin.trim())
        }
    }

    override fun settingsLockPin(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[SETTINGS_LOCK_PIN_KEY]?.takeIf { it.isNotBlank() }
    }.distinctUntilChanged()

    override suspend fun setSettingsLockPin(pin: String?) {
        dataStore.edit { prefs ->
            if (pin.isNullOrBlank()) {
                prefs.remove(SETTINGS_LOCK_PIN_KEY)
                prefs.remove(MENUS_KEY)
            } else {
                prefs[SETTINGS_LOCK_PIN_KEY] = Pin.hash(pin.trim())
            }
        }
    }

    override fun settingsLockMenus(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MENUS_KEY] == true
    }.distinctUntilChanged()

    override suspend fun setSettingsLockMenus(enabled: Boolean) {
        dataStore.edit { prefs ->
            if (enabled) prefs[MENUS_KEY] = true else prefs.remove(MENUS_KEY)
        }
    }

    private companion object {
        val CUSTOMIZE_KEY = stringPreferencesKey("customizePinHash")
        val SETTINGS_LOCK_PIN_KEY = stringPreferencesKey("settingsPinHash")
        val MENUS_KEY = booleanPreferencesKey("settingsLockMenus")
    }
}
