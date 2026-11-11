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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.PopupFontScale
import tv.own.owntv.core.theme.PopupSizeScale
import tv.own.owntv.core.theme.ThemeMode

/**
 * Available OwnTV themes. Persisted via DataStore and selectable from Settings → Theme.
 * SYSTEM follows the platform dark/light setting.
 */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.DARK }

/**
 * Width of the focus ring the user picks in Settings, to make the selection prominent.
 * A CompositionLocal rather than a [Dimens] constant because every surface that draws it has to
 * follow the same choice.
 */
val LocalFocusBorderWidth = staticCompositionLocalOf { Dimens.FocusBorderWidth }

/** The offered ring widths in dp — thin, normal, thick, extra thick. 2 dp is the shipped default. */
val FocusBorderWidthChoices = listOf(1, 2, 4, 6)

/** Map the resolved OwnTV tokens onto a tv-material3 M3 [ColorScheme]. */
private fun schemeFrom(c: MyOwnTVColors): ColorScheme =
    if (c.isDark) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            border = c.outline,
            error = c.favorite,
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            border = c.outline,
            error = c.favorite,
        )
    }

@Composable
fun OwnTVTheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    systemInDarkTheme: Boolean,
    customAccent: String = "",
    focusHighlight: String = "",
    focusBorderWidthDp: Int = 2,
    animationLevel: AnimationLevel = AnimationLevel.FULL,
    mainFontFamily: AppFontFamily = AppFontFamily.PLUS_JAKARTA_SANS,
    popupFontFamily: AppFontFamily = AppFontFamily.PLUS_JAKARTA_SANS,
    popupFontSizePercent: Int = PopupFontScale.DEFAULT,
    popupSizePercent: Int = PopupSizeScale.DEFAULT,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

    val colors = ownTvColors(
        isDark = useDark,
        accent = accent,
        customAccent = customAccent,
        focusHighlight = focusHighlight,
    )

    CompositionLocalProvider(
        LocalMyOwnTVColors provides colors,
        LocalStagePalette provides if (useDark) StagePalette.Dark else StagePalette.Light,
        LocalThemeMode provides themeMode,
        LocalFocusBorderWidth provides focusBorderWidthDp.dp,
        LocalAnimationLevel provides animationLevel,
        LocalMainFontFamily provides mainFontFamily.asComposeFamily(),
        LocalPopupFontFamily provides popupFontFamily.asComposeFamily(),
        LocalPopupFontScaleFactor provides PopupFontScale.factor(popupFontSizePercent),
        LocalPopupSizeScaleFactor provides PopupSizeScale.factor(popupSizePercent),
    ) {
        MaterialTheme(
            colorScheme = schemeFrom(colors),
            typography = ownTVTypography(LocalMainFontFamily.current),
            content = content,
        )
    }
}

/** Convenience accessor: `OwnTVTheme.colors.focusBorder`. */
object OwnTVTheme {
    val colors: MyOwnTVColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMyOwnTVColors.current
}
