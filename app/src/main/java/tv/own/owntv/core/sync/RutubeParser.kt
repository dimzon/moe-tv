package tv.own.owntv.core.sync

import org.json.JSONObject

/**
 * Pure parsers for RuTube's public API JSON — ported from the TVTube app's
 * [app.tvtube.data.parser.RutubeParser], adapted to [org.json] (core's existing JSON
 * library; Gson is test-only). Malformed catalogue/schedule payloads fail loudly: treating a
 * truncated HTTP-200 response as "empty" would authorize destructive prune or erase a good guide.
 */
object RutubeParser {

    private fun malformed(message: String): Nothing =
        throw RutubeApi.RutubeException("Malformed RuTube payload: $message")

    fun parseCategories(body: String): List<RutubeCategory> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: malformed("categories root")
        val results = root.optJSONArray("results") ?: malformed("categories results")
        return (0 until results.length()).map { i ->
            val obj = results.optJSONObject(i) ?: malformed("category row $i")
            val id = obj.optInt("id", Int.MIN_VALUE).takeIf { it > 0 } ?: malformed("category id $i")
            RutubeCategory(id = id, name = obj.optString("name", ""), alias = obj.optString("alias", ""))
        }
    }

    /**
     * Rows of the official TV feed (`pangolin/api/tv/feeds/live_smarttv/`): every tab lists
     * `resources`, and each resource is a group ("Федеральные", "Спортивные", "Киноэфиры", …)
     * whose [RutubeLiveRow.url] returns the channel cards. Tabs and rows are sorted by
     * `order_number`; duplicate row names are collapsed because the same group appears in
     * several tabs — the first (canonical) occurrence wins.
     */
    fun parseLiveFeedRows(body: String): List<RutubeLiveRow> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: malformed("live feed root")
        val tabs = root.optJSONArray("tabs") ?: malformed("live feed tabs")
        val orderedTabs = (0 until tabs.length())
            .map { i -> tabs.optJSONObject(i) ?: malformed("live feed tab $i") }
            .sortedBy { it.optInt("order_number", 0) }
        val out = ArrayList<RutubeLiveRow>()
        val seen = HashSet<String>()

        for (tab in orderedTabs) {
            val resources = tab.optJSONArray("resources") ?: malformed("live feed resources")
            val orderedRows = (0 until resources.length())
                .map { i -> resources.optJSONObject(i) ?: malformed("live feed row $i") }
                .sortedBy { it.optInt("order_number", 0) }

            for (row in orderedRows) {
                val name = row.str("name")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: malformed("live feed row name")
                val url = row.str("url") ?: malformed("live feed row url")
                if (!seen.add(name)) continue
                out += RutubeLiveRow(name = name, url = url)
            }
        }
        return out
    }

    fun parseChannelPage(body: String): RutubeChannelPage {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return RutubeChannelPage(emptyList(), null, isValid = false)
        val results = root.optJSONArray("results")
            ?: return RutubeChannelPage(emptyList(), null, isValid = false)
        val parsed = ArrayList<RutubeChannel>(results.length())
        var isValid = true
        for (i in 0 until results.length()) {
            val channel = parseChannel(results.optJSONObject(i))
            if (channel == null) isValid = false else parsed += channel
        }
        return RutubeChannelPage(
            results = parsed,
            nextUrl = root.str("next"),
            isValid = isValid,
        )
    }

    fun parseBriefPage(body: String): RutubeBriefPage {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: malformed("brief root")
        val results = root.optJSONArray("results") ?: malformed("brief results")
        val channels = ArrayList<RutubeChannel>()
        val schedules = HashMap<String, List<RutubeProgramme>>()
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: malformed("brief row $i")
            val channel = parseChannel(obj) ?: malformed("brief channel $i")
            channels += channel
            val sched = obj.optJSONArray("schedule") ?: malformed("brief schedule $i")
            val programmes = (0 until sched.length()).map { j ->
                parseBriefProgramme(sched.optJSONObject(j)) ?: malformed("brief programme $i/$j")
            }
            if (programmes.isNotEmpty()) schedules[channel.videoId] = programmes
        }
        return RutubeBriefPage(channels, schedules, root.str("next"))
    }

    fun parsePangolinDay(body: String): List<RutubeProgramme> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: malformed("pangolin root")
        val schedule = root.optJSONObject("result")?.optJSONObject("schedule") ?: malformed("pangolin schedule")
        val out = ArrayList<RutubeProgramme>()
        val keys = schedule.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val day = schedule.optJSONArray(key) ?: malformed("pangolin day $key")
            for (i in 0 until day.length()) {
                parsePangolinProgramme(day.optJSONObject(i))?.let(out::add) ?: malformed("pangolin programme $key/$i")
            }
        }
        return out
    }

    fun parseMobileSchedule(body: String): List<RutubeProgramme> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: malformed("mobile schedule root")
        val days = root.optJSONArray("schedule") ?: malformed("mobile schedule days")
        val out = ArrayList<RutubeProgramme>()
        for (i in 0 until days.length()) {
            val items = days.optJSONObject(i)?.optJSONArray("items") ?: malformed("mobile schedule day $i")
            for (j in 0 until items.length()) {
                parseMobileProgramme(items.optJSONObject(j))?.let(out::add) ?: malformed("mobile programme $i/$j")
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
        val blockingRule = if (detailType == "blocking_rule") {
            val languages = ArrayList<RutubeBlockingRuleLanguage>()
            val rows = detailObject?.optJSONArray("languages")
            if (rows != null) {
                for (i in 0 until minOf(rows.length(), 8)) {
                    val row = rows.optJSONObject(i) ?: continue
                    val title = row.str("title")?.take(512)
                    val description = row.str("description")?.take(2_048)
                    if (title.isNullOrBlank() && description.isNullOrBlank()) continue
                    languages += RutubeBlockingRuleLanguage(
                        lang = row.str("lang")?.take(16),
                        title = title,
                        description = description,
                    )
                }
            }
            RutubeBlockingRule(
                imageUrl = detailObject?.str("image")?.take(2_048),
                languages = languages,
            )
        } else null

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
            detailType == "blocking_rule" -> UnavailableReason.BLOCKING_RULE
            detailType == "login_required" || detailType == "common_subscription" -> UnavailableReason.LOGIN_REQUIRED
            detail == "login_required" -> UnavailableReason.LOGIN_REQUIRED
            originType == "ifrm" || !iframeUrl.isNullOrBlank() -> UnavailableReason.IFRAME
            httpCode !in 200..299 -> UnavailableReason.NETWORK
            else -> UnavailableReason.NO_STREAM
        }
        return RutubePlayback.Unavailable(reason, detail, blockingRule)
    }


    /**
     * A JSON string field, with JSON `null` read as absent. org.json's `optString(name, null)`
     * returns the *string* "null" for a JSON null, which then looks like a usable URL
     * (`"next": null` on the last catalogue page) or a channel title.
     */
    private fun JSONObject.str(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name, null)?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
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
        val cats = if (obj.has("broadcast_category") && !obj.isNull("broadcast_category")) {
            obj.optJSONArray("broadcast_category") ?: malformed("channel broadcast_category")
        } else null
        val categoryIds = ArrayList<Int>()
        if (cats != null) for (i in 0 until cats.length()) {
            val category = cats.optJSONObject(i) ?: malformed("channel broadcast_category row $i")
            val id = category.optInt("id", Int.MIN_VALUE).takeIf { it > 0 }
                ?: malformed("channel broadcast_category id $i")
            categoryIds += id
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
            originType = obj.str("origin_type"),
            authorId = author?.optLong("id", Long.MIN_VALUE)?.takeIf { it != Long.MIN_VALUE },
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
            ageRating = obj.str("ageRating"),
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
            // Pangolin spells it `ageRating`; the mobile schedule spells it `age_rating`.
            ageRating = obj.str("ageRating") ?: obj.str("age_rating"),
            startUtcMillis = start,
            endUtcMillis = stop,
            iconUrl = obj.str("preview_picture_url"),
        )
    }

    private fun parseMobileProgramme(obj: JSONObject?): RutubeProgramme? = parsePangolinProgramme(obj)

    /**
     * ISO-8601 → epoch millis, hand-rolled so it runs on Android 7's `java.text` (`java.time` is
     * API 26+ and desugaring is deliberately off). The only real producer is the brief schedule
     * API (`tvbroadcastinfo`): `2026-11-14T16:55:00+03:00` — fixed positions, no fractional
     * seconds. Parsed defensively like the rest of this file: an optional fraction is accepted
     * and dropped, the zone must be `Z`/`z` or `±HH:MM` (a missing zone is rejected, as
     * `OffsetDateTime.parse` did), anything else returns null so one odd entry never costs the
     * schedule.
     */
    internal fun parseIsoMillis(raw: String): Long? {
        // "2026-11-14T16:55:00" is 19 chars; a zone suffix or ".fff…" extends it.
        if (raw.length < 19 || raw[4] != '-' || raw[7] != '-' || raw[10] != 'T' ||
            raw[13] != ':' || raw[16] != ':'
        ) return null
        val year = raw.substring(0, 4).toIntOrNull() ?: return null
        val month = raw.substring(5, 7).toIntOrNull() ?: return null
        val day = raw.substring(8, 10).toIntOrNull() ?: return null
        val hour = raw.substring(11, 13).toIntOrNull() ?: return null
        val minute = raw.substring(14, 16).toIntOrNull() ?: return null
        val second = raw.substring(17, 19).toIntOrNull() ?: return null
        // The optional fraction ".ddd…" sits between the seconds and the zone; skip over it.
        var i = 19
        if (i < raw.length && raw[i] == '.') {
            i = raw.indexOfAny(ZONE_CHARS, i + 1)
            if (i < 0) return null
        }
        val zone = raw.substring(i)
        val offsetMinutes = when {
            zone == "Z" || zone == "z" -> 0
            zone.length == 6 && (zone[0] == '+' || zone[0] == '-') && zone[3] == ':' -> {
                val zh = zone.substring(1, 3).toIntOrNull() ?: return null
                val zm = zone.substring(4, 6).toIntOrNull() ?: return null
                if (zh > 23 || zm > 59) return null
                val magnitude = zh * 60 + zm
                if (zone[0] == '+') magnitude else -magnitude
            }
            else -> return null
        }
        return runCatching {
            // Non-lenient UTC calendar per thread (see UtcDates.calendarPool): Feb 31 or month 13
            // must fail the way OffsetDateTime.parse did, not roll over.
            val cal = UtcDates.borrowCalendar()
            cal.set(year, month - 1, day, hour, minute, second)
            cal.timeInMillis - offsetMinutes * 60_000L
        }.getOrNull()
    }

    private val ZONE_CHARS = charArrayOf('+', '-', 'Z', 'z')

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
