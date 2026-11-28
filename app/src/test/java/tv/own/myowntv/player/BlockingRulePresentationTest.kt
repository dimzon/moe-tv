package tv.own.myowntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.sync.RutubeBlockingRule
import tv.own.owntv.core.sync.RutubeBlockingRuleLanguage
import java.util.Locale

class BlockingRulePresentationTest {

    private val rule = RutubeBlockingRule(
        languages = listOf(
            RutubeBlockingRuleLanguage("rus", "Видео недоступно", "VPN"),
            RutubeBlockingRuleLanguage("eng", "Video is not available", "Rights holder"),
        ),
    )

    @Test
    fun `language codes select the matching provider text`() {
        assertEquals("Видео недоступно", selectBlockingRuleLanguage(rule, Locale("ru"))?.title)
        assertEquals("Video is not available", selectBlockingRuleLanguage(rule, Locale("en"))?.title)
    }

    @Test
    fun `unsupported locale falls back to English then first entry`() {
        assertEquals("Video is not available", selectBlockingRuleLanguage(rule, Locale("de"))?.title)
        val noEnglish = rule.copy(languages = rule.languages.take(1))
        assertEquals("Видео недоступно", selectBlockingRuleLanguage(noEnglish, Locale("de"))?.title)
    }

    @Test
    fun `image URL accepts only HTTPS RuTube owned hosts`() {
        assertEquals(
            "https://pic.rtbcdn.ru/block.png",
            safeBlockingRuleImageUrl("https://pic.rtbcdn.ru/block.png"),
        )
        assertEquals(
            "https://pic.rtbcdn.ru/block.png",
            safeBlockingRuleImageUrl("//pic.rtbcdn.ru/block.png"),
        )
        assertNull(safeBlockingRuleImageUrl("http://pic.rtbcdn.ru/block.png"))
        assertNull(safeBlockingRuleImageUrl("https://evilrutube.ru/block.png"))
        assertNull(safeBlockingRuleImageUrl("https://user:pass@pic.rtbcdn.ru/block.png"))
        assertNull(safeBlockingRuleImageUrl("file:///sdcard/block.png"))
    }
}
