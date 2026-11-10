package tv.own.myowntv.ui.theme
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
import tv.own.owntv.core.profile.*
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isUnspecified
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography

/**
 * Popup typography is separate from the main interface. Lora remains the default, while the user
 * may choose any bundled family without changing the popup host's established geometry scaling.
 */
private val LocalPopupTypographyApplied = compositionLocalOf { false }

/**
 * Wraps popup-menu content so every text style uses [LocalPopupFontFamily]. [fontScale] shrinks
 * (or grows) all font sizes and line heights — 1f keeps the design sizes; the EPG match/review
 * popups pass 0.75f for a denser look.
 */
@Composable
fun PopupFontTheme(fontScale: Float = 1f, content: @Composable () -> Unit) {
    // Popup bodies historically wrapped themselves even when their platform host already supplied
    // the popup theme. Keep nesting idempotent so the shared host's 30% scale is never multiplied by
    // an older per-dialog 0.75/0.50 scale and made unreadably small.
    if (LocalPopupTypographyApplied.current) {
        content()
        return
    }
    val baseDensity = LocalDensity.current
    val popupSizeScale = LocalPopupSizeScaleFactor.current
    // The user's popup font size rides on the density's fontScale, not on the typography slots, so it
    // reaches every sp inside the popup — hardcoded sizes and unstyled Text included. Multiplying the
    // typography alone left buttons and input fields untouched, which read as "the setting does nothing".
    val popupDensity = Density(
        density = baseDensity.density * popupSizeScale,
        fontScale = baseDensity.fontScale * LocalPopupFontScaleFactor.current /
            (popupSizeScale * LocalUiFontScaleFactor.current),
    )
    // Only the caller's fixed design scale (0.70 host / 0.75 dense menus / 0.50 forms) stays here.
    val effectiveFontScale = fontScale
    val t = MaterialTheme.typography
    // Stage popups use the app's own font, as the rest of the app does; a
    // separate popup font made them read as a different app. The popup font SIZE still applies.
    val popupFamily = LocalMainFontFamily.current
    fun androidx.compose.ui.text.TextStyle.popup() = copy(
        fontFamily = popupFamily,
        fontSize = if (effectiveFontScale == 1f) fontSize else fontSize * effectiveFontScale,
        lineHeight = if (effectiveFontScale == 1f || lineHeight.isUnspecified) lineHeight else lineHeight * effectiveFontScale,
    )
    CompositionLocalProvider(
        LocalPopupTypographyApplied provides true,
        LocalDensity provides popupDensity,
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            shapes = MaterialTheme.shapes,
            typography = Typography(
                displayLarge = t.displayLarge.popup(),
                displayMedium = t.displayMedium.popup(),
                displaySmall = t.displaySmall.popup(),
                headlineLarge = t.headlineLarge.popup(),
                headlineMedium = t.headlineMedium.popup(),
                headlineSmall = t.headlineSmall.popup(),
                titleLarge = t.titleLarge.popup(),
                titleMedium = t.titleMedium.popup(),
                titleSmall = t.titleSmall.popup(),
                bodyLarge = t.bodyLarge.popup(),
                bodyMedium = t.bodyMedium.popup(),
                bodySmall = t.bodySmall.popup(),
                labelLarge = t.labelLarge.popup(),
                labelMedium = t.labelMedium.popup(),
                labelSmall = t.labelSmall.popup(),
            ),
            content = content,
        )
    }
}
