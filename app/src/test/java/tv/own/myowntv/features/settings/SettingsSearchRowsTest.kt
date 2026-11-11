package tv.own.myowntv.features.settings
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The hand-written settings screens keep a list of their rows for Settings search beside the rows
 * themselves. This holds each list to its screen: a row title drawn there and missing from the list
 * would be unfindable by its own name again.
 *
 * Source-scanned, like the Video player catalogue test: the thing worth checking is the text.
 */
class SettingsSearchRowsTest {

    private fun read(name: String): String {
        val file = File("src/main/java/tv/own/myowntv/features/settings/$name")
        assertTrue("expected to run from the app module, cwd=${File(".").absolutePath}", file.isFile)
        return file.readText()
    }

    /** The string names inside `val <list> ... = listOf(...)`. */
    private fun listed(source: String, list: String): Set<String> {
        val body = Regex("""val $list\b[^=]*=\s*listOf\(([^)]*)\)""").find(source)?.groupValues?.get(1)
        assertTrue("$list not found", body != null)
        return Regex("""R\.string\.([a-z_0-9]+)""").findAll(body!!).map { it.groupValues[1] }.toSet()
    }

    // `title = …` on a row, or a Stage row's `val useTitle = …`; for
    // `if (testing) R.string.settings_testing else R.string.x` the row is the `else` string.
    private fun titles(source: String, notRows: Set<String>): Set<String> =
        Regex("""(?:title|Title) = stringResource\((?:if \(.*?\) R\.string\.[a-z_0-9]+ else )?R\.string\.([a-z_0-9]+)""").findAll(source)
            .map { it.groupValues[1] }.filterNot { it in notRows }.toSet()

    private fun check(file: String, list: String, notRows: Set<String> = emptySet()) {
        val source = read(file)
        val drawn = titles(source, notRows)
        assertTrue("no rows found in $file — has it changed shape?", drawn.isNotEmpty())
        assertEquals("$file rows missing from $list", emptySet<String>(), drawn - listed(source, list))
    }

    @Test
    fun `player button rows are searchable`() = check("PlayerButtonsSettingsScreen.kt", "PLAYER_BUTTONS_SEARCH_ROWS")

    // A Stage full page's own title is the screen itself, which has a search entry of its own.
    @Test
    fun `proxy rows are searchable`() = check("NetworkSettingsScreen.kt", "PROXY_SEARCH_ROWS", notRows = setOf("common_proxy"))

    @Test
    fun `dns rows are searchable`() = check("DnsSettingsScreen.kt", "DNS_SEARCH_ROWS", notRows = setOf("settings_dns", "common_save"))

    @Test
    fun `subtitle appearance rows are searchable`() {
        val source = read("VideoPlayerSettingsScreen.kt")
        // The Stage page's five style rows, each drawn as `SubStyleRow(stringResource(R.string.…), …)`.
        val drawn = Regex("""SubStyleRow\(stringResource\(R\.string\.([a-z_0-9]+)\)""").findAll(source)
            .map { it.groupValues[1] }.toSet()
        assertEquals(5, drawn.size)
        assertEquals(emptySet<String>(), drawn - listed(source, "SUBTITLE_APPEARANCE_SEARCH_ROWS"))
    }
}
