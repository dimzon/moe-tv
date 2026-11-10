package tv.own.owntv.core.parser

import android.os.SystemClock
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.HttpClient
import java.io.InputStream
import java.net.URLEncoder

// --- Parsed Xtream models ---
data class XtCategory(val id: String, val name: String)
data class XtLiveStream(
    val streamId: String, val name: String, val icon: String?, val epgChannelId: String?,
    val categoryId: String?, val num: Int?,
    /** `tv_archive` = 1 → catch-up available; `tv_archive_duration` = days of archive kept. */
    val archive: Boolean = false, val archiveDays: Int = 0,
    /** The panel's own `direct_source`, when it publishes a non-blank one. A last-resort retry URL
     *  only — see [tv.own.owntv.core.database.entity.ChannelEntity.directSource]. */
    val directSource: String? = null,
)
/**
 * [value] as one URL path segment, for the Xtream stream paths (`…/live/{user}/{pass}/{id}.ts`).
 * Only what a segment cannot hold raw is percent-encoded — `/ ? # %`, spaces, other ASCII outside
 * RFC 3986's `pchar`, and non-ASCII bytes — so a credential made of letters, digits and the usual
 * punctuation keeps exactly the URL it always had, while one with a `#` or `/` no longer cuts the
 * URL short. The `player_api.php` calls already encode the same values, as query parameters.
 */
internal fun xtreamPathSegment(value: String?): String {
    val bytes = value.orEmpty().toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size)
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        if (c < 0x80 && c.toChar().let { it.isLetterOrDigit() || it in PATH_SEGMENT_SAFE }) {
            out.append(c.toChar())
        } else {
            out.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }
    return out.toString()
}

private const val PATH_SEGMENT_SAFE = "-._~!$&'()*+,;=:@"
private const val HEX = "0123456789ABCDEF"

/** What a real request for an `.m3u8` live URL got back — see [XtreamClient.probeHls]. */
sealed interface HlsProbe {
    /** A playlist came back. Proof the panel serves HLS, whatever it advertises. */
    data object Served : HlsProbe
    /** The account was out of connections, so the request never got as far as a format decision. */
    data class Busy(val code: Int) : HlsProbe
    /** The panel answered and the answer was not a playlist. */
    data class NotServed(val reason: HlsNotServedReason) : HlsProbe
    /** No usable answer — network failure, nothing to test with, or an unexpected status. */
    data class Inconclusive(val reason: HlsInconclusiveReason) : HlsProbe
}

sealed interface HlsNotServedReason {
    data object NotPlaylist : HlsNotServedReason
    data class NoEndpoint(val httpCode: Int) : HlsNotServedReason
}

sealed interface HlsInconclusiveReason {
    data class HttpError(val httpCode: Int) : HlsInconclusiveReason
    data class Unexpected(val rawMessage: String) : HlsInconclusiveReason
    data object NoAnswer : HlsInconclusiveReason
    data object NoLiveChannels : HlsInconclusiveReason
    data object DeadTestChannel : HlsInconclusiveReason
}

/** Declaration + real probe. [declared] is null when the panel couldn't be reached at all. */
data class HlsTest(val declared: Boolean?, val probe: HlsProbe)
/** A schedule row. [programmeId] is the stored programme's id, or 0 for a row only the provider gave. */
data class XtEpgEntry(val title: String, val description: String?, val startMs: Long, val stopMs: Long, val programmeId: Long = 0)

/**
 * Xtream Codes `player_api.php` client. Category lists are small and collected to a list; the large
 * stream lists are streamed object-by-object with [android.util.JsonReader] and pushed to a callback,
 * so a 340k-channel response never sits fully in memory.
 */
class XtreamClient(private val http: HttpClient) {

    // --- Categories ---
    suspend fun liveCategories(s: SourceEntity, onProgress: ((Long, Long?) -> Unit)? = null) = categories(s, "get_live_categories", onProgress)
    private suspend fun categories(s: SourceEntity, action: String, onProgress: ((Long, Long?) -> Unit)? = null): List<XtCategory> =
        http.get(api(s, action), s.userAgent, onProgress, maxAttempts = CATEGORY_MAX_ATTEMPTS) { readCategories(it) }

