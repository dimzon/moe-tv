package tv.own.myowntv.features.categorylock

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
