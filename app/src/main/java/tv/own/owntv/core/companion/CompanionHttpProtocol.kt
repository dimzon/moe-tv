package tv.own.owntv.core.companion

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Pure HTTP protocol helpers used by [CompanionHttpServer]. Keeping parsing, PIN comparison, and
 * bounded-body handling independent of Android Context makes the security-sensitive paths testable
 * in the ordinary JVM unit-test source set without constructing a listener.
 */
internal object CompanionHttpProtocol {
    /** Backup JSON / base64 image uploads. Generous, but finite. */
    const val UPLOAD_BODY_LIMIT = 16 * 1024 * 1024

    /** PIN posts — a few hundred bytes in practice. */
    const val FORM_BODY_LIMIT = 64 * 1024

    /** Where a paired device posts its container during an automatic sync (see LocalAutoSyncServer). */
    const val AUTO_SYNC_IMPORT_PATH = "/sync/auto/import"


    private const val INITIAL_BODY_BUFFER = 64 * 1024
    private const val BODY_CHUNK = 16 * 1024

    /** Length-checked, constant-time PIN compare so timing cannot leak the PIN digit by digit. */
    fun pinEquals(submitted: String, expected: String): Boolean {
        if (submitted.length != expected.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (submitted[i].code xor expected[i].code)
        return diff == 0
    }

    /** Parse an `application/x-www-form-urlencoded` string into a key/value map. */
    fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val split = pair.split('=', limit = 2)
            val key = urlDecode(split[0])
            if (key.isNotBlank()) out[key] = urlDecode(split.getOrNull(1).orEmpty())
        }
        return out
    }

    /** Return the finite body cap for an endpoint. */
    fun maxBodyBytes(path: String): Int = when {
        path == "/backup" || path == "/background" -> UPLOAD_BODY_LIMIT
        // An automatic sync's container, raw: a whole library's favourites and history.
        path == AUTO_SYNC_IMPORT_PATH -> UPLOAD_BODY_LIMIT
        else -> FORM_BODY_LIMIT
    }

    /**
     * Read a request body without trusting Content-Length for allocation. A declared or observed
     * body over [limit] returns null so the caller can answer 413. A body that ends before its
     * Content-Length throws [java.io.EOFException]: half an uploaded backup used to be kept and then
     * failed to decrypt, which the TV could only report as a wrong password.
     */
    fun readBody(input: BufferedInputStream, headers: Map<String, String>, limit: Int): String? =
        readBodyBytes(input, headers, limit)?.toString(StandardCharsets.UTF_8)

    /** [readBody] for a binary body: the bytes as sent. */
    fun readBodyBytes(input: BufferedInputStream, headers: Map<String, String>, limit: Int): ByteArray? {
        val declared = headers["content-length"]?.toLongOrNull() ?: 0L
        if (declared > limit) return null
        if (declared == 0L) return ByteArray(0)
        val buffer = ByteArrayOutputStream(declared.coerceAtMost(INITIAL_BODY_BUFFER.toLong()).toInt())
        val chunk = ByteArray(BODY_CHUNK)
        var total = 0L
        while (total < declared) {
            val want = minOf(chunk.size.toLong(), declared - total).toInt()
            val read = input.read(chunk, 0, want)
            if (read <= 0) break
            total += read
            if (total > limit) return null
            buffer.write(chunk, 0, read)
        }
        if (total < declared) throw java.io.EOFException("Body ended at $total of $declared bytes")
        return buffer.toByteArray()
    }

    private fun urlDecode(value: String): String =
        runCatching { URLDecoder.decode(value.replace('+', ' '), StandardCharsets.UTF_8.name()) }
            .getOrDefault(value)
}
