package tv.own.myowntv.features.live

import tv.own.owntv.player.play
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewOnOkTest {

    @Test
    fun `switch off always watches`() {
        assertEquals(OkAction.WATCH, okAction(twoStep = false, paneCanPlay = true, paneChannelId = null, okChannelId = "c1"))
        assertEquals(OkAction.WATCH, okAction(twoStep = false, paneCanPlay = true, paneChannelId = "c2", okChannelId = "c1"))
        assertEquals(OkAction.WATCH, okAction(twoStep = false, paneCanPlay = true, paneChannelId = "c1", okChannelId = "c1"))
    }

    @Test
    fun `no pane to play in always watches`() {
        assertEquals(OkAction.WATCH, okAction(twoStep = true, paneCanPlay = false, paneChannelId = null, okChannelId = "c1"))
        assertEquals(OkAction.WATCH, okAction(twoStep = true, paneCanPlay = false, paneChannelId = "c2", okChannelId = "c1"))
    }

    @Test
    fun `first OK previews, OK on the channel in the pane watches`() {
        assertEquals(OkAction.PREVIEW, okAction(twoStep = true, paneCanPlay = true, paneChannelId = null, okChannelId = "c1"))
        assertEquals(OkAction.PREVIEW, okAction(twoStep = true, paneCanPlay = true, paneChannelId = "c2", okChannelId = "c1"))
        assertEquals(OkAction.WATCH, okAction(twoStep = true, paneCanPlay = true, paneChannelId = "c1", okChannelId = "c1"))
    }
}
