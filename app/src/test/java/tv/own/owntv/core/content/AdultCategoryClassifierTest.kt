package tv.own.owntv.core.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.model.MediaType

class AdultCategoryClassifierTest {
    @Test
    fun `recognizes common provider adult category names`() {
        listOf(
            "ADULT",
            "UK | Adults 18+",
            "XXX Movies",
            "Erotik",
            "Für Erwachsene",
            "Para Adultos",
            "Dla dorosłych",
            "Для взрослых",
            "للكبار",
            "成人频道",
            "성인",
        ).forEach { name ->
            assertTrue("Expected adult category: $name", AdultCategoryClassifier.isAdult(name))
        }
    }

    @Test
    fun `does not classify adult swim or ordinary categories as adult`() {
        listOf(
            "Adult Swim",
            "US | Adult-Swim HD",
            "Kids",
            "Family Movies",
            "Channel 18",
            "Movies +1",
            "",
        ).forEach { name ->
            assertFalse("Expected safe category: $name", AdultCategoryClassifier.isAdult(name))
        }
    }

    @Test
    fun `classifies the category names the backfill made visible`() {
        // Categories that only reach a user because the sync now asks for the ones a provider's bulk
        // list omitted. These are real-world category names that merely look adult — Adult Swim is
        // a cartoon block.
        assertTrue(AdultCategoryClassifier.isAdult("18| FOR ADULTS"))
        assertFalse(AdultCategoryClassifier.isAdult("NETFLIX ADULT-SWIM"))
        assertFalse(AdultCategoryClassifier.isAdult("HBO MAX (ADULT SWIM)"))
    }

    @Test
    fun `adult swim exception does not mask another adult marker`() {
        assertTrue(AdultCategoryClassifier.isAdult("Adult Swim | XXX"))
    }

    @Test
    fun `a category the user has not hidden is never hidden`() {
        val adult = category(1, "Adults 18+")
        assertTrue(AdultCategoryClassifier.hiddenCategoryIds(listOf(adult), emptySet()).isEmpty())
    }

    private fun category(id: Long, name: String) = CategoryEntity(
        id = id,
        mediaType = MediaType.MOVIE,
        name = name,
    )
}
