package tv.own.owntv.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.network.HttpClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Client for RuTube's public API — ported from TVTube's
 * [app.tvtube.data.remote.RutubeApi], adapted to core's [HttpClient].
 *
 * Anonymous: the TV catalogue, the guide and play options are open. Two quirks:
 *  - play options is gated on a **mobile User-Agent** — a desktop UA gets 404 for every video;
 *  - requests must **not** go through a system proxy — the caller must provide an OkHttpClient
 *    built with `Proxy.NO_PROXY` (see [RutubeHttpClients] in DataModule).
 *
 * [http] must be a raw OkHttp-backed client for 404 handling: [HttpClient.get] throws
 * [HttpClient.HttpStatusException] on non-2xx, which is exactly the signal we need for
 * "no schedule upstream" (HTTP 404) versus a real failure.
 */
class RutubeApi(
    private val http: HttpClient,
    private val baseUrl: String = BASE_URL,
    private val userAgent: String = USER_AGENT,
) {
    class RutubeException(message: String, cause: Throwable? = null) : Exception(message, cause)

    suspend fun categories(): List<RutubeCategory> = withContext(Dispatchers.IO) {
        RutubeParser.parseCategories(getText("$baseUrl/api/livestream/categories/"))
    }

    suspend fun channels(): List<RutubeChannel> = withContext(Dispatchers.IO) {
        val out = ArrayList<RutubeChannel>()
        var url: String? = "$baseUrl/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=false"
        val visited = HashSet<String>()
        while (!url.isNullOrBlank() && visited.size < MAX_PAGES && visited.add(url)) {
            val page = RutubeParser.parseChannelPage(getText(url))
            out += page.results
            url = page.nextUrl
        }
        out
    }

    suspend fun briefSchedules(): Map<String, List<RutubeProgramme>> = withContext(Dispatchers.IO) {
        val out = HashMap<String, List<RutubeProgramme>>()
        var url: String? = "$baseUrl/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=true"
        val visited = HashSet<String>()
        while (!url.isNullOrBlank() && visited.size < MAX_PAGES && visited.add(url)) {
            val page = RutubeParser.parseBriefPage(getText(url))
            for ((k, v) in page.schedules) if (v.isNotEmpty()) out[k] = v
            url = page.nextUrl
        }
        out
    }

    /**
     * One channel's grid for one day (the bulk loader, ~40 KB per channel-day).
     * HTTP 404 means "no schedule upstream" and returns an empty list — never retry the
     * 2 MB mobile fallback for it.
     */
    suspend fun scheduleDay(videoId: String, date: LocalDate): List<RutubeProgramme> =
        withContext(Dispatchers.IO) {
            val day = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val url = "$baseUrl/pangolin/api/tv/tvprogram/$videoId?client=tv&programDate=$day"
            try {
                RutubeParser.parsePangolinDay(getText(url))
            } catch (e: HttpClient.HttpStatusException) {
                if (e.code == 404) emptyList() else throw e
            }
        }

    suspend fun scheduleAllDays(videoId: String): List<RutubeProgramme> = withContext(Dispatchers.IO) {
        RutubeParser.parseMobileSchedule(getText("$baseUrl/api/v1/livestream/video/schedule/$videoId/"))
    }

    suspend fun playUrl(videoId: String): RutubePlayback = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/play/options/$videoId/"
        try {
            val body = getText(url)
            RutubeParser.parsePlayOptions(200, body)
        } catch (e: HttpClient.HttpStatusException) {
            // Paid channels answer 404 with detail.name = login_required — present in the
            // body, which HttpClient discards. Re-parse best-effort: without the body we
            // cannot distinguish, so treat any play-options 404 as login_required (the
            // dominant cause) and let the UI show the paid message. Iframe/no-stream
            // channels answer 200 with no stream, handled above.
            if (e.code == 404) {
                RutubePlayback.Unavailable(UnavailableReason.LOGIN_REQUIRED, e.message)
            } else {
                RutubePlayback.Unavailable(UnavailableReason.NETWORK, e.message)
            }
        } catch (e: java.io.IOException) {
            RutubePlayback.Unavailable(UnavailableReason.NETWORK, e.message)
        }
    }

    private suspend fun getText(url: String): String = http.getText(
        url = url,
        userAgent = userAgent,
        headers = mapOf("Accept" to "application/json", "Referer" to "$baseUrl/"),
    )

    companion object {
        const val BASE_URL = "https://rutube.ru"
        const val USER_AGENT = "com.rutube.mobile/31.18.0 (Android 13)"
        private const val MAX_PAGES = 60
    }
}
