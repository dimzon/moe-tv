package tv.own.owntv.core.sync

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.network.HttpClient

/** A truncated HTTP-200 payload is a failure, never permission to prune or erase a good guide. */
class RutubeParserIntegrityTest {

    @Test
    fun malformedCategoryRowFailsInsteadOfReturningAPartialList() = runTest {
        val api = RutubeApi(HttpClient(client {
            response(200, """{"results":[{"id":1,"name":"News","alias":"news"},{"name":"missing id"}]}""")
        }))

        val error = runCatching { api.categories() }.exceptionOrNull()
        assertTrue(error is RutubeApi.RutubeException)
    }

    @Test
    fun malformedLiveFeedRowMakesTheCatalogueIncomplete() = runTest {
        val api = RutubeApi(HttpClient(client {
            response(
                200,
                """{"tabs":[{"name":"TV","resources":[
                    {"name":"Federal","url":"https://rutube.ru/row/federal"},
                    {"name":"Sport"}]}]}""",
            )
        }))

        val result = api.liveChannels(emptyList())
        assertFalse(result.isComplete)
        assertTrue(result.channels.isEmpty())
    }

    @Test
    fun malformedChannelCategoryMakesThePageInvalid() {
        val body = """{"results":[{
            "id":"abc123","title":"Channel","author":{"name":"Author"},
            "broadcast_category":[{"name":"missing id"}]}],"next":null}"""
        val error = runCatching { RutubeParser.parseChannelPage(body) }.exceptionOrNull()
        assertTrue(error is RutubeApi.RutubeException)
    }

    @Test
    fun malformedDayAndMobileSchedulesFailInsteadOfLookingEmpty() = runTest {
        val api = RutubeApi(HttpClient(client { url ->
            if (url.contains("/pangolin/")) {
                response(200, """{"result":{"schedule":{"day":[{"title":"missing times"}]}}}""")
            } else {
                response(200, """{"schedule":[{"items":[{"title":"missing times"}]}]}""")
            }
        }))

        assertTrue(runCatching { api.scheduleDay("abc123", "2026-11-24") }.exceptionOrNull() is RutubeApi.RutubeException)
        assertTrue(runCatching { api.scheduleAllDays("abc123") }.exceptionOrNull() is RutubeApi.RutubeException)
    }

    @Test
    fun validEmptySchedulesRemainSuccessful() = runTest {
        val api = RutubeApi(HttpClient(client { url ->
            if (url.contains("/pangolin/")) {
                response(200, """{"result":{"schedule":{}}}""")
            } else {
                response(200, """{"schedule":[]}""")
            }
        }))

        assertEquals(emptyList<RutubeProgramme>(), api.scheduleDay("abc123", "2026-11-24"))
        assertEquals(emptyList<RutubeProgramme>(), api.scheduleAllDays("abc123"))
    }

    private fun client(respond: (String) -> Response) = OkHttpClient.Builder()
        .addInterceptor { chain -> respond(chain.request().url.toString()) }
        .build()

    private fun response(code: Int, body: String) = Response.Builder()
        .request(okhttp3.Request.Builder().url("https://rutube.ru/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}
