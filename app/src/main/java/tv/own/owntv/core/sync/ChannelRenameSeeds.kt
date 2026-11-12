package tv.own.owntv.core.sync

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * The shipped rename dictionary: RuTube hex videoId → the display name the channel carries in
 * every list. Lives in `assets/channel_renames.json` (a plain `{ hex: name }` object) — data, not
 * code, so editing the dictionary never touches a Kotlin file. Mirrored into `channel_rename` by
 * [RuTubeSyncer] on every sync, so a dictionary change reaches already-stored rows without waiting
 * for their content to change.
 *
 * Deliberately EXPLICIT pairs, no pattern rules: a new channel the author names badly shows its
 * provider name until a pair is added (or the user renames it in Customize, which applies on top
 * of whatever lands here) — an explicit list cannot misfire on a name it was never meant to touch.
 *
 * To add an entry: read the channel's hex id (the natural key of `channels.remoteId` — a
 * `SELECT remoteId, name FROM channels` on any synced install), then add one line to the JSON.
 */
object ChannelRenameSeeds {
    private const val TAG = "ChannelRenameSeeds"
    const val ASSET = "channel_renames.json"

    /** Reads the asset once per process. A missing or malformed file means NO renames, never a crash. */
    fun load(context: Context): Map<String, String> =
        runCatching {
            parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
        }.getOrElse {
            Log.w(TAG, "$ASSET unreadable — shipping no renames", it)
            emptyMap()
        }

    /** `{ "hex": "Name", … }`. A malformed file means NO renames; blank names are skipped, not trusted. */
    fun parse(json: String): Map<String, String> = try {
        val obj = JSONObject(json)
        val out = LinkedHashMap<String, String>(obj.length())
        for (key in obj.keys()) {
            val name = obj.optString(key).trim()
            if (name.isNotEmpty()) out[key] = name
        }
        out
    } catch (e: org.json.JSONException) {
        Log.w(TAG, "rename dictionary is malformed — shipping no renames")
        emptyMap()
    }
}
