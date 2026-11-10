package tv.own.myowntv.features.customize
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
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R

/** The entries of the Customize screen's ⋯ menu, as (key, label) pairs. */
@Composable
internal fun customizeMoreOptions(
    hideNewCategories: Boolean,
    hasPin: Boolean,
): List<Pair<String, String>> = listOfNotNull(
    // Bulk rename over every listed category, as the items page has it (the long-press span
    // stays). In ⋯ rather than the tool row: two more pills pushed ⋯ itself off the screen.
    "BULK" to stringResource(R.string.settings_bulk_rename_title),
    "BULK_CLEANUP" to stringResource(R.string.settings_bulk_rename_auto_cleanup),
    "NEW" to stringResource(
        R.string.settings_customize_new_categories_button,
        stringResource(if (hideNewCategories) R.string.settings_customize_behavior_hide else R.string.settings_customize_behavior_show),
    ),
    if (!hasPin) "PIN_SET" to stringResource(R.string.settings_customize_set_pin)
    else "PIN_CHANGE" to stringResource(R.string.settings_customize_change_pin),
    if (hasPin) "PIN_REMOVE" to stringResource(R.string.settings_customize_remove_pin) else null,
    // Undo for hide / rename / move / reorder, for the playlist(s) this screen shows.
    CustomizeOption.RESTORE.name to stringResource(R.string.settings_customize_restore_defaults),
)

/** Key of the restore entry in the More menu (the others are the screen's existing string keys). */
internal enum class CustomizeOption { RESTORE }