    internal suspend fun readCategories(input: InputStream): List<XtCategory> {
        val out = ArrayList<XtCategory>()
        streamObjects(input) { m ->
            val id = m["category_id"] ?: return@streamObjects
            out.add(XtCategory(id, m["category_name"] ?: id))
        }
        return out
    }

    // --- Streams (callback-streamed) ---
    // Each returns true if the full list parsed cleanly, false if the server truncated it mid-stream
    // the sync uses that to fall back to per-category fetching. [categoryId] filters the
    // request server-side (`&category_id=X`), keeping payloads small enough to dodge the truncation.
    suspend fun streamLive(
        s: SourceEntity,
        categoryId: String? = null,
        onItem: suspend (XtLiveStream) -> Unit,
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): Boolean =
        streamLive(
            s = s,
            categoryId = categoryId,
            transform = { streamId, name, icon, epgChannelId, itemCategoryId, num, archive, archiveDays, directSource ->
                XtLiveStream(
                    streamId, name, icon, epgChannelId, itemCategoryId, num, archive, archiveDays, directSource,
                )
            },
            onItem = onItem,
            onProgress = onProgress,
        )

    suspend fun <T : Any> streamLive(
        s: SourceEntity,
        categoryId: String? = null,
        transform: (
            streamId: String,
            name: String,
            icon: String?,
            epgChannelId: String?,
            categoryId: String?,
            num: Int?,
            archive: Boolean,
            archiveDays: Int,
            directSource: String?,
        ) -> T?,
        onItem: suspend (T) -> Unit,
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): Boolean {
        return http.get(api(s, "get_live_streams", categoryParam(categoryId)), s.userAgent, onProgress) { input ->
            streamItems("get_live_streams", input, { reader -> readLiveStreamAs(reader, transform) }, onItem)
        }
    }

