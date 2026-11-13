package tv.own.owntv.core.customize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.ChannelMembership
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.isChannelVisible
import tv.own.owntv.core.model.MediaType

/** A locked category counts as hidden until the PIN opens it, stays on the rail, and needs a PIN to count at all. */
class CategoryLockRulesTest {

    private fun cat(id: Long, name: String) =
        CategoryEntity(id = id, name = name, remoteId = "r$id")

    private val cats = listOf(cat(1, "Music"), cat(2, "News"), cat(3, "Adult"))
    private fun keyOf(id: Long) = CustomizeKeys.category(cats.first { it.id == id })

    private val locked = SectionCustomizations(lockedCategories = setOf(keyOf(3)))

    @Test
    fun `a locked category nobody opened counts as hidden`() {
        val c = locked.withLocks(hasPin = true, unlocked = emptySet())
        assertEquals(setOf(keyOf(3)), c.hiddenCategories)
        assertEquals(setOf(keyOf(3)), c.sealedCategories)
    }

    @Test
    fun `an opened category is not hidden`() {
        assertSame(locked, locked.withLocks(hasPin = true, unlocked = setOf(keyOf(3))))
    }

    @Test
    fun `no PIN means no locks`() {
        val c = locked.withLocks(hasPin = false, unlocked = emptySet())
        assertTrue(c.lockedCategories.isEmpty())
        assertTrue(c.sealedCategories.isEmpty())
        assertTrue(c.hiddenCategories.isEmpty())
        assertFalse(cats.railCategories(c).any { it.locked })
    }

    @Test
    fun `a category the user also hid stays sealed and off the rail`() {
        val c = locked.copy(hiddenCategories = setOf(keyOf(3))).withLocks(hasPin = true, unlocked = emptySet())
        assertEquals(setOf(keyOf(3)), c.sealedCategories)
        assertEquals(setOf(keyOf(3)), c.hiddenCategories)
        assertTrue(cats.railCategories(c).none { it.key == keyOf(3) })
    }

    @Test
    fun `the rail keeps a sealed category, marked, and still drops real hides`() {
        val c = locked.copy(hiddenCategories = setOf(keyOf(2))).withLocks(hasPin = true, unlocked = emptySet())
        val rows = cats.railCategories(c)
        assertEquals(listOf("Music", "Adult"), rows.map { it.displayName })
        val adult = rows.single { it.key == keyOf(3) }
        assertTrue(adult.locked && adult.sealed)
        val opened = cats.railCategories(locked.withLocks(hasPin = true, unlocked = setOf(keyOf(3))))
        assertTrue(opened.single { it.key == keyOf(3) }.let { it.locked && !it.sealed })
    }

    @Test
    fun `a sealed custom category is sealed by key, a provider folder by id`() {
        val c = SectionCustomizations(sealedCategories = setOf("custom:x", keyOf(3)), sealedCategoryIds = setOf(3L))
        assertTrue(c.seals(LiveKey.Custom("custom:x")))
        assertTrue(c.seals(LiveKey.Folder(3)))
        assertFalse(c.seals(LiveKey.Folder(1)))
        assertFalse(c.seals(LiveKey.All))
        assertFalse(c.seals(null))
        assertTrue(c.sealsItemIn(3L))
        assertFalse(c.sealsItemIn(null))
    }

    @Test
    fun `a sealed channel is kept out of Favorites and History too, unlike a hidden one`() {
        val ch = ChannelEntity( name = "X", remoteId = "c1")
        ChannelMembership.snapshot = mapOf("c1" to setOf(3L))
        val hidden = SectionCustomizations(hiddenCategories = setOf(keyOf(3)))
        assertTrue(isChannelVisible(ch, hidden, setOf(3L), LiveKey.Favorites))
        val sealed = hidden.copy(sealedCategories = setOf(keyOf(3)), sealedCategoryIds = setOf(3L))
        assertFalse(isChannelVisible(ch, sealed, setOf(3L), LiveKey.Favorites))
        assertFalse(isChannelVisible(ch, sealed, setOf(3L), LiveKey.History))
        assertFalse(isChannelVisible(ch, sealed, setOf(3L)))
    }

    @Test
    fun `nothing in a sealed custom category shows while it is open on screen`() {
        val ch = ChannelEntity( name = "Y", remoteId = "c2")
        val c = SectionCustomizations(sealedCategories = setOf("custom:x"))
        assertFalse(isChannelVisible(ch, c, emptySet(), LiveKey.Custom("custom:x")))
        assertTrue(isChannelVisible(ch, c, emptySet(), LiveKey.All))
    }
}
