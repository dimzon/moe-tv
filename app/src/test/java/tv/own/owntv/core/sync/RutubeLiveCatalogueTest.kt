package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The official TV feed (`pangolin/api/tv/feeds/live_smarttv/`) is the source the RUTUBE app itself
 * uses; these tests pin the two invariants the sync depends on: rows survive parsing with their
 * group names, and merging with the curated list keeps the curated metadata while unioning genres.
 * Pure JVM, like the rest of the parser tests.
 */
class RutubeLiveCatalogueTest {

    @Test
    fun `feed rows keep their names and collapse duplicate groups`() {
        val rows = RutubeParser.parseLiveFeedRows(
            """{"tabs":[
                 {"name":"Главная","resources":[
                   {"name":"Федеральные","url":"https://rutube.ru/pangolin/api/tv/resource/live_smarttv/60627"},
                   {"name":"Спортивные","url":"https://rutube.ru/pangolin/api/tv/resource/live_smarttv/58101"}]},
                 {"name":"Спорт","resources":[
                   {"name":"Спортивные","url":"https://rutube.ru/pangolin/api/tv/resource/live_smarttv/58101"},
                   {"name":"Радио","url":"https://rutube.ru/pangolin/api/tv/resource/live_smarttv/83167"}]}]}""",
        )
        assertEquals(
            listOf("Федеральные", "Спортивные", "Радио"),
            rows.map { it.name },
        )
        assertEquals(3, rows.map { it.url }.distinct().size)
    }

    @Test
    fun `feed rows follow tab and resource order numbers`() {
        val rows = RutubeParser.parseLiveFeedRows(
            """{"tabs":[
                 {"order_number":2,"resources":[
                   {"order_number":2,"name":"Спорт","url":"https://rutube.ru/pangolin/api/tv/resource/live_smarttv/2"},
                   {"order_number":1,"name":"Кино","url":"https://rutube.ru/pangolin/api/tv/resource/live_smarttv/1"}]},
                 {"order_number":1,"resources":[
                   {"order_number":1,"name":"Федеральные","url":"https://rutube.ru/pangolin/api/tv/resource/live_smarttv/0"}]}]}""",
        )
        assertEquals(listOf("Федеральные", "Кино", "Спорт"), rows.map { it.name })
    }

    @Test
    fun `a feed card without broadcast_category still parses`() {
        val page = RutubeParser.parseChannelPage(
            """{"resourceId":58101,"has_next":false,"next":null,"results":[
                 {"id":"5F5DC9D64B43DFC740E92F2BDF2B0D38",
                  "title":"Прямой эфир SKY SPORTS TENNIS",
                  "thumbnail_url":"https://pic.rtbcdn.ru/video/59/a9/x.jpg",
                  "duration":0,
                  "author":{"id":67464793,"name":"Live. Спортивные трансляции"},
                  "is_livestream":true,"is_on_air":true,"is_paid":false,
                  "common_subscription_product_codes":[]}]}""",
        )
        assertEquals(1, page.results.size)
        val channel = page.results.single()
        assertEquals("5f5dc9d64b43dfc740e92f2bdf2b0d38", channel.videoId)
        assertEquals("Live. Спортивные трансляции", channel.name)
        assertEquals("Прямой эфир SKY SPORTS TENNIS", channel.title)
        assertEquals("https://pic.rtbcdn.ru/video/59/a9/x.jpg?size=l&picture_type=ios", channel.logoUrl)
        assertTrue(channel.categoryIds.isEmpty())
        assertEquals(null, channel.orderNumber)
        assertEquals(67464793L, channel.authorId)
        assertEquals(null, channel.originType)
        assertFalse(channel.isPaid)
    }

