package tv.own.owntv.core.sync

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.network.HttpClient

/** Partial provider responses are useful for upserts, but never authority to delete stored rows. */
class RutubeCatalogueCompletenessTest {

    @Test
    fun `one failed genre bucket keeps base rows and marks the catalogue incomplete`() = runTest {
        val client = client { url ->
            when {
                url.contains("category=5") -> response(500, "server error")
                else -> page(listOf(channelJson("base")), next = null)
            }
        }

        val result = RutubeApi(HttpClient(client)).channels(listOf(5))

        assertEquals(listOf("base"), result.channels.map { it.videoId })
        assertFalse(result.isComplete)
    }

    @Test
    fun `a pagination cycle is incomplete rather than a successful endpoint`() = runTest {
        val client = client { url ->
            when {
                url.endsWith("page=1") -> page(listOf(channelJson("first")), next = "https://rutube.ru/page=2")
                url.endsWith("page=2") -> page(listOf(channelJson("second")), next = "https://rutube.ru/page=1")
                else -> page(listOf(channelJson("base")), next = "https://rutube.ru/page=1")
            }
        }

        val result = RutubeApi(HttpClient(client)).channels()

        assertEquals(listOf("base", "first", "second"), result.channels.map { it.videoId })
        assertFalse(result.isComplete)
    }

    @Test
    fun `a malformed page is incomplete even when some rows parsed`() = runTest {
        val body = """{"results":[${channelJson("good")},{"missing":"author and title"}],"next":null}"""
        val client = client { response(200, body) }

        val result = RutubeApi(HttpClient(client)).channels()

        assertEquals(listOf("good"), result.channels.map { it.videoId })
        assertFalse(result.isComplete)
    }

    @Test
    fun `a failed live feed row keeps other rows and marks the catalogue incomplete`() = runTest {
        val client = client { url ->
            when {
                url.contains("/feeds/") -> response(
                    200,
                    """{"tabs":[{"name":"TV","resources":[
                        {"name":"Federal","url":"https://rutube.ru/row/federal"},
                        {"name":"Sport","url":"https://rutube.ru/row/sport"}]}]}""",
                )
                url.contains("/row/sport") -> response(500, "server error")
                else -> page(listOf(channelJson("federal")), next = null)
            }
        }

        val result = RutubeApi(HttpClient(client)).liveChannels(emptyList())

        assertEquals(listOf("federal"), result.channels.map { it.videoId })
        assertFalse(result.isComplete)
    }

    @Test
    fun `sync success carries catalogue completeness to cleanup callers`() {
        val incomplete = SyncResult.Success(catalogueComplete = false)
        val complete = SyncResult.Success()

        assertFalse(incomplete.catalogueComplete)
        assertTrue(complete.catalogueComplete)
    }

    @Test
    fun `all requested pages and rows make a complete catalogue`() = runTest {
        val client = client { url ->
            when {
                url.contains("/feeds/") -> response(
                    200,
                    """{"tabs":[{"name":"TV","resources":[
                        {"name":"Federal","url":"https://rutube.ru/row/federal"}]}]}""",
                )
                url.endsWith("/page=2") -> page(emptyList(), next = null)
                url.endsWith("/page=1") -> page(listOf(channelJson("base")), next = "https://rutube.ru/page=2")
                else -> page(listOf(channelJson("federal")), next = null)
            }
        }
        val api = RutubeApi(HttpClient(client))

        assertTrue(api.channels().isComplete)
        assertTrue(api.liveChannels(emptyList()).isComplete)
    }

    private fun client(respond: (String) -> Response) = OkHttpClient.Builder()
        .addInterceptor { chain -> respond(chain.request().url.toString()) }
        .build()

    private fun response(code: Int, body: String) = Response.Builder()
        .request(okhttp3.Request.Builder().url("https://rutube.ru/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody(null))
        .build()

    private fun page(rows: String, next: String? = null) = response(
        200,
        """{"results":$rows,"next":${next?.let { "\"$it\"" } ?: "null"}}""",
    )

    private fun page(rows: List<String>, next: String? = null) = page(rows.joinToString(",", "[", "]"), next)

    private fun channelJson(id: String) =
        """{"id":"$id","title":"Channel $id","author":{"name":"Author $id"},"is_livestream":true}"""
}
