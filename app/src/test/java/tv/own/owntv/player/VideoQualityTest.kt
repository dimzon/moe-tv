package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.settings.QualityPick

/** The Settings limit, the mobile-data limit and the Quality-menu pick. */
class VideoQualityTest {

    @Test
    fun `the mobile-data limit counts only on a metered connection, and the lower limit wins`() {
        assertNull(VideoQuality.cap(maxHeight = 0, mobileDataMaxHeight = 720, metered = false))
        assertEquals(720, VideoQuality.cap(maxHeight = 0, mobileDataMaxHeight = 720, metered = true))
        assertEquals(480, VideoQuality.cap(maxHeight = 480, mobileDataMaxHeight = 720, metered = true))
        assertEquals(1080, VideoQuality.cap(maxHeight = 1080, mobileDataMaxHeight = 0, metered = true))
    }

    @Test
    fun `an engine's own limit can only lower the result`() {
        assertEquals(360, VideoQuality.lower(360, 1080))
        assertEquals(720, VideoQuality.lower(null, 720))
        assertNull(VideoQuality.lower(null, null))
    }

    @Test
    fun `the menu lists distinct heights, highest first, and only when there is a choice`() {
        assertEquals(listOf(1080, 720, 360), VideoQuality.heights(listOf(720, 1080, 360, 720, 0)))
        assertEquals(emptyList<Int>(), VideoQuality.heights(listOf(1080, 1080)))
    }

    @Test
    fun `a stored pick collapses to Auto unless it is Maximum or a height`() {
        assertEquals(QualityPick.AUTO, QualityPick.normalize(0))
        assertEquals(QualityPick.AUTO, QualityPick.normalize(-7))
        assertEquals(QualityPick.MAXIMUM, QualityPick.normalize(-1))
        assertEquals(1080, QualityPick.normalize(1080))
    }

    @Test
    fun `Maximum resolves to the stream's best playable height, and only when there is a choice`() {
        val tracks = Tracks(listOf(videoGroup(720, 1080, 480)))
        assertEquals(1080, VideoQuality.topHeightOf(tracks))
        assertNull(VideoQuality.topHeightOf(Tracks(listOf(videoGroup(720)))))
    }

    private fun videoGroup(vararg heights: Int): Tracks.Group {
        val formats = heights.map { Format.Builder().setId(it.toString()).setSampleMimeType(MimeTypes.VIDEO_H264).setHeight(it).build() }
        return Tracks.Group(
            TrackGroup(*formats.toTypedArray()), false,
            heights.map { C.FORMAT_HANDLED }.toIntArray(), BooleanArray(heights.size),
        )
    }
}