    private fun JsonReader.nextStringOrNull(): String? = when (peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    /**
     * Reads an int from a number or a numeric string, tolerating null/other. The text is read and
     * parsed here rather than with `nextInt()`, which throws on `1.5` or an overflow and took the
     * whole show down with it; a whole `1.0` still reads as 1.
     */
    private fun JsonReader.nextIntOrNull(): Int? = when (peek()) {
        JsonToken.NUMBER, JsonToken.STRING -> nextString().trim().let { text ->
            text.toIntOrNull() ?: text.toDoubleOrNull()?.let { d -> d.toInt().takeIf { it.toDouble() == d } }
        }
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    /** Reads a long from a number or a numeric string, tolerating null/other — leniently, as [nextIntOrNull]. */
    private fun JsonReader.nextLongOrNull(): Long? = when (peek()) {
        JsonToken.NUMBER, JsonToken.STRING -> nextString().trim().let { text ->
            text.toLongOrNull() ?: text.toDoubleOrNull()?.let { d -> d.toLong().takeIf { it.toDouble() == d } }
        }
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    /** Reads a double from a number or a numeric string, tolerating null/other. */
    private fun JsonReader.nextDoubleOrNull(): Double? = when (peek()) {
        JsonToken.NUMBER -> nextDouble()
        JsonToken.STRING -> nextString().trim().toDoubleOrNull()
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    /**
     * Short EPG (now + a few upcoming programmes) for a single live channel via `get_short_epg`.
     * Titles/descriptions are base64-encoded; timestamps are unix seconds. Returns entries sorted by
     * start time (empty if the panel has no guide for this channel).
     */
    suspend fun getShortEpg(s: SourceEntity, streamId: String, limit: Int = 6): List<XtEpgEntry> {
        val out = ArrayList<XtEpgEntry>()
        http.get(api(s, "get_short_epg", "&stream_id=$streamId&limit=$limit"), s.userAgent) { input ->
            JsonReader(input.reader(Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return@use }
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "epg_listings" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        readEpgListings(reader, out)
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
        return out.sortedBy { it.startMs }
    }

    private fun readEpgListings(
        reader: JsonReader,
        out: MutableList<XtEpgEntry>,
        keepUntitled: Boolean = true,
    ) {
        reader.beginArray()
        while (reader.hasNext()) {
            var title = ""
            var desc: String? = null
            var startTs = 0L
            var stopTs = 0L
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (reader.peek() == JsonToken.NULL) { reader.nextNull(); continue }
                when (name) {
                    "title" -> title = decodeBase64(reader.nextString())
                    "description" -> desc = EpgText.clean(decodeBase64(reader.nextString()))
                    "start_timestamp" -> startTs = reader.nextString().toLongOrNull() ?: 0
                    "stop_timestamp" -> stopTs = reader.nextString().toLongOrNull() ?: 0
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (startTs > 0 && stopTs > startTs && (keepUntitled || title.isNotBlank())) {
                out.add(XtEpgEntry(title.ifBlank { "—" }, desc, startTs * 1000, stopTs * 1000))
            }
        }
        reader.endArray()
    }

    private fun decodeBase64(s: String): String =
        runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim() }.getOrDefault(s)

    // --- Stream URL builders ---
    // Live uses the raw MPEG-TS endpoint (.ts) — the universal Xtream live format. The .m3u8/HLS
    // wrapper isn't served by every panel (mpegts-only providers 404 on it, so channels won't load),
    // whereas every panel serves .ts.
    fun liveUrl(s: SourceEntity, streamId: String) = "${base(s)}/live/${credentials(s)}/$streamId.ts"

    /** Full XMLTV guide for the whole account (all channels) — the bulk EPG used by the guide grid. */
    data class XtAccountDetails(
        val expiryMs: Long? = null,
        val hlsSupported: Boolean = false,
        /** `user_info.max_connections` — simultaneous streams the account may open; 0 when the panel
         *  omits it or reports nonsense. `1` is the one that changes playback. */
        val maxConnections: Int = 0,
        /** `user_info.active_cons` — streams open right now; -1 when the panel omits it. */
        val activeConnections: Int = -1,
        /** `user_info.status` verbatim ("Active", "Expired", "Banned", …); null when omitted. Panels
         *  invent their own words here, so it is shown as-is rather than mapped to an enum. */
        val status: String? = null,
        /** `user_info.auth` — the panel says 0 when it rejected these credentials. */
        val authOk: Boolean = true,
        /** `user_info.is_trial` — "1" on a trial line. */
        val trial: Boolean = false,
        /** The panel's own time zone from `server_info` — see [xtreamServerTimeZone]; null when unknown. */
        val serverTimeZone: String? = null,
    )

    /**
     * Account details (expiry + HLS support) from the bare `player_api.php` call
     * (`user_info.exp_date` and `user_info.allowed_output_formats`).
     *
     * - [XtAccountDetails.expiryMs]: null when the panel reports none/unlimited or payload is malformed.
     * - [XtAccountDetails.hlsSupported]: true only if `"m3u8"` is present in `allowed_output_formats`;
     *   defaults to false when omitted, unlisted, or on network/parse failure.
     */
    suspend fun fetchAccountDetails(s: SourceEntity): XtAccountDetails? = try {
        fetchAccountStatus(s)
    } catch (e: Exception) {
        Log.w(TAG, "fetchAccountDetails failed for source ${s.id}", e)
        null
    }

    /**
     * The same call as [fetchAccountDetails], but **propagating** the failure instead of returning null.
     * "Test source" has to tell "the host never answered" apart from "the host said no", and a null
     * cannot carry that difference.
     */
    suspend fun fetchAccountStatus(s: SourceEntity): XtAccountDetails {
        val u = URLEncoder.encode(s.username.orEmpty(), "UTF-8")
        val p = URLEncoder.encode(s.password.orEmpty(), "UTF-8")
        return http.get("${base(s)}/player_api.php?username=$u&password=$p", s.userAgent) { input ->
            var expSec: Long? = null
            var hlsSupported = false
            var maxConnections = 0
            var activeConnections = -1
            var status: String? = null
            var authOk = true
            var trial = false
            var zoneName: String? = null
            var timeNow: String? = null
            var timestampNow: Long? = null
            JsonReader(java.io.InputStreamReader(input, Charsets.UTF_8)).use { r ->
                r.isLenient = true
                if (r.peek() != JsonToken.BEGIN_OBJECT) return@use
                r.beginObject()
                while (r.hasNext()) {
                    val section = r.nextName()
                    if (section == "server_info" && r.peek() == JsonToken.BEGIN_OBJECT) {
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "timezone" -> zoneName = if (r.peek() == JsonToken.STRING) r.nextString() else { r.skipValue(); null }
                                "time_now" -> timeNow = if (r.peek() == JsonToken.STRING) r.nextString() else { r.skipValue(); null }
                                "timestamp_now" -> timestampNow = when (r.peek()) {
                                    JsonToken.STRING -> r.nextString().trim().toLongOrNull()
                                    JsonToken.NUMBER -> r.nextLong()
                                    else -> { r.skipValue(); null }
                                }
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                    } else if (section == "user_info" && r.peek() == JsonToken.BEGIN_OBJECT) {
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "exp_date" -> {
                                    expSec = when (r.peek()) {
                                        JsonToken.STRING -> r.nextString().toLongOrNull()
                                        JsonToken.NUMBER -> r.nextLong()
                                        else -> { r.skipValue(); null }
                                    }
                                }
                                // Panels send this as a string ("1") about as often as a number.
                                "max_connections" -> {
                                    maxConnections = when (r.peek()) {
                                        JsonToken.STRING -> r.nextString().trim().toIntOrNull() ?: 0
                                        JsonToken.NUMBER -> r.nextInt()
                                        else -> { r.skipValue(); 0 }
                                    }.coerceAtLeast(0)
                                }
                                "active_cons" -> {
                                    activeConnections = when (r.peek()) {
                                        JsonToken.STRING -> r.nextString().trim().toIntOrNull() ?: -1
                                        JsonToken.NUMBER -> r.nextInt()
                                        else -> { r.skipValue(); -1 }
                                    }.coerceAtLeast(-1)
                                }
                                "status" -> {
                                    status = if (r.peek() == JsonToken.STRING) {
                                        r.nextString().trim().takeIf { it.isNotEmpty() }
                                    } else { r.skipValue(); null }
                                }
                                // Panels send auth/is_trial as a number or a string, interchangeably.
                                "auth" -> {
                                    authOk = when (r.peek()) {
                                        JsonToken.STRING -> r.nextString().trim() != "0"
                                        JsonToken.NUMBER -> r.nextInt() != 0
                                        else -> { r.skipValue(); true }
                                    }
                                }
                                "is_trial" -> {
                                    trial = when (r.peek()) {
                                        JsonToken.STRING -> r.nextString().trim() == "1"
                                        JsonToken.NUMBER -> r.nextInt() == 1
                                        else -> { r.skipValue(); false }
                                    }
                                }
                                "allowed_output_formats", "user_output_formats" -> {
                                    when (r.peek()) {
                                        JsonToken.BEGIN_ARRAY -> {
                                            r.beginArray()
                                            while (r.hasNext()) {
                                                val fmt = r.nextStringOrNull()
                                                if (fmt?.equals("m3u8", ignoreCase = true) == true) {
                                                    hlsSupported = true
                                                }
                                            }
                                            r.endArray()
                                        }
                                        JsonToken.STRING -> {
                                            val str = r.nextString()
                                            if (str.contains("m3u8", ignoreCase = true)) {
                                                hlsSupported = true
                                            }
                                        }
                                        else -> r.skipValue()
                                    }
                                }
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                    } else {
                        r.skipValue()
                    }
                }
                r.endObject()
            }
            XtAccountDetails(
                expiryMs = expSec?.takeIf { it > 0 }?.times(1000),
                hlsSupported = hlsSupported,
                maxConnections = maxConnections,
                activeConnections = activeConnections,
                status = status,
                authOk = authOk,
                trial = trial,
                serverTimeZone = xtreamServerTimeZone(zoneName, timeNow, timestampNow),
            )
        }
    }

    /**
     * Whether [s] answers as an Xtream account at all — `player_api.php` returned a `user_info` with a
     * status or an explicit `auth: 0`. A plain web server serving a `get.php` playlist parses to the
     * all-defaults [XtAccountDetails], so the defaults alone never count. Any failure is "no".
     */
    suspend fun isXtreamAccount(s: SourceEntity): Boolean = try {
        fetchAccountStatus(s).let { it.status != null || !it.authOk }
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (e: Exception) {
        Log.i(TAG, "isXtreamAccount: no (${e.message})")
        false
    }

    /** Convenience delegate returning only [XtAccountDetails.expiryMs]. */
    suspend fun accountExpiryMs(s: SourceEntity): Long? = fetchAccountDetails(s)?.expiryMs

    // --- "Test HLS support" (playlist form) ---

    /** Thrown from the first-item callback to abort a streamed list early. Never escapes [firstLiveStreamId]. */
    private class StopStreaming : RuntimeException(null, null, false, false)

    /**
     * The stream id of the FIRST live channel the panel lists. Used to build a probe URL for a playlist
     * that hasn't been synced yet — `get_live_streams` is parsed incrementally, so this aborts after one
     * object instead of downloading a 340k-channel response.
     *
     * Returns the id even if the abort exception is swallowed downstream (the value is captured before
     * the throw), so the early-exit trick can never cost a correct answer.
     */
    suspend fun firstLiveStreamId(s: SourceEntity): String? {
        var first: String? = null
        return try {
            streamLive(s, onItem = { item ->
                if (first == null) {
                    first = item.streamId
                    throw StopStreaming()
                }
            })
            first
        } catch (c: kotlin.coroutines.cancellation.CancellationException) {
            throw c
        } catch (e: Exception) {
            first ?: run {
                Log.w(TAG, "firstLiveStreamId failed for source ${s.id}", e)
                null
            }
        }
    }

    /**
     * Ask the panel for `…/live/user/pass/{id}.m3u8` and see what actually comes back. A panel's
     * `allowed_output_formats` is a *claim*; plenty serve HLS without listing it and a few list it
     * without serving it, so only this answers the question.
     *
     * Reads just the first few hundred bytes — an HLS playlist starts `#EXTM3U`, an error page doesn't —
     * and never plays anything, so it costs one short request.
     */
    suspend fun probeHls(s: SourceEntity, streamId: String): HlsProbe {
        val url = "${base(s)}/live/${credentials(s)}/$streamId.m3u8"
        return try {
            http.get(url, s.userAgent) { input ->
                val head = ByteArray(HLS_PROBE_BYTES)
                var read = 0
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (String(head, 0, read, Charsets.UTF_8).contains("#EXTM3U")) {
                    HlsProbe.Served
                } else {
                    HlsProbe.NotServed(HlsNotServedReason.NotPlaylist)
                }
            }
        } catch (c: kotlin.coroutines.cancellation.CancellationException) {
            throw c
        } catch (e: HttpClient.HttpStatusException) {
            when (e.code) {
                // Xtream's "too many connections". Every live URL returns this while the account is
                // maxed out, `.ts` included, so it says nothing about HLS either way — not a "no".
                458, 509 -> HlsProbe.Busy(e.code)
                404, 400 -> HlsProbe.NotServed(HlsNotServedReason.NoEndpoint(e.code))
                else -> HlsProbe.Inconclusive(HlsInconclusiveReason.HttpError(e.code))
            }
        } catch (e: Exception) {
            Log.w(TAG, "probeHls failed for source ${s.id}", e)
            HlsProbe.Inconclusive(
                HlsInconclusiveReason.Unexpected(
                    e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName,
                ),
            )
        }
    }

    /**
     * Control probe: does this channel play at all over plain MPEG-TS? An MPEG-TS packet starts with
     * the sync byte `0x47`, which an HTML error page never does — one byte settles it.
     *
     * Only used to interpret a *negative* HLS result. Panels routinely list a dead placeholder entry
     * ("### INFO ###") as their first channel, and without this a dead channel would be reported as
     * "your provider doesn't do HLS" when it means nothing of the sort.
     */
    private suspend fun probeTsPlayable(s: SourceEntity, streamId: String): Boolean = try {
        http.get(liveUrl(s, streamId), s.userAgent) { input -> input.read() == 0x47 }
    } catch (c: kotlin.coroutines.cancellation.CancellationException) {
        throw c
    } catch (e: Exception) {
        Log.w(TAG, "probeTsPlayable failed for source ${s.id}", e)
        false
    }

    /**
     * The full "Test HLS support" run: the panel's own declaration, plus a real request for an `.m3u8`
     * channel. [knownStreamId] skips the channel-list fetch when the playlist is already synced.
     */
    suspend fun testHlsSupport(s: SourceEntity, knownStreamId: String? = null): HlsTest {
        val details = fetchAccountDetails(s)
            ?: return HlsTest(declared = null, probe = HlsProbe.Inconclusive(HlsInconclusiveReason.NoAnswer))
        val streamId = knownStreamId?.takeIf { it.isNotBlank() } ?: firstLiveStreamId(s)
            ?: return HlsTest(details.hlsSupported, HlsProbe.Inconclusive(HlsInconclusiveReason.NoLiveChannels))
        val probe = probeHls(s, streamId)
        // A "no" is only trustworthy once the same channel is known to play over MPEG-TS. If it doesn't,
        // the test channel is the problem, not HLS — say so instead of recording a false verdict.
        if (probe is HlsProbe.NotServed && !probeTsPlayable(s, streamId)) {
            return HlsTest(
                details.hlsSupported,
                HlsProbe.Inconclusive(HlsInconclusiveReason.DeadTestChannel),
            )
        }
        return HlsTest(details.hlsSupported, probe)
    }

    fun xmltvUrl(s: SourceEntity): String {
        val u = URLEncoder.encode(s.username.orEmpty(), "UTF-8")
        val p = URLEncoder.encode(s.password.orEmpty(), "UTF-8")
        return "${base(s)}/xmltv.php?username=$u&password=$p"
    }

    // --- helpers ---
    private fun base(s: SourceEntity) = s.url.trimEnd('/')

    /** The `{user}/{pass}` pair of a stream path, each encoded as a path segment. */
    private fun credentials(s: SourceEntity) = "${xtreamPathSegment(s.username)}/${xtreamPathSegment(s.password)}"

    private fun api(s: SourceEntity, action: String, extra: String = ""): String {
        val u = URLEncoder.encode(s.username.orEmpty(), "UTF-8")
        val p = URLEncoder.encode(s.password.orEmpty(), "UTF-8")
        return "${base(s)}/player_api.php?username=$u&password=$p&action=$action$extra"
    }

    /** `&category_id=X` query suffix (server-side filter), or "" when fetching everything. */
    private fun categoryParam(categoryId: String?): String =
        categoryId?.takeIf { it.isNotBlank() }?.let { "&category_id=$it" } ?: ""

    /** Category lists are small, so keeping the map reader here keeps that path simple. */
    private suspend fun streamObjects(input: InputStream, onObject: suspend (Map<String, String?>) -> Unit): Boolean =
        streamItems("objects", input, ::readObject, onObject)

    private suspend fun <T> streamItems(
        label: String,
        input: InputStream,
        readItem: (JsonReader) -> T?,
        onItem: suspend (T) -> Unit,
    ): Boolean =
        // Per-item timing costs millions of elapsedRealtime() syscalls on a 170k+ catalog, so the
        // detailed metrics only run when the tag is debuggable (`setprop log.tag.XtreamClient DEBUG`).
        if (DEBUG) {
            streamArray(label, input) { reader, metrics ->
                val parseStart = SystemClock.elapsedRealtime()
                val item = readItem(reader)
                metrics.parseOrReadMs += SystemClock.elapsedRealtime() - parseStart
                if (item != null) {
                    val callbackStart = SystemClock.elapsedRealtime()
                    onItem(item)
                    metrics.callbackMs += SystemClock.elapsedRealtime() - callbackStart
                }
            }
        } else {
            streamArray(label, input) { reader, _ ->
                readItem(reader)?.let { onItem(it) }
            }
        }

    /**
     * Streams a top-level JSON array. Returns true if the array parsed to its end, false if the server
     * truncated it mid-stream. A failure before any item is read is fatal and rethrown.
     */
    private suspend fun streamArray(label: String, input: InputStream, readItem: suspend (JsonReader, StreamMetrics) -> Unit): Boolean {
        val ctx = currentCoroutineContext()
        val startedAt = SystemClock.elapsedRealtime()
        var lastLogAt = startedAt
        var lastParseOrReadMs = 0L
        var lastCallbackMs = 0L
        var count = 0
        val metrics = StreamMetrics()
        if (DEBUG) Log.d(TAG, "streamArray start label=$label")
        try {
            JsonReader(input.reader(Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                    // Some servers return {} or an error object instead of an array.
                    reader.skipValue()
                    Log.d(TAG, "streamArray non-array label=$label totalMs=${SystemClock.elapsedRealtime() - startedAt}")
                    return true
                }
                reader.beginArray()
                while (reader.hasNext()) {
                    ctx.ensureActive()
                    readItem(reader, metrics)
                    count++
                    if (DEBUG && count % STREAM_LOG_ITEM_STEP == 0) {
                        val now = SystemClock.elapsedRealtime()
                        val parseOrReadDelta = metrics.parseOrReadMs - lastParseOrReadMs
                        val callbackDelta = metrics.callbackMs - lastCallbackMs
                        Log.d(
                            TAG,
                            "streamArray parsed count=$count label=$label deltaMs=${now - lastLogAt} " +
                                "parseOrReadMs=$parseOrReadDelta callbackMs=$callbackDelta " +
                                "totalParseOrReadMs=${metrics.parseOrReadMs} totalCallbackMs=${metrics.callbackMs} " +
                                "totalMs=${now - startedAt}",
                        )
                        lastLogAt = now
                        lastParseOrReadMs = metrics.parseOrReadMs
                        lastCallbackMs = metrics.callbackMs
                    }
                }
                reader.endArray()
            }
            Log.i(TAG, "streamArray end count=$count label=$label totalMs=${SystemClock.elapsedRealtime() - startedAt}")
            return true
        } catch (c: kotlin.coroutines.cancellation.CancellationException) {
            throw c
        } catch (e: Exception) {
            // Truncated mid-stream (JsonReader reports "Unterminated string …"). Keep everything parsed
            // so far; only a failure before ANY item is read is fatal.
            if (count == 0) throw e
            Log.w(
                TAG,
                "Stream truncated after $count items label=$label — partial list kept " +
                    "parseOrReadMs=${metrics.parseOrReadMs} callbackMs=${metrics.callbackMs} " +
                    "totalMs=${SystemClock.elapsedRealtime() - startedAt}",
                e,
            )
            return false
        }
    }

    private class StreamMetrics {
        var parseOrReadMs = 0L
        var callbackMs = 0L
    }

    private companion object {
        const val TAG = "XtreamClient"
        const val STREAM_LOG_ITEM_STEP = 10_000
        /** Detailed per-item parse metrics — off in normal runs, enabled via `setprop log.tag.XtreamClient DEBUG`. */
        val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
        /** Category lists are tiny; a retry on a connect/DNS blip saves the whole sync phase. */
        const val CATEGORY_MAX_ATTEMPTS = 3
        /** Enough of an HLS response to see `#EXTM3U` (or that an HTML error page came instead). */
        const val HLS_PROBE_BYTES = 512
    }

    private fun <T : Any> readLiveStreamAs(
        reader: JsonReader,
        transform: (
            streamId: String,
            name: String,
            icon: String?,
            epgChannelId: String?,
            categoryId: String?,
            num: Int?,
            archive: Boolean,
            archiveDays: Int,
            directSource: String?,
        ) -> T?,
    ): T? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var streamId: String? = null
        var name = ""
        var icon: String? = null
        var epgChannelId: String? = null
        var categoryId: String? = null
        var num: Int? = null
        var archive = false
        var archiveDays = 0
        var directSource: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stream_id" -> streamId = reader.nextScalarStringOrNull()
                "name" -> name = reader.nextScalarStringOrNull().orEmpty()
                "stream_icon" -> icon = reader.nextScalarStringOrNull()
                "epg_channel_id" -> epgChannelId = reader.nextScalarStringOrNull()?.takeIf { it.isNotBlank() }
                "category_id" -> categoryId = reader.nextScalarStringOrNull()
                "num" -> num = reader.nextIntOrNull()
                "tv_archive" -> archive = (reader.nextIntOrNull() ?: 0) > 0
                "tv_archive_duration" -> archiveDays = reader.nextIntOrNull() ?: 0
                // The panel's own URL for this channel, when it publishes one. NEVER the URL we tune:
                // it is built from the streaming server's configured domain and falls back to its raw
                // IP, so a misconfigured panel or load balancer publishes an address that is only
                // reachable inside their network. Kept strictly as a last-resort retry (see
                // `ChannelEntity.directSource`), and blank — which is what most panels send — is null.
                "direct_source" -> directSource = reader.nextScalarStringOrNull()?.takeIf { it.isNotBlank() }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return streamId?.let {
            transform(it, name, icon, epgChannelId, categoryId, num, archive, archiveDays, directSource)
        }
    }

    private fun readObject(reader: JsonReader): Map<String, String?> {
        val map = HashMap<String, String?>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (reader.peek()) {
                JsonToken.NULL -> {
                    reader.nextNull()
                    map[name] = null
                }
                JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> reader.skipValue()
                // nextScalarStringOrNull, not nextString: a panel sending `"is_adult": false` in its
                // category list failed the whole import on "Expected a string but was BOOLEAN".
                else -> map[name] = reader.nextScalarStringOrNull()
            }
        }
        reader.endObject()
        return map
    }

    private fun JsonReader.nextScalarStringOrNull(): String? = when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            null
        }
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> {
            skipValue()
            null
        }
    }
}

/**
 * The time zone an Xtream panel runs on, as a `java.util.TimeZone` ID, stored per source at sync.
 * [name] is `server_info.timezone` and wins when it is a real zone ID. Otherwise the
 * offset between `server_info.time_now` (panel-local "yyyy-MM-dd HH:mm:ss") and `timestamp_now` (unix
 * seconds) gives a fixed `GMT±hh:mm`, rounded to the quarter hour every real zone sits on. Null when
 * neither is usable, so the caller falls back to the device's zone.
 */
internal fun xtreamServerTimeZone(name: String?, timeNow: String?, timestampNowSec: Long?): String? {
    val id = name?.trim()
    if (!id.isNullOrEmpty() && id in java.util.TimeZone.getAvailableIDs()) return id
    if (timeNow.isNullOrBlank() || timestampNowSec == null || timestampNowSec <= 0) return null
    val localAsUtc = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC"); isLenient = false }
            .parse(timeNow.trim())?.time
    }.getOrNull() ?: return null
    val quarterHours = Math.round((localAsUtc - timestampNowSec * 1000) / 900_000.0).toInt()
    if (quarterHours !in -12 * 4..14 * 4) return null
    val minutes = kotlin.math.abs(quarterHours) * 15
    val sign = if (quarterHours < 0) '-' else '+'
    return "GMT$sign${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
}
