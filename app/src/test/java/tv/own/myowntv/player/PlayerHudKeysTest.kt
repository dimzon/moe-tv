package tv.own.myowntv.player

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerHudKeysTest {

    @Test
    fun `volume keys fall through to system volume instead of revealing the controls`() {
        assertFalse(revealsPlayerHudOn(Key.VolumeUp))
        assertFalse(revealsPlayerHudOn(Key.VolumeDown))
        assertFalse(revealsPlayerHudOn(Key.VolumeMute))
    }

    @Test
    fun `back stays with the shell while other keys still reveal the controls`() {
        assertFalse(revealsPlayerHudOn(Key.Back))
        assertTrue(revealsPlayerHudOn(Key.Enter))
        assertTrue(revealsPlayerHudOn(Key.DirectionCenter))
        assertTrue(revealsPlayerHudOn(Key.MediaPlayPause))
        assertTrue(revealsPlayerHudOn(Key.ChannelUp))
    }
}
