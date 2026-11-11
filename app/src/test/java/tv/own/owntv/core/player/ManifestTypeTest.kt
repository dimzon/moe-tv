package tv.own.owntv.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The streaming container a playlist entry declares for itself.
 *
 * The bug this closes: a proxy publishes a DASH channel at `https://host/live/mpd/173` — no
 * extension — and declares `manifest_type=mpd` on its own `#KODIPROP` line. That declaration used to
 * be parsed and thrown away, so Media3 inferred "progressive" from the extensionless URL, was handed
 * an XML manifest after the redirect, and failed with `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` on
 * every tune. The declaration is what makes the first attempt correct.
 */
class ManifestTypeTest {

    // --- decode / encode ---

    @Test
    fun `the three kodi tokens decode`() {
        assertEquals(ManifestType.MPD, ManifestType.decode("mpd"))
        assertEquals(ManifestType.HLS, ManifestType.decode("hls"))
        assertEquals(ManifestType.ISM, ManifestType.decode("ism"))
    }

    @Test
    fun `decoding is case and whitespace insensitive`() {
        assertEquals(ManifestType.MPD, ManifestType.decode("MPD"))
        assertEquals(ManifestType.MPD, ManifestType.decode("  mpd  "))
    }

    /** Total, like `DrmConfig.decode`: a value we cannot interpret reads as "no declaration". */
    @Test
    fun `absent blank and unknown values decode to null`() {
        assertNull(ManifestType.decode(null))
        assertNull(ManifestType.decode(""))
        assertNull(ManifestType.decode("   "))
        assertNull(ManifestType.decode("smoothstreaming"))
    }

    @Test
    fun `encode round-trips through the stored form`() {
        ManifestType.entries.forEach { assertEquals(it, ManifestType.decode(ManifestType.encode(it))) }
        assertNull(ManifestType.encode(null))
    }

    // --- the KODIPROP wiring ---

    /** The reported playlist shape: extensionless DASH URL, declaration on its own line. */
    @Test
    fun `a declared manifest type is read from the property`() {
        assertEquals(
            ManifestType.MPD,
            ManifestType.fromKodiProps(
                mapOf(
                    "inputstream" to "inputstream.adaptive",
                    "manifest_type" to "mpd",
                    "license_type" to "com.widevine.alpha",
                ),
            ),
        )
    }

    /** Not a DRM concept: an unprotected entry declaring `mpd` gets the same routing. */
    @Test
    fun `an unprotected entry can declare a manifest type too`() {
        assertEquals(
            ManifestType.MPD,
            ManifestType.fromKodiProps(mapOf("manifest_type" to "mpd")),
        )
    }

    /** A declaration we do not know is dropped rather than stored, so the entry keeps exactly the
     *  behaviour it has today instead of being routed somewhere that cannot open it. */
    @Test
    fun `an unknown declaration is dropped`() {
        assertNull(ManifestType.fromKodiProps(mapOf("manifest_type" to "rtmp")))
        assertNull(ManifestType.fromKodiProps(emptyMap()))
    }
}
