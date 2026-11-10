package tv.own.myowntv.player
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.core.settings.RemoteShortcutBinding
import tv.own.owntv.core.settings.RemoteShortcutBindings
import tv.own.owntv.core.settings.RemoteShortcutPress

class RemoteShortcutBindingsTest {
    @Test
    fun defaultsPreserveCurrentBrowsePaging() {
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_CHANNEL_UP, RemoteShortcutPress.SHORT, RemoteShortcutAction.PAGE_TOWARD_FIRST) in RemoteShortcutBindings.defaults)
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_CHANNEL_DOWN, RemoteShortcutPress.LONG, RemoteShortcutAction.JUMP_TO_LAST) in RemoteShortcutBindings.defaults)
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_MEDIA_REWIND, RemoteShortcutPress.LONG, RemoteShortcutAction.JUMP_TO_FIRST) in RemoteShortcutBindings.defaults)
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, RemoteShortcutPress.SHORT, RemoteShortcutAction.PAGE_TOWARD_LAST) in RemoteShortcutBindings.defaults)
    }

    @Test
    fun bindingsRoundTripAndIgnoreMalformedValues() {
        val expected = listOf(
            RemoteShortcutBinding(KeyEvent.KEYCODE_PROG_RED, RemoteShortcutPress.SHORT, RemoteShortcutAction.OPEN_GUIDE),
            RemoteShortcutBinding(KeyEvent.KEYCODE_INFO, RemoteShortcutPress.LONG, RemoteShortcutAction.TOGGLE_PLAYBACK_INFO),
        )

        assertEquals(expected.toSet(), RemoteShortcutBindings.decode(RemoteShortcutBindings.encode(expected)).toSet())
        assertTrue(RemoteShortcutBindings.decode(setOf("bad", "1|NOPE|OPEN_HOME")).isEmpty())
    }

    @Test
    fun replacementChangesOnlyMatchingButtonAndPress() {
        val replacement = RemoteShortcutBinding(
            KeyEvent.KEYCODE_CHANNEL_UP,
            RemoteShortcutPress.SHORT,
            RemoteShortcutAction.OPEN_GUIDE,
        )
        val result = RemoteShortcutBindings.replace(RemoteShortcutBindings.defaults, replacement)

        assertTrue(replacement in result)
        assertFalse(RemoteShortcutBinding(KeyEvent.KEYCODE_CHANNEL_UP, RemoteShortcutPress.SHORT, RemoteShortcutAction.PAGE_TOWARD_FIRST) in result)
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_CHANNEL_UP, RemoteShortcutPress.LONG, RemoteShortcutAction.JUMP_TO_FIRST) in result)
    }

    @Test
    fun essentialNavigationIsProtectedButNumberAndSpareKeysAreAssignable() {
        assertTrue(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_BACK))
        assertTrue(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_7))
        assertFalse(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_NUMPAD_7))
        assertFalse(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_PROG_BLUE))
        assertFalse(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
}
