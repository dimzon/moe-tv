package tv.own.owntv.core.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity

/**
 * Backup format v17: the fixes for the silent-data-loss classes an audit found in the
 * export/restore path. Every case here failed before that change.
 *
 *  1. **Per-item content keys were never remapped.** [tv.own.owntv.core.player.contentKey] is
 *     "<sourceId>:<TYPE>:<remoteId>", but the rows were imported verbatim — so after a merge restore
 *     they named a source id that either did not exist here or, worse, belonged to somebody else's
 *     playlist. (The per-item engine pins the same audit found are gone with the second engine, and
 *     the typed subtitle/TMDB keys with the VOD catalogue; old files lose those silently, by design.)
 *  2. **Source-keyed blocks were exported unscoped.** An id whose source is not in the file cannot
 *     be remapped on restore, and an unmapped numeric id happily matches whatever unrelated source
 *     holds that number on the target device.
 *  3. **New [SourceEntity] fields were quietly dropped.** `preferHls` and `livePrerollSecs` shipped
 *     without ever being added to the backup, and nothing failed. [sourceEntityFieldParity] is the
 *     tripwire: adding a field to the entity now breaks this test until the author decides.
 */
class BackupV17ScopingTest {

    private val idMap = mapOf(7L to 91L, 8L to 92L)

    // --- 1. per-item content keys ---

    @Test
    fun `per-item player key follows its source to the device id`() {
        assertEquals("91:LIVE:bbc-one", remapContentKey("7:LIVE:bbc-one", idMap))
        assertEquals("92:MOVIE:12345", remapContentKey("8:MOVIE:12345", idMap))
    }

    @Test
    fun `per-item player key for a source that took no part in the restore is dropped`() {
        // 5 is not in the map: the file's source was not merged, so there is no device id to point
        // at. Passing it through was not the safe failure it looked like — this device may have its
        // own, unrelated source 5, which would inherit its zoom or delay. Dropped instead.
        assertEquals(null, remapContentKey("5:LIVE:abc", idMap))
        // With no sources in the restore at all there is nothing to remap against: unchanged.
        assertEquals("7:LIVE:abc", remapContentKey("7:LIVE:abc", emptyMap()))
    }

    @Test
    fun `legacy stream-URL keys are not mistaken for ids`() {
        // "http" is not a Long, so the key must survive untouched — these are the rows made before
        // the stable key existed, and they still work by URL.
        val url = "http://panel.example:8080/live/user/pass/123.ts"
        assertEquals(url, remapContentKey(url, idMap))
    }

    // --- 2. export scoping ---

    @Test
    fun `per-item player keys are scoped to the sources riding in the backup`() {
        val keys = listOf("7:LIVE:a", "8:MOVIE:b", "99:LIVE:c")
        assertEquals(listOf("7:LIVE:a", "8:MOVIE:b"), filterContentKeys(keys, setOf(7L, 8L), false))
    }

    @Test
    fun `a URL-shaped key needs a passphrase because the URL carries the account credentials`() {
        val url = "http://panel.example:8080/live/user/secret/123.ts"
        val keys = listOf("7:LIVE:a", url)
        assertEquals(listOf("7:LIVE:a"), filterContentKeys(keys, setOf(7L), allowUrlKeys = false))
        assertEquals(keys, filterContentKeys(keys, setOf(7L), allowUrlKeys = true))
    }

    @Test
    fun `source-id-keyed maps drop entries whose source is absent`() {
        val map = JSONObject().put("7", "HOURS_6").put("99", "HOURS_24")
        val filtered = filterBySourceId(map, setOf(7L))
        assertEquals("HOURS_6", filtered.optString("7"))
        assertFalse(filtered.has("99"))
    }


    // --- 3. schema parity tripwire ---

    @Test
    fun sourceEntityFieldParity() {
        // Every user-controlled playlist setting must be in the backup. When this fails you have
        // added a field to SourceEntity: decide whether it belongs in sourceJson()/sourceFrom() and
        // in the existing-source merge branch of import(), then add it to the right list below.
        val backedUp = setOf(
            "id", "name", "type", "url", "username", "password", "mac",
            "stalkerSerialNumber", "stalkerDeviceId", "stalkerDeviceId2", "stalkerSignature",
            "userAgent", "epgUrl", "syncLive", "syncMovies", "syncSeries", "importPortalEpg",
            "preferHls", "livePrerollSecs", "hlsSupported", "createdAt", "lastSyncAt",
            "liveEnginePreference", "liveLatencyMode", "liveLatencyCustomSecs",
            // v22. `maxConnections` used to be excluded on the grounds that the Xtream sync
            // re-derives it — true for Xtream, and false for everything else. Since it can now also
            // be *measured*, by opening real streams for up to two minutes with playback stopped,
            // dropping it would make a restored playlist forget an expensive answer and never take
            // it again: the automatic measurement runs only on a playlist's first sync, and a
            // restored playlist has already had one. It describes the account, not the device, so it
            // is the same wherever it is restored. The merge prefers whatever this device measured
            // itself.
            "maxConnections", "maxConnectionsProbedAt",
            // v44: the per-playlist overrides. `vodEnginePreference` left this list with v56 — the
            // VOD engine choice is gone, and old files that carry the key are ignored on import.
            "catchupTimezone", "catchupOffsetMin", "liveTuneTimeoutSecs", "httpReferer",
            // v50: the playlist's "Clean channel names" choice and its custom words.
            "cleanChannelNames", "channelNameStrip",
            // v53: the playlist's "Ignore connection limit" choice.
            "ignoreConnectionLimit",
        )
        // v49 `serverTimeZone`: the Xtream panel's own report, re-read at EVERY sync (unlike a measured
        // `maxConnections`, nothing expensive is lost) and only ever set for Xtream. A restored row
        // reads null — catch-up's Automatic falls back to the device zone — until its next sync.
        // v54 `isPaid` (RuTube): the same kind of fact — provider-reported, re-read at every sync,
        // never a user choice — so it is not exported either.
        val deliberatelyExcluded = setOf("serverTimeZone", "isPaid")
        // Not a field of the entity at all: the Compose compiler adds `$stable` as a static, and
        // Kotlin does not flag it synthetic.
        val compilerGenerated = setOf("\$stable")

        // Java reflection, not kotlin-reflect: the latter is not on the unit-test classpath, and a
        // data class's backing fields carry the property names anyway.
        val actual = SourceEntity::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet() - compilerGenerated

        assertEquals(
            "SourceEntity fields not accounted for in the backup",
            emptySet<String>(),
            actual - backedUp - deliberatelyExcluded,
        )
        assertEquals(
            "backup lists name fields SourceEntity no longer has",
            emptySet<String>(),
            backedUp - actual,
        )
    }
}
