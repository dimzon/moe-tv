package tv.own.owntv.core.sync

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Pure parsers for RuTube's public API JSON — ported from the TVTube app's
 * [app.tvtube.data.parser.RutubeParser], adapted to [org.json] (core's existing JSON
 * library; Gson is test-only). Defensive: fields flip types and vanish between endpoints,
 * one odd value must never cost the whole channel list.
 */
object RutubeParser {

    fun parseCategories(body: String): List<RutubeCategory> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val obj = results.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optInt("id", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } ?: return@mapNotNull null
            RutubeCategory(id = id, name = obj.optString("name", ""), alias = obj.optString("alias", ""))
        }
    }

    fun parseChannelPage(body: String): RutubeChannelPage {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return RutubeChannelPage(emptyList(), null)
        val results = root.optJSONArray("results") ?: return RutubeChannelPage(emptyList(), null)
        val nextUrl = root.str("next")
        return RutubeChannelPage(
            results = (0 until results.length()).mapNotNull { i -> parseChannel(results.optJSONObject(i)) },
            nextUrl = nextUrl,
        )
    }

    fun parseBriefPage(body: String): RutubeBriefPage {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return RutubeBriefPage(emptyList(), emptyMap(), null)
        val results = root.optJSONArray("results") ?: return RutubeBriefPage(emptyList(), emptyMap(), null)
        val channels = ArrayList<RutubeChannel>()
        val schedules = HashMap<String, List<RutubeProgramme>>()
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val channel = parseChannel(obj) ?: continue
            channels += channel
            val sched = obj.optJSONArray("schedule") ?: continue
            val programmes = (0 until sched.length()).mapNotNull { j -> parseBriefProgramme(sched.optJSONObject(j)) }
            if (programmes.isNotEmpty()) schedules[channel.videoId] = programmes
        }
        return RutubeBriefPage(channels, schedules, root.str("next"))
    }

    fun parsePangolinDay(body: String): List<RutubeProgramme> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val schedule = root.optJSONObject("result")?.optJSONObject("schedule") ?: return emptyList()
        val out = ArrayList<RutubeProgramme>()
        val keys = schedule.keys()
        while (keys.hasNext()) {
            val day = schedule.optJSONArray(keys.next()) ?: continue
            for (i in 0 until day.length()) {
                parsePangolinProgramme(day.optJSONObject(i))?.let(out::add)
            }
        }
        return out
    }

    fun parseMobileSchedule(body: String): List<RutubeProgramme> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val days = root.optJSONArray("schedule") ?: return emptyList()
        val out = ArrayList<RutubeProgramme>()
        for (i in 0 until days.length()) {
            val items = days.optJSONObject(i)?.optJSONArray("items") ?: continue
            for (j in 0 until items.length()) {
                parseMobileProgramme(items.optJSONObject(j))?.let(out::add)
            }
        }
        return out
    }

    fun parsePlayOptions(httpCode: Int, body: String?): RutubePlayback {
        val root = body?.let { runCatching { JSONObject(it) }.getOrNull() }
        val detailObject = root?.optJSONObject("detail")
        val detail = detailObject?.str("name")
        // Two shapes mean "not playable without an account": `login_required` (single video) and
        // `common_subscription` (Premier/SPORT packages). The name carries the package title.
        val detailType = detailObject?.str("type")
        val iframeUrl = root?.str("iframe_url")
        val originType = root?.str("origin_type")

        // Sidecar subtitles (`captions[]`): SRT/VTT URLs the client side-loads under the video. Anything
        // else in the array is skip-worthy, and one odd entry must never cost the playlist or the
        // stream — same defensible-parsing contract as the rest of this file. A missing array means
        // "no subtitles advertised", not an error: the feed is anonymous and per-broadcast.
        val captions = ArrayList<RutubeCaption>()
        val capsArr = root?.optJSONArray("captions")
        if (capsArr != null) {
            for (i in 0 until capsArr.length()) {
                val o = capsArr.optJSONObject(i) ?: continue
                val format = o.str("format")?.trim()?.lowercase() ?: continue
                if (format != "srt" && format != "vtt") continue
                val file = o.str("file") ?: continue
                val subId = (o.opt("sub_id") as? Number)?.toLong()
                    ?: o.optString("sub_id", "").toLongOrNull()
                    ?: i.toLong()
                captions += RutubeCaption(
                    subId = subId,
                    format = format,
                    file = file,
                    langTitle = o.str("langTitle"),
                    isAutogenerated = o.optBoolean("is_autogenerated", false),
                )
            }
        }

        val liveArr = root?.optJSONObject("live_streams")?.optJSONArray("hls")
        if (liveArr != null) {
            for (i in 0 until liveArr.length()) {
                val u = liveArr.optJSONObject(i)?.str("url")
                if (u != null) return RutubePlayback.Ready(u, captions)
            }
        }
        val balancer = root?.optJSONObject("video_balancer")
        val vod = balancer?.str("m3u8_m4s")
            ?: balancer?.str("m3u8")
        if (vod != null) return RutubePlayback.Ready(vod, captions)

        val reason = when {
            detailType == "login_required" || detailType == "common_subscription" -> UnavailableReason.LOGIN_REQUIRED
            detail == "login_required" -> UnavailableReason.LOGIN_REQUIRED
            originType == "ifrm" || !iframeUrl.isNullOrBlank() -> UnavailableReason.IFRAME
            httpCode !in 200..299 -> UnavailableReason.NETWORK
            else -> UnavailableReason.NO_STREAM
        }
        return RutubePlayback.Unavailable(reason, detail)
    }


    /**
     * A JSON string field, with JSON `null` read as absent. org.json's `optString(name, null)`
     * returns the *string* "null" for a JSON null, which then looks like a usable URL
     * (`"next": null` on the last catalogue page) or a channel title.
     */
    private fun JSONObject.str(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name, null)?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    /**
     * Ask RuTube's CDN (`pic.rtbcdn.ru`) for a pre-scaled image instead of the original 1920px
     * picture — the same `?size=<tier>&picture_type=…` trick SmartRuTube's `RuTubeImageUrlHelper`
     * uses (s=193, m=480, l=640, xl=800, xxl=1280 px). `l` (640) is plenty for the largest logo tile
     * yet a fraction of the original's weight. Any non-CDN URL (a third-party feed, an iframe) is
     * returned unchanged; a URL that already carries a query gets `&…` appended, not `?…`.
     */
    private fun rutubeLogoUrl(url: String?): String? {
        if (url == null) return null
        val cdn = "pic.rtbcdn.ru/"
        if (!(url.startsWith("https://$cdn") || url.startsWith("http://$cdn"))) return url
        val sep = if (url.contains('?')) '&' else '?'
        return "$url${sep}size=l&picture_type=ios"
    }

    private fun parseChannel(obj: JSONObject?): RutubeChannel? {
        if (obj == null) return null
        // The 16-byte hex videoId is the channel's natural key — it becomes channels.remoteId,
        // epgChannelId and the channel_category link. Normalise case once here so every consumer
        // stores/compares the identical lowercase string under BINARY collation (a mixed-case id
        // would silently miss its own FK/join). RuTube already returns lowercase; this makes it a
        // guarantee, not luck.
        val videoId = obj.str("id")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val author = obj.optJSONObject("author")
        val name = author?.str("name")
            ?: obj.str("title")
            ?: return null
        val cats = obj.optJSONArray("broadcast_category")
        val categoryIds = ArrayList<Int>()
        if (cats != null) for (i in 0 until cats.length()) {
            cats.optJSONObject(i)?.let {
                val id = it.optInt("id", Int.MIN_VALUE)
                if (id != Int.MIN_VALUE) categoryIds += id
            }
        }
        // `is_paid` is false on every live row the API returns, but subscription channels carry
        // `common_subscription_product_codes` (PREMIER, SPORT, …) and their play/options answer
        // 404 `common_subscription`. Treat either signal as paid so the UI can badge them.
        val subscriptionCodes = obj.optJSONArray("common_subscription_product_codes")
        val hasSubscription = subscriptionCodes != null && subscriptionCodes.length() > 0
        return RutubeChannel(
            videoId = videoId,
            name = name,
            title = obj.str("title"),
            // Prefer the live programme's cover (thumbnail_url) over the channel's own avatar: for
            // film/series channels it is the on-air artwork (richer — what SmartRuTube shows), the
            // avatar is the fallback. The RuTube CDN resize param keeps it small and crisp instead of
            // the 1920px original.
            logoUrl = rutubeLogoUrl(obj.str("thumbnail_url") ?: author?.str("avatar_url")),
            categoryIds = categoryIds,
            orderNumber = obj.optInt("order_number", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            isPaid = obj.optBoolean("is_paid", false) || hasSubscription,
        )
    }

    private fun parseBriefProgramme(obj: JSONObject?): RutubeProgramme? {
        if (obj == null) return null
        val title = obj.str("title") ?: return null
        val start = obj.optString("start", null)?.let(::parseIsoMillis) ?: return null
        val stop = obj.optString("stop", null)?.let(::parseIsoMillis) ?: return null
        if (stop <= start) return null
        return RutubeProgramme(
            title = title,
            subtitle = obj.str("subtitle"),
            description = obj.str("description"),
            startUtcMillis = start,
            endUtcMillis = stop,
        )
    }

    private fun parsePangolinProgramme(obj: JSONObject?): RutubeProgramme? {
        if (obj == null) return null
        val title = obj.str("title") ?: return null
        val start = obj.optEpochSeconds("start") ?: return null
        val stop = obj.optEpochSeconds("stop") ?: return null
        if (stop <= start) return null
        val cats = obj.optJSONArray("ext_categories")
        val category = if (cats != null) {
            var found: String? = null
            for (i in 0 until cats.length()) {
                val n = cats.optJSONObject(i)?.str("name")
                if (n != null) { found = n; break }
            }
            found
        } else null
        return RutubeProgramme(
            title = title,
            subtitle = obj.str("subtitle"),
            description = obj.str("description"),
            category = category,
            startUtcMillis = start,
            endUtcMillis = stop,
            iconUrl = obj.str("preview_picture_url"),
        )
    }

    private fun parseMobileProgramme(obj: JSONObject?): RutubeProgramme? = parsePangolinProgramme(obj)

    internal fun parseIsoMillis(raw: String): Long? = runCatching {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    }.getOrNull() ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()

    private fun JSONObject.optEpochSeconds(key: String): Long? {
        if (!has(key)) return null
        // Value may be a String ("1700000000"), a number, or a float-ish string ("1700000000.0").
        val raw = when (val v = opt(key)) {
            is Number -> v.toLong() * 1000L
            else -> {
                val s = optString(key, null)?.trim()?.substringBefore('.') ?: return null
                val secs = s.toLongOrNull() ?: return null
                secs * 1000L
            }
        }
        return raw
    }
}
