package tv.own.owntv.core.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataIdentityTest {

    @Test
    fun `rid excludes a mutable display name from canonical identity`() {
        val identity = canonicalUserDataIdentity(
            JSONObject().put("rid", "abc123").put("name", "Old name").put("ctx", "custom:1").put("startMs", 42L),
        )

        val json = JSONObject(identity)
        assertEquals("abc123", json.getString("rid"))
        assertEquals("custom:1", json.getString("ctx"))
        assertEquals(42L, json.getLong("startMs"))
        assertFalse(json.has("name"))
        assertFalse(identity.contains("Old name"))
    }

    @Test
    fun `a channel rename cannot change a rid tombstone`() {
        assertEquals(
            canonicalUserDataIdentity(JSONObject().put("rid", "abc123").put("name", "Before")),
            canonicalUserDataIdentity(JSONObject().put("rid", "abc123").put("name", "After")),
        )
    }

    @Test
    fun `legacy records without rid still use their name`() {
        val identity = canonicalUserDataIdentity(JSONObject().put("name", "Legacy channel"))

        assertEquals("""{"name":"Legacy channel"}""", identity)
        assertTrue(identity.contains("Legacy channel"))
    }

    @Test
    fun `json null rid allows the legacy name fallback`() {
        assertEquals(
            canonicalUserDataIdentity(JSONObject().put("name", "Legacy channel")),
            canonicalUserDataIdentity(JSONObject().put("rid", JSONObject.NULL).put("name", "Legacy channel")),
        )
    }
}
