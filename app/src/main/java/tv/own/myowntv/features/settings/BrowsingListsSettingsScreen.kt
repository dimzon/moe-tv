package tv.own.myowntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.MyOwnTVIcon

/**
 * Settings › Layout › Browsing lists as a Stage page: what Live TV remembers — its last
 * category, then its last item — whether the TV Guide shares Live TV's remembered category,
 * and whether the All-channels built-in category shows.
 * The Movies/Series switches and the catch-up rail went with VOD and catch-up.
 */
@Composable
fun BrowsingListsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val catLive by vm.rememberCategoryLive.collectAsStateWithLifecycle()
    val guideFollows by vm.guideFollowsLiveCategory.collectAsStateWithLifecycle()
    val itemLive by vm.rememberLastLive.collectAsStateWithLifecycle()
    val builtIns by vm.liveBuiltIns.collectAsStateWithLifecycle()
    val showAll = builtIns.showAll
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_layout)),
        title = stringResource(R.string.settings_browsing_lists),
        count = pluralStringResource(R.plurals.settings_setting_count, 4, 4),
        onBack = onBack,
        modifier = modifier,
    ) {
        val live = stringResource(R.string.settings_history_live)
        val cat = stringResource(R.string.settings_browsing_last_category)
        val catHelp = stringResource(R.string.settings_browsing_last_category_description)
        StageSettingsHeading(cat, 2, first = true)
        BrowsingSwitch(MyOwnTVIcon.LIVE_TV, live, cat, catHelp, catLive, Modifier.focusRequester(first)) { vm.setRememberCategoryLive(!catLive) }
        BrowsingSwitch(
            MyOwnTVIcon.EPG,
            stringResource(R.string.settings_guide_follows_live),
            cat,
            stringResource(R.string.settings_help_guide_follows_live),
            guideFollows,
            desc = stringResource(R.string.settings_guide_follows_live_description),
        ) { vm.setGuideFollowsLiveCategory(!guideFollows) }
        val item = stringResource(R.string.settings_browsing_last_item)
        val itemHelp = stringResource(R.string.settings_browsing_last_item_description)
        StageSettingsHeading(item, 1)
        BrowsingSwitch(MyOwnTVIcon.LIVE_TV, live, item, itemHelp, itemLive) { vm.setRememberLastLive(!itemLive) }
        val liveCats = stringResource(R.string.settings_browsing_live_categories)
        val liveCatsHelp = stringResource(R.string.settings_browsing_live_categories_description)
        StageSettingsHeading(liveCats, 1)
        BrowsingSwitch(MyOwnTVIcon.LIVE_TV, stringResource(R.string.content_category_all_channels), liveCats, liveCatsHelp, showAll) { vm.setLiveShowAllRail(!showAll) }
    }
}

@Composable
private fun BrowsingSwitch(
    icon: MyOwnTVIcon,
    title: String,
    part: String,
    help: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    desc: String? = null,
    onClick: () -> Unit,
) {
    val value = SettingValue.Switch(on)
    StageSettingRow(
        icon = icon,
        title = title,
        desc = desc,
        value = value,
        onClick = onClick,
        // The panel names the part too: "Remember last category · Live TV".
        help = settingHelp(null, stringResource(R.string.settings_breadcrumb_eyebrow, part, title), help, value, pinnable = false),
        modifier = modifier,
    )
}
