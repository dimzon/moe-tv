package tv.own.owntv.core.util

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Crash signatures that are already diagnosed and must not be mailed again. The entries live in
 * `src/main/resources/crash_blacklist.json`, so suppressing another known issue is a data edit.
 * JSON has no comments, so every entry carries a human-readable [CrashBlacklistEntry.reason].
 *
 * A missing or malformed resource means "suppress nothing": crash reporting must keep working even
 * when its optional blacklist cannot be read.
 */
internal data class CrashBlacklistEntry(
    val signature: String,
    val reason: String,
)

internal object CrashBlacklist {
    private const val TAG = "CrashBlacklist"
    const val RESOURCE = "crash_blacklist.json"

    private val entries: List<CrashBlacklistEntry> by lazy { load() }

    /** The reason this exact crash is suppressed, or null when it should still be uploaded. */
    fun reasonFor(report: String): String? = reasonFor(report, entries)

    internal fun reasonFor(report: String, entries: List<CrashBlacklistEntry>): String? {
        val signature = report.lineSequence()
            .firstOrNull { it.startsWith("signature ") }
            ?.substringAfter(' ')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return entries.firstOrNull { it.signature == signature }?.reason
    }

    internal fun load(): List<CrashBlacklistEntry> {
        val text = runCatching {
            CrashBlacklist::class.java.getResourceAsStream("/$RESOURCE")
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Log.w(TAG, "$RESOURCE unreadable — crash uploads are not suppressed")
            return emptyList()
        }
        return parse(text)
    }

    /** Blank or incomplete entries are ignored; one bad row does not disable the other rows. */
    internal fun parse(json: String): List<CrashBlacklistEntry> = runCatching {
        val array = JSONArray(json)
        val out = ArrayList<CrashBlacklistEntry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val signature = item.optString("signature").trim()
            val reason = item.optString("reason").trim()
            if (signature.isNotEmpty() && reason.isNotEmpty()) {
                out += CrashBlacklistEntry(signature, reason)
            }
        }
        out
    }.getOrElse {
        Log.w(TAG, "$RESOURCE malformed — crash uploads are not suppressed")
        emptyList()
    }
}
