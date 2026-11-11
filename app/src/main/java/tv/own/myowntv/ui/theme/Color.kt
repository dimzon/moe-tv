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

import androidx.compose.ui.graphics.Color
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.OwnTVPalette

/**
 * Material 3 tonal palette for OwnTV (teal-seeded). NEUTRAL + secondary/tertiary roles are
 * theme-only; the `primary` roles are seeded per [AccentColor] (default teal == these values).
 *
 * Dark uses a near-black background (#040e0b) so the panel colours pop against
 * the deep dark surface while keeping a subtle green undertone.
 *
 * **The values themselves live in core**, in [OwnTVPalette], because the mobile app renders the
 * same product and a second copy of the hex codes would drift. This file only wraps them in
 * Compose's [Color], which core cannot do — it carries the Compose runtime, not `compose-ui`.
 */

// Brand mark color (the OwnTV play logo) — constant.
val AccentCyan = Color(OwnTVPalette.AccentCyan)

// ---------------- DARK (M3 dark over near-black #040e0b) ----------------
val DarkBackground = Color(OwnTVPalette.DarkBackground) // Option A — nav + inter-panel gap surface
val DarkSurface = Color(OwnTVPalette.DarkSurface)
val DarkSurfaceContainerLowest = Color(OwnTVPalette.DarkSurfaceContainerLowest)
val DarkSurfaceContainerLow = Color(OwnTVPalette.DarkSurfaceContainerLow)
val DarkSurfaceContainer = Color(OwnTVPalette.DarkSurfaceContainer)
val DarkSurfaceContainerHigh = Color(OwnTVPalette.DarkSurfaceContainerHigh)
val DarkSurfaceContainerHighest = Color(OwnTVPalette.DarkSurfaceContainerHighest)
val DarkOnSurface = Color(OwnTVPalette.DarkOnSurface)
val DarkOnSurfaceVariant = Color(OwnTVPalette.DarkOnSurfaceVariant)
val DarkOutline = Color(OwnTVPalette.DarkOutline)
val DarkOutlineVariant = Color(OwnTVPalette.DarkOutlineVariant)
val DarkSecondary = Color(OwnTVPalette.DarkSecondary)
val DarkOnSecondary = Color(OwnTVPalette.DarkOnSecondary)
val DarkSecondaryContainer = Color(OwnTVPalette.DarkSecondaryContainer)
val DarkOnSecondaryContainer = Color(OwnTVPalette.DarkOnSecondaryContainer)
val DarkTertiary = Color(OwnTVPalette.DarkTertiary)
val DarkOnTertiary = Color(OwnTVPalette.DarkOnTertiary)
val DarkTertiaryContainer = Color(OwnTVPalette.DarkTertiaryContainer)
val DarkOnTertiaryContainer = Color(OwnTVPalette.DarkOnTertiaryContainer)
val DarkError = Color(OwnTVPalette.DarkError)

// ---------------- LIGHT (M3 light) ----------------
val LightBackground = Color(OwnTVPalette.LightBackground)
val LightSurface = Color(OwnTVPalette.LightSurface)
val LightSurfaceContainerLowest = Color(OwnTVPalette.LightSurfaceContainerLowest)
val LightSurfaceContainerLow = Color(OwnTVPalette.LightSurfaceContainerLow)
val LightSurfaceContainer = Color(OwnTVPalette.LightSurfaceContainer)
val LightSurfaceContainerHigh = Color(OwnTVPalette.LightSurfaceContainerHigh)
val LightSurfaceContainerHighest = Color(OwnTVPalette.LightSurfaceContainerHighest)
val LightOnSurface = Color(OwnTVPalette.LightOnSurface)
val LightOnSurfaceVariant = Color(OwnTVPalette.LightOnSurfaceVariant)
val LightOutline = Color(OwnTVPalette.LightOutline)
val LightOutlineVariant = Color(OwnTVPalette.LightOutlineVariant)
val LightSecondary = Color(OwnTVPalette.LightSecondary)
val LightOnSecondary = Color(OwnTVPalette.LightOnSecondary)
val LightSecondaryContainer = Color(OwnTVPalette.LightSecondaryContainer)
val LightOnSecondaryContainer = Color(OwnTVPalette.LightOnSecondaryContainer)
val LightTertiary = Color(OwnTVPalette.LightTertiary)
val LightOnTertiary = Color(OwnTVPalette.LightOnTertiary)
val LightTertiaryContainer = Color(OwnTVPalette.LightTertiaryContainer)
val LightOnTertiaryContainer = Color(OwnTVPalette.LightOnTertiaryContainer)
val LightError = Color(OwnTVPalette.LightError)
