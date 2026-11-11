package tv.own.myowntv.features.categorylock
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import tv.own.owntv.core.categorylock.CategoryLockClosed
import tv.own.owntv.core.categorylock.CategoryLockFlow
import tv.own.owntv.core.categorylock.CategoryLockUi
import tv.own.owntv.core.categorylock.PinAttemptLimiters
import tv.own.owntv.core.categorylock.PinDoor
import tv.own.owntv.core.categorylock.SealedCategoryAction
import tv.own.owntv.core.customize.CategoryUnlockSession
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/** Core's [CategoryLockFlow] for the Live TV screen, which shows its own prompts. */
class CategoryLockViewModel(
    settings: SettingsRepository,
    categoryDao: CategoryDao,
    customize: CustomizationStore,
    session: CategoryUnlockSession,
    limiters: PinAttemptLimiters,
) : ViewModel() {
    private val flow = CategoryLockFlow(
        viewModelScope, settings, categoryDao, customize, session, limiters.of(PinDoor.CATEGORY),
    )

    val ui: StateFlow<CategoryLockUi> = flow.ui
    val closed: SharedFlow<CategoryLockClosed> = flow.closed

    /** A PIN popup is up: the category sheet behind it must not close when it loses focus. */
    val prompting: Boolean get() = flow.prompting

    fun open(type: MediaType, key: LiveKey, then: SealedCategoryAction? = null) = flow.open(type, key, then)
    fun toggleLock(type: MediaType, key: LiveKey) = flow.toggleLock(type, key)
    fun submit(pin: String) = flow.submit(pin)
    fun dismiss() = flow.dismiss()
}

private const val TAG = "CategoryLock"
