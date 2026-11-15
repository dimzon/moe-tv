package tv.own.owntv.core.sync

import android.util.Log
import org.json.JSONObject

/**
 * Group names of the official TV feed (`pangolin/api/tv/feeds/live_smarttv/`) → genre aliases of
 * `api/livestream/categories/`. The pairs live in `src/main/resources/rutube_live_groups.json`, so
 * a new feed group is a data edit, not a Kotlin edit.
 *
 * [load] is called from [RutubeApi]'s companion initializer, i.e. parsed once per process. A
 * missing or malformed file means "no aliases": feed cards still become channels, they just carry
 * no genre — never a crash, and never a wiped catalogue.
 */
internal object RutubeLiveGroups {
    private const val TAG = "RutubeLiveGroups"
    const val RESOURCE = "rutube_live_groups.json"

    fun load(): Map<String, String> {
        val text = runCatching {
            RutubeLiveGroups::class.java.getResourceAsStream("/$RESOURCE")
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Log.w(TAG, "$RESOURCE unreadable — feed rows add no genres")
            return emptyMap()
        }
        return parse(text)
    }

    /** `{ "группа": "alias" }`; keys/values are trimmed and lowercased, blank pairs dropped. */
    fun parse(json: String): Map<String, String> = runCatching {
        val obj = JSONObject(json)
        val out = LinkedHashMap<String, String>(obj.length())
        for (key in obj.keys()) {
            val group = key.trim().lowercase()
            val alias = obj.optString(key).trim().lowercase()
            if (group.isNotEmpty() && alias.isNotEmpty()) out[group] = alias
        }
        out
    }.getOrElse {
        Log.w(TAG, "$RESOURCE malformed — feed rows add no genres")
        emptyMap()
    }
}