    @Test
    fun `merge keeps curated metadata and unions genres`() {
        val curated = RutubeChannel(
            videoId = "54de765aeea96a73d23a24b4e2cab07d",
            name = "ТВОЙ КИНОТЕАТР | MOVIE TIME CINEMA",
            title = "ЛУЧШИЕ ФАНТАСТИЧЕСКИЕ ФИЛЬМЫ",
            logoUrl = "curated.jpg",
            categoryIds = listOf(5),
            orderNumber = 999,
            isPaid = false,
        )
        val fromFeed = RutubeChannel(
            videoId = "54de765aeea96a73d23a24b4e2cab07d",
            name = "Feed name",
            title = "Feed title",
            logoUrl = "feed.jpg",
            categoryIds = listOf(2),
            orderNumber = null,
            isPaid = true,
        )
        val feedOnly = RutubeChannel(
            videoId = "5f5dc9d64b43dfc740e92f2bdf2b0d38",
            name = "Live. Спортивные трансляции",
            title = "Прямой эфир SKY SPORTS TENNIS",
            logoUrl = "sky.jpg",
            categoryIds = listOf(6),
            orderNumber = null,
            isPaid = false,
        )

        val merged = mergeRutubeChannels(listOf(curated), listOf(fromFeed, feedOnly))
        assertEquals(2, merged.size)
        val first = merged[0]
        assertEquals("curated.jpg", first.logoUrl)
        assertEquals("ТВОЙ КИНОТЕАТР | MOVIE TIME CINEMA", first.name)
        assertEquals(999, first.orderNumber)
        assertEquals(listOf(5, 2), first.categoryIds)
        assertTrue("a paid flag from either source wins", first.isPaid)
        assertEquals("Live. Спортивные трансляции", merged[1].name)
    }

    @Test
    fun `merge sorts by guide order and appends feed-only channels`() {
        val first = channel("first", "Первый", 1)
        val late = channel("late", "Поздний", 100)
        val early = channel("early", "Ранний", 3)
        val feedOnly = channel("feed", "Feed only", null)

        val merged = mergeRutubeChannels(listOf(late, first, early), listOf(feedOnly))

        assertEquals(listOf("first", "early", "late", "feed"), merged.map { it.videoId })
    }

    @Test
    fun `playable duplicate uses the filtered placeholder order`() {
        val playable = channel("playable", "СТС", 999, authorId = 42)
        val placeholder = channel("placeholder", "СТС", 12, originType = "ifrm", authorId = 42)
        val late = channel("late", "Поздний", 20)

        val merged = mergeRutubeChannels(listOf(playable, placeholder, late), emptyList())

        assertEquals(listOf("playable", "placeholder", "late"), merged.map { it.videoId })
    }

    @Test
    fun `feed-only twin inherits placeholder order`() {
        val placeholder = channel("placeholder", "РЕН ТВ. Новости", 10, originType = "ifrm", categoryIds = listOf(2))
        val late = channel("late", "Поздний", 20)
        val feed = channel("feed", "РЕН ТВ", null, originType = "rst", categoryIds = listOf(4))

        val merged = mergeRutubeChannels(listOf(placeholder, late), listOf(feed))

        assertEquals(listOf("placeholder", "feed", "late"), merged.map { it.videoId })
        assertEquals(listOf(4, 2), merged[1].categoryIds)
    }

    @Test
    fun `known broken rows are removed before numbering and liveness probes`() {
        val normal = channel("normal", "Рабочий канал", 1)
        val paid = channel("paid", "Подписочный", 2, isPaid = true)
        val iframe = channel("iframe", "Заглушка", 3, originType = "ifrm")
        val ctv = channel("f785d738a845cc80658e887843a18ed9", "CTV", 4)
        val rbkUfa = channel("75957f55ed1e9b4e0ec00f8dd9594fcc", "РБК-Уфа", 5)
        val illi = channel("eda2da42eea89951e4135141a30915f7", "ИЛЛИ ТВ", 6)

        val filtered = RuTubeSyncer.filterCatalogueChannels(listOf(normal, paid, iframe, ctv, rbkUfa, illi))

        assertEquals(listOf(normal), filtered)
    }

    private fun channel(
        id: String,
        name: String,
        order: Int?,
        originType: String? = null,
        authorId: Long? = null,
        categoryIds: List<Int> = emptyList(),
        isPaid: Boolean = false,
    ) = RutubeChannel(
        videoId = id,
        name = name,
        title = null,
        logoUrl = null,
        categoryIds = categoryIds,
        orderNumber = order,
        isPaid = isPaid,
        originType = originType,
        authorId = authorId,
    )
}
