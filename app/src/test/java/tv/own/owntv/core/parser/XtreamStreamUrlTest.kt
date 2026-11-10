package tv.own.owntv.core.parser

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HttpClient

/**
 * Xtream stream URLs carry the credentials as path segments. A password the API login accepts must
 * give a URL that reaches the same account, and a plain one must give exactly the URL it always did.
 */
class XtreamStreamUrlTest {

    private val client = XtreamClient(HttpClient(OkHttpClient()))

    private fun source(user: String, pass: String) =
        SourceEntity(name = "t", type = SourceType.XTREAM, url = "http://panel.invalid:8080/", username = user, password = pass)

    @Test
    fun `plain credentials keep the URL they always had`() {
        val s = source("alice", "s3cret")
        assertEquals("http://panel.invalid:8080/live/alice/s3cret/7.ts", client.liveUrl(s, "7"))
        assertEquals("http://panel.invalid:8080/movie/alice/s3cret/7.mkv", client.movieUrl(s, "7", "mkv"))
        assertEquals("http://panel.invalid:8080/series/alice/s3cret/7.mp4", client.seriesEpisodeUrl(s, "7", null))
    }

    @Test
    fun `characters that would cut the URL short are encoded`() {
        val s = source("alice", "p#ss/w?rd%1")
        assertEquals("http://panel.invalid:8080/live/alice/p%23ss%2Fw%3Frd%251/7.ts", client.liveUrl(s, "7"))
    }

    @Test
    fun `spaces and non-ASCII are encoded, path-legal punctuation is not`() {
        assertEquals("a%20b", xtreamPathSegment("a b"))
        assertEquals("%C3%A9t%C3%A9", xtreamPathSegment("été"))
        assertEquals("-._~!$&'()*+,;=:@", xtreamPathSegment("-._~!$&'()*+,;=:@"))
        assertEquals("", xtreamPathSegment(null))
    }
}
