package tv.own.myowntv

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemDialogKeysTest {

    @Test
    fun `keys whose fallback handler closes system dialogs are swallowed`() {
        assertTrue(swallowsSystemDialogKey(KeyEvent.KEYCODE_CALL))
        assertTrue(swallowsSystemDialogKey(KeyEvent.KEYCODE_CAMERA))
        assertTrue(swallowsSystemDialogKey(KeyEvent.KEYCODE_SEARCH))
    }

    @Test
    fun `navigation, volume, media and digit keys are left to their handlers`() {
        assertFalse(swallowsSystemDialogKey(KeyEvent.KEYCODE_BACK))
        assertFalse(swallowsSystemDialogKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(swallowsSystemDialogKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(swallowsSystemDialogKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertFalse(swallowsSystemDialogKey(KeyEvent.KEYCODE_7))
    }
}
