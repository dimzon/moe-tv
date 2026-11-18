package tv.own.owntv.core.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Locale runtime helpers. Both operations work down to API 23:
 *
 *  - [wrap] returns a `Context` whose `Resources` resolve strings for the effective locale list.
 *  - [applyGlobally] sets the process `Locale`/`LocaleList` defaults so `java.text`, `java.time`,
 *    `String.format` and every other `Locale.getDefault()` reader follow the selection.
 *
 * `""` ([SYSTEM_DEFAULT_TAG]) means "follow the current device locale list". The device list is read
 * from `Resources.getSystem().configuration` — the process-wide system singleton — which is used
 * **only** for device-locale metadata here, never for app strings (app strings resolve from the
 * wrapped context). Selecting "System default" is therefore a durable instruction to follow the TV's
 * locale list, not "leave the current app locale unchanged".
 *
 * The public API returns [LocaleListCompat] instead of `android.os.LocaleList` (API 24+): on API 23
 * that class does not exist and even mentioning it in a signature can trip the verifier at the call
 * site. [ConfigurationCompat] maps to the singular `Configuration.locale` on older platforms.
 */
object AppLocale {

    /** The stored tag value meaning "follow the current device locale list". */
    const val SYSTEM_DEFAULT_TAG = ""

    /** The effective locale list for [tag], as a platform-independent value. */
    fun effectiveLocaleList(tag: String): LocaleListCompat {
        val trimmed = tag.trim()
        val deviceLocales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        if (trimmed.isEmpty()) return deviceLocales
        val primary = Locale.forLanguageTag(trimmed)
        if (deviceLocales.isEmpty) return LocaleListCompat.create(primary)
        val combined = ArrayList<Locale>(deviceLocales.size() + 1)
        combined.add(primary)
        for (i in 0 until deviceLocales.size()) {
            val l = deviceLocales[i] ?: continue
            if (l != primary) combined.add(l)
        }
        return LocaleListCompat.create(*combined.toTypedArray())
    }

    /**
     * A context whose `Resources` carry the effective locale list. `Configuration.setLocales(...)`
     * also writes the `SCREENLAYOUT_LAYOUTDIR` bits, so `ldrtl`-qualified drawables resolve for free;
     * [ConfigurationCompat.setLocales] applies them through the platform's own implementation on
     * API 24+ and does the equivalent via `Configuration.locale` on API 23. Returns [base] unchanged
     * when the effective list is empty (no locales to apply).
     */
    fun wrap(base: Context, tag: String): Context {
        val locales = effectiveLocaleList(tag)
        if (locales.isEmpty) return base
        val config = Configuration(base.resources.configuration)
        ConfigurationCompat.setLocales(config, locales)
        return base.createConfigurationContext(config)
    }

    /**
     * Applies the effective locale list to the process: `Locale.setDefault` and, on API 24+,
     * `LocaleList.setDefault`. Needed for `java.text` / `java.time` / `String.format` and every
     * other `Locale.getDefault()` reader — none of which Compose's locals touch. Not needed for
     * `DateFormat.getTimeFormat(ctx)`, which reads the context's own configuration.
     */
    fun applyGlobally(tag: String) {
        val locales = effectiveLocaleList(tag)
        if (locales.isEmpty) return
        Locale.setDefault(locales[0])
        // LocaleList is API 24+; behind the guard the class is never resolved on Android 6.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.setDefault(LocaleList(*(0 until locales.size()).mapNotNull { locales[it] }.toTypedArray()))
        }
    }

    /**
     * Compose needs localized Resources, but it must not receive the bare ContextImpl returned by
     * createConfigurationContext(). That context is not an Activity: frame-rate matching, WebView
     * theming, and activity launches all depend on being able to unwrap the host Activity.
     *
     * The wrapper keeps the Activity as its base and delegates only resource/assets lookups to the
     * localized configuration context. Theme, window services, application context, and
     * startActivity semantics remain those of the host Activity.
     */
    fun wrapForCompose(base: Context, tag: String): Context {
        val localized = wrap(base, tag)
        val activity = findActivity(base) ?: return localized
        if (localized === base) return base
        return ActivityResourceContext(activity, localized)
    }

    private fun findActivity(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}

/** A localized resource view which still unwraps to the host Activity. */
private class ActivityResourceContext(
    host: Activity,
    private val localized: Context,
) : ContextWrapper(host) {
    override fun getResources(): Resources = localized.resources
    override fun getAssets() = localized.assets
    override fun getTheme() = baseContext.theme
}
