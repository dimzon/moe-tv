package tv.own.owntv.core.i18n

import android.content.res.Configuration
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Locale access that works on API 23 and up.
 *
 * `Configuration.locales` and `android.os.LocaleList` were added in API 24; reading them on
 * Android 6 (API 23) throws NoSuchFieldError before Application.onCreate can finish.
 * [ConfigurationCompat] uses the singular `Configuration.locale` field on older platforms and the
 * real locale list on newer ones, so every call site in this app goes through these helpers.
 */
object LocaleCompat {

    /** The effective locale list of [config], never null and empty only when the device has none. */
    fun listOf(config: Configuration): LocaleListCompat = ConfigurationCompat.getLocales(config)

    /** The primary locale of [config], falling back to the process default (never the device's). */
    fun firstOf(config: Configuration): Locale {
        val list = ConfigurationCompat.getLocales(config)
        return if (list.isEmpty) Locale.getDefault() else list[0]!!
    }

    /** BCP-47 tags of [config]'s locale list, comma-separated (the LocaleList.toLanguageTags form). */
    fun tagsOf(config: Configuration): String =
        ConfigurationCompat.getLocales(config).toLanguageTags().orEmpty()
}

/** Convenience: `configuration.firstLocaleCompat()` in place of `configuration.locales[0]`. */
fun Configuration.firstLocaleCompat(): Locale = LocaleCompat.firstOf(this)

/** Convenience: `configuration.localeTagsCompat()` in place of `configuration.locales.toLanguageTags()`. */
fun Configuration.localeTagsCompat(): String = LocaleCompat.tagsOf(this)
