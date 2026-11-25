package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbering pass over the merged RuTube catalogue: provider numbers as they arrive, and the
 * app's own sequential numbers for the rows RuTube leaves unnumbered. Pure JVM, like the rest of
 * the parser tests.
 */
class RutubeChannelNumbersTest {

    private fun channel(videoId: String, orderNumber: Int?) = RutubeChannel(
        videoId = videoId,
        name = videoId,
        title = null,
        logoUrl = null,
        categoryIds = emptyList(),
        orderNumber = orderNumber,
        isPaid = false,
    )

    @Test
    fun `provider numbers are kept as they arrive`() {
        val numbers = rutubeChannelNumbers(listOf(channel("a", 5), channel("b", 1501), channel("c", 3)))
        assertEquals(5, numbers["a"])
        assertEquals(1501, numbers["b"])
        assertEquals(3, numbers["c"])
    }

    @Test
    fun `duplicate provider numbers get unique app-assigned fallbacks`() {
        val numbers = rutubeChannelNumbers(
            listOf(channel("first", 1), channel("tnt", 1), channel("high", 1501), channel("unnumbered", 999)),
        )
        assertEquals(1, numbers["first"])
        assertEquals(2, numbers["tnt"])
        assertEquals(1501, numbers["high"])
        assertEquals(1502, numbers["unnumbered"])
        assertEquals(4, numbers.values.toSet().size)
    }

    @Test
    fun `duplicate fallback skips provider numbers and earlier fallbacks`() {
        val numbers = rutubeChannelNumbers(
            listOf(channel("first", 1), channel("tnt", 1), channel("channel2", 2), channel("another", 1)),
        )
        assertEquals(1, numbers["first"])
        assertEquals(2, numbers["channel2"])
        assertEquals(3, numbers["tnt"])
        assertEquals(4, numbers["another"])
    }

    @Test
    fun `the 999 and 99999 placeholders and a missing number all mean unnumbered`() {
        val numbers = rutubeChannelNumbers(
            listOf(channel("izvestia", 999), channel("ural1", 99999), channel("sky", null)),
        )
        assertEquals(listOf(1, 2, 3), listOf(numbers["izvestia"], numbers["ural1"], numbers["sky"]))
    }

    @Test
    fun `fallback numbers start after the highest provider number`() {
        val numbers = rutubeChannelNumbers(listOf(channel("a", 1501), channel("izvestia", 999)))
        assertEquals(1501, numbers["a"])
        assertEquals(1502, numbers["izvestia"])
    }

    @Test
    fun `fallback numbers skip every number the catalogue already uses`() {
        val numbers = rutubeChannelNumbers(
            listOf(channel("a", 102), channel("b", 105), channel("first", 999), channel("second", 999)),
        )
        assertEquals(106, numbers["first"])
        assertEquals(107, numbers["second"])
    }

    @Test
    fun `zero and negative order numbers are treated as unnumbered`() {
        val numbers = rutubeChannelNumbers(listOf(channel("a", 0), channel("b", -7), channel("c", 1)))
        assertEquals(1, numbers["c"])
        assertEquals(2, numbers["a"])
        assertEquals(3, numbers["b"])
    }

    @Test
    fun `every channel of the catalogue ends up with a number`() {
        val channels = listOf(
            channel("a", 1), channel("b", 999), channel("c", null), channel("d", 99999), channel("e", 1501),
        )
        val numbers = rutubeChannelNumbers(channels)
        assertEquals(channels.map { it.videoId }, numbers.keys.toList())
        assertTrue(numbers.values.all { it > 0 })
        assertEquals(1501, numbers["e"])
        assertEquals(1502, numbers["b"])
        assertEquals(1503, numbers["c"])
        assertEquals(1504, numbers["d"])
    }
}
