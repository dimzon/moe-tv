package tv.own.myowntv.features.setup
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

import tv.own.owntv.core.brand.AppIcon
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom

/**
 * The first-run display-size step: interface zoom and text size, before the rest of setup.
 *
 * Both values are the same stored settings the Settings screen edits, so a choice made here is the
 * app's from then on. Writes land in DataStore immediately — [MainActivity] scales `LocalDensity`
 * from these flows, which is what makes the step's sample text resize as the user presses.
 */
class DisplaySizeViewModel(private val settings: SettingsRepository) : ViewModel() {

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiZoom.DEFAULT)

    val fontSizePercent: StateFlow<Int> = settings.fontCustomization
        .map { it.sizePercent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiFontScale.DEFAULT)

    /**
     * The full [UiZoom.MIN]..[UiZoom.MAX] range, exactly as Settings offers it. Stepping below
     * [UiZoom.LOW_RAM_WARN] is gated by the same accept-the-risk warning — the screen owns
     * that prompt, this only writes what the user settled on.
     */
    fun setZoom(percent: Int) = viewModelScope.launch {
        settings.setUiZoomPercent(UiZoom.clamp(percent))
    }

    /** Font size only — the rest of the stored customization (families, popup sizes) is preserved. */
    fun setFontSize(percent: Int) = viewModelScope.launch {
        val current = settings.fontCustomization.first()
        settings.setFontCustomization(current.copy(sizePercent = UiFontScale.clamp(percent)))
    }

    /** The icon colour picked here reaches the launcher once the app is in the background. */
    val appIcon: StateFlow<AppIcon> = settings.appIcon
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppIcon.DEFAULT)

    fun setAppIcon(icon: AppIcon) = viewModelScope.launch { settings.setAppIcon(icon) }

    fun reset() = viewModelScope.launch {
        settings.setUiZoomPercent(UiZoom.DEFAULT)
        val current = settings.fontCustomization.first()
        settings.setFontCustomization(current.copy(sizePercent = UiFontScale.DEFAULT))
    }
}
