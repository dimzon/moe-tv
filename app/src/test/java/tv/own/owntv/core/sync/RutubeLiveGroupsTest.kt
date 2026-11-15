package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feed-group mapping is shipped as JSON (`src/main/resources/rutube_live_groups.json`) and
 * parsed once by [RutubeApi]'s companion initializer. These tests pin the shipped content and the
 * defensive parsing rules; a broken file must never take the catalogue down with it.
 */
class RutubeLiveGroupsTest {

    @Test
    fun `the shipped mapping covers every feed group`() {
        val aliases = RutubeLiveGroups.load()
        assertEquals("federal", aliases["федеральные"])
        assertEquals("regional", aliases["региональные"])
        assertEquals("news", aliases["новостные"])
        assertEquals("fun", aliases["развлекательные"])
        assertEquals("movies", aliases["киноэфиры"])
        assertEquals("sport", aliases["спортивные"])
        assertEquals("kids", aliases["детские"])
        assertEquals("education", aliases["познавательные"])
        assertEquals("music", aliases["радиостанции"])
        assertEquals("music", aliases["радио"])
    }

    @Test
    fun `keys and aliases are normalised and blanks dropped`() {
        val aliases = RutubeLiveGroups.parse(
            """{"  Федеральные ":" FEDERAL ","": "news", "Киноэфиры":"  "}""",
        )
        assertEquals(mapOf("федеральные" to "federal"), aliases)
    }

    @Test
    fun `malformed json means no aliases`() {
        assertTrue(RutubeLiveGroups.parse("{not json").isEmpty())
    }
}
