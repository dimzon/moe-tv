package tv.own.myowntv
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

// One launcher activity per icon colour, so each can carry its own icon, banner and launch-screen theme
// (see the manifest and core's AppIconSwitcher). No code of their own: MainActivity is the app.
class MainActivitySunflower : MainActivity()
class MainActivityCobalt : MainActivity()
class MainActivityTomato : MainActivity()
class MainActivityBoard : MainActivity()
class MainActivityPetrol : MainActivity()
class MainActivityOlive : MainActivity()
class MainActivityOliveCream : MainActivity()
class MainActivityPixel : MainActivity()

/** The launch theme with the still mark (values-v31 `….Still`), for Animations Off. */
internal fun stillLaunchTheme(icon: tv.own.owntv.core.brand.AppIcon): Int = when (icon) {
    tv.own.owntv.core.brand.AppIcon.PETROL -> R.style.Theme_MyOwnTV_Starting_Petrol_Still
    tv.own.owntv.core.brand.AppIcon.SUNFLOWER -> R.style.Theme_MyOwnTV_Starting_Sunflower_Still
    tv.own.owntv.core.brand.AppIcon.COBALT -> R.style.Theme_MyOwnTV_Starting_Cobalt_Still
    tv.own.owntv.core.brand.AppIcon.TOMATO -> R.style.Theme_MyOwnTV_Starting_Tomato_Still
    tv.own.owntv.core.brand.AppIcon.BOARD -> R.style.Theme_MyOwnTV_Starting_Board_Still
    tv.own.owntv.core.brand.AppIcon.EGGSHELL -> R.style.Theme_MyOwnTV_Starting_Eggshell_Still
    tv.own.owntv.core.brand.AppIcon.OLIVE -> R.style.Theme_MyOwnTV_Starting_Olive_Still
    tv.own.owntv.core.brand.AppIcon.OLIVE_CREAM -> R.style.Theme_MyOwnTV_Starting_OliveCream_Still
    tv.own.owntv.core.brand.AppIcon.PIXEL -> R.style.Theme_MyOwnTV_Starting_Pixel_Still
}
