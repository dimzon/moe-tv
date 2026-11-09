package tv.own.myowntv.features.profiles
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
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*
import tv.own.owntv.player.ffmpeg.*

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the cold-start ordering between DataStore and Room. */
class ProfileLaunchGateTest {
    @Test
    fun `only one unlocked profile bypasses the launch gate`() {
        assertFalse(profileGateRequired(listOf(fakeProfile(1L))))
        assertTrue(profileGateRequired(listOf(fakeProfile(1L).copy(pinHash = "locked"))))
        assertTrue(profileGateRequired(listOf(fakeProfile(1L), fakeProfile(2L))))
    }

    @Test
    fun `single unlocked profile enters shell immediately without authentication`() {
        assertTrue(
            shellMayCompose(
                profileState = ProfileLoadState.Loaded(listOf(fakeProfile(42L))),
                activeProfileId = 42L,
                authenticatedProfileId = null,
                gateRequired = false,
            ),
        )
    }

    @Test
    fun `active locked id arriving before Room cannot compose the shell`() {
        assertFalse(
            shellMayCompose(
                profileState = ProfileLoadState.Loading,
                activeProfileId = 42L,
                authenticatedProfileId = null,
                gateRequired = true,
            ),
        )
        assertFalse(
            shellMayCompose(
                profileState = ProfileLoadState.Loaded(emptyList()),
                activeProfileId = 42L,
                authenticatedProfileId = null,
                gateRequired = true,
            ),
        )
        assertFalse(
            shellMayCompose(
                profileState = ProfileLoadState.Loaded(listOf(fakeProfile(42L))),
                activeProfileId = 42L,
                authenticatedProfileId = null,
                gateRequired = true,
            ),
        )
        assertTrue(
            shellMayCompose(
                profileState = ProfileLoadState.Loaded(listOf(fakeProfile(42L))),
                activeProfileId = 42L,
                authenticatedProfileId = 42L,
                gateRequired = true,
            ),
        )
    }

    @Test
    fun `loaded empty result cannot enter shell even when stale id is already active`() {
        assertFalse(shellMayCompose(ProfileLoadState.Loaded(emptyList()), 7L, authenticatedProfileId = 7L, gateRequired = false))
    }

    @Test
    fun `an active id absent from a loaded list cannot enter shell`() {
        assertFalse(
            shellMayCompose(
                ProfileLoadState.Loaded(listOf(fakeProfile(1L))),
                activeProfileId = 7L,
                authenticatedProfileId = 7L,
                gateRequired = false,
            ),
        )
    }

    @Test
    fun `authenticated profile A cannot authorize automatically selected locked profile B`() {
        val profiles = ProfileLoadState.Loaded(listOf(fakeProfile(2L).copy(pinHash = "locked")))
        val session = ProfileGateSessionViewModel()
        session.authenticateProfile(1L)

        // Deleting A clears the session before the repository selects B. Even if a caller observes
        // the new active id before that clear, the profile-bound policy still rejects stale A auth.
        session.invalidateAuthentication()
        assertFalse(shellMayCompose(profiles, 2L, session.authenticatedProfileId, gateRequired = true))
        assertFalse(shellMayCompose(profiles, 2L, authenticatedProfileId = 1L, gateRequired = true))
    }

    private fun fakeProfile(id: Long) = tv.own.owntv.core.database.entity.ProfileEntity(
        id = id,
        name = "Test",
        avatarColor = 0,
        avatarId = 0,
    )
}
