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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Activity-scoped gate/session state for the profile gate and the in-session "Who's watching?"
 * transitions. Owns nothing persisted.
 *
 * **Why a ViewModel and not `rememberSaveable`** (see `docs/internationalization.md`,
 * "The profile gate: configuration-only retention, never saveable"):
 *
 * The authenticated profile id must survive an Activity recreation caused by a *configuration* change
 * (font scale, dark mode, a script-family language switch — all of which OwnTV now triggers far more
 * often than before i18n), so the user is not dumped back at the PIN gate mid-session. But it must
 * **not** survive system-initiated process death: `rememberSaveable` persists through
 * `onSaveInstanceState`, which is restored after a background kill, and a restored authentication id
 * would put a user straight past the profile/PIN gate when Android revives the task — precisely the
 * case the gate exists to cover.
 *
 * A ViewModel tied to the Activity's `ViewModelStore` has exactly that lifetime: it survives
 * configuration recreations through store retention, and is cleared when the process dies. There is
 * deliberately **no `SavedStateHandle`** and **no saved-state key** for the authentication flag. The
 * neighbouring in-session navigation flags (`addingProfile`, `switchProfileRequested`) share that
 * lifetime because they are transient UI navigation the user expects to survive a rotation but not a
 * kill; they were audited individually rather than batch-converted (see the table in the same
 * section of the plan). The active profile's existence and membership are observed from the Room
 * list and persisted active id in `MainActivity`, never remembered as launch permission.
 */
class ProfileGateSessionViewModel : ViewModel() {

    /** The profile authenticated in this Activity session; never a process-wide boolean. */
    var authenticatedProfileId by mutableStateOf<Long?>(null)
        private set

    /** True while the "add a profile" onboarding flow is open from the gate. Resets on process death. */
    var addingProfile by mutableStateOf(false)
        private set

    /**
     * Set by the sidebar avatar single-click so the "Who's watching?" gate opens even for a single
     * unpinned profile (otherwise switch-profile is a silent no-op with one profile). Resets on
     * process death.
     */
    var switchProfileRequested by mutableStateOf(false)
        private set

    /** Record successful authentication for exactly [profileId], then close any pending switch gate. */
    fun authenticateProfile(profileId: Long?) {
        authenticatedProfileId = profileId?.takeIf { it >= 0L }
        switchProfileRequested = false
    }

    /** Clear authentication before a profile is deleted or otherwise replaced. */
    fun invalidateAuthentication() {
        authenticatedProfileId = null
    }

    /**
     * Clear authentication only when the profile being deleted is the active profile. Deleting an
     * unrelated profile must not reopen the gate for the profile that remains on screen.
     */
    fun invalidateIfDeletingActiveProfile(deletedProfileId: Long, activeProfileId: Long?) {
        if (deletedProfileId == activeProfileId) invalidateAuthentication()
    }

    /**
     * Defensive binding for asynchronous active-profile changes. A stale authentication can never
     * authorize a newly selected profile, even during the brief DataStore/Room transition.
     */
    fun invalidateIfNotProfile(activeProfileId: Long?) {
        // null means the asynchronous active-id stream has not emitted yet. Do not turn a
        // configuration recreation's transient null into a needless PIN prompt.
        if (activeProfileId != null && authenticatedProfileId != null && authenticatedProfileId != activeProfileId) {
            authenticatedProfileId = null
        }
    }

    /** Open the add-profile onboarding from the gate. */
    fun startAddingProfile() {
        addingProfile = true
        switchProfileRequested = false
    }

    /** Add-profile onboarding completed: enter the shell as the newly active profile. */
    fun completeAddingProfile(profileId: Long?) {
        addingProfile = false
        authenticateProfile(profileId)
    }

    /** Add-profile onboarding cancelled: back to the gate. */
    fun cancelAddingProfile() {
        addingProfile = false
    }

    /** Back out of a user-requested switch: restore authentication for the unchanged active profile. */
    fun cancelSwitchProfileRequest(activeProfileId: Long?) {
        switchProfileRequested = false
        authenticateProfile(activeProfileId)
    }

    /**
     * Sidebar "switch profile": drop back to the gate and force it open even for a single unpinned
     * profile. Playback stop is the caller's responsibility (it owns the player engines); this only
     * owns session state.
     */
    fun requestSwitchProfile() {
        // A forced chooser is a new authentication boundary. If the user backs out, the caller
        // explicitly restores the unchanged active profile id via cancelSwitchProfileRequest().
        authenticatedProfileId = null
        switchProfileRequested = true
    }
}