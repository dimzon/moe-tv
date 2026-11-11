package tv.own.owntv.core.i18n
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.LocaleList
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizedContentContextTest {
    @Test
    fun localizedComposeContextStillUnwrapsToHostActivity() {
        ActivityScenario.launch(LocaleTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val localized = activity.localizedContext
                assertNotNull("LocalizedContent must publish LocalContext", localized)
                val context = localized!!
                assertTrue("Compose LocalContext must remain a wrapper", context is ContextWrapper)
                assertSame("Activity must remain the ContextWrapper base", activity, (context as ContextWrapper).baseContext)
                assertSame("Activity theme must be preserved", activity.theme, context.theme)
                assertSame(activity, findActivity(context))
                assertTrue(context.resources.configuration.locales[0].language == "en")
            }
        }
    }

    @Test
    fun likely_subtags_supply_scripts_for_system_style_region_tags() {
        assertEquals("Latn", scriptForTag("de-DE"))
        assertEquals("Arab", scriptForTag("ar-EG"))
        assertEquals("Hans", scriptForTag("zh-CN"))
        assertTrue(sameScriptFamily("de-DE", "de"))
        assertTrue(!sameScriptFamily("zh-CN", "zh-TW"))
    }

    @Test
    fun locale_store_write_path_updates_process_defaults() {
        val originalLocale = Locale.getDefault()
        val originalLocales = LocaleList.getDefault()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val prefs = app.getSharedPreferences("locale_write_path_test", Context.MODE_PRIVATE)
        try {
            val store = LocaleStore.overPreferences(prefs, app)
            runBlocking { store.set("de") }
            assertEquals("Java default locale must follow the selected tag", "de", Locale.getDefault().language)
            assertEquals("LocaleList default must follow the selected tag", "de", LocaleList.getDefault()[0].language)
        } finally {
            prefs.edit().clear().commit()
            Locale.setDefault(originalLocale)
            LocaleList.setDefault(originalLocales)
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
