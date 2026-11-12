package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shipped trim rules: prefixes/tails come off, a name that is only a prefix stays itself. */
class ChannelRenameSeedsTest {

    @Test
    fun stripsKnownPrefixesAndTails() {
        assertEquals(
            "National Geographic НА РУССКОМ ЯЗЫКЕ |  Nat Geo",
            ChannelRenameSeeds.cleanProviderName("Телеканалы мира онлайн · National Geographic НА РУССКОМ ЯЗЫКЕ |  Nat Geo"),
        )
        assertEquals(
            "Дворцовый мост и панорама Невы онлайн веб-камера Санкт-Петербург",
            ChannelRenameSeeds.cleanProviderName("Mobotix Webcams Russia · Дворцовый мост и панорама Невы онлайн веб-камера Санкт-Петербург"),
        )
        assertEquals(
            "ЛУЧШИЕ ДРАМАТИЧЕСКИЕ ФИЛЬМЫ",
            ChannelRenameSeeds.cleanProviderName("ТВОЙ КИНОТЕАТР | MOVIE TIME CINEMA · ЛУЧШИЕ ДРАМАТИЧЕСКИЕ ФИЛЬМЫ | КРУГЛОСУТОЧНЫЙ ПРЯМОЙ ЭФИР"),
        )
    }

    @Test
    fun bothPrefixAndTailComeOffOneName() {
        assertEquals(
            "ТВОЙ КИНОТЕАТР",
            ChannelRenameSeeds.cleanProviderName("ТВОЙ КИНОТЕАТР | MOVIE TIME CINEMA · ТВОЙ КИНОТЕАТР | КРУГЛОСУТОЧНЫЙ ПРЯМОЙ ЭФИР"),
        )
    }

    @Test
    fun anUntouchedNamePassesThrough() {
        assertEquals("Первый канал", ChannelRenameSeeds.cleanProviderName("Первый канал"))
    }

    @Test
    fun aNameThatIsOnlyAPrefixStaysItself() {
        val raw = "ТВОЙ КИНОТЕАТР | MOVIE TIME CINEMA · "
        assertEquals(raw, ChannelRenameSeeds.cleanProviderName(raw))
    }
}
