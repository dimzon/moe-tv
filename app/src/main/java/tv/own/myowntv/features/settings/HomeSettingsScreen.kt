package tv.own.myowntv.features.settings

import tv.own.owntv.player.titleRes
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.R
import tv.own.owntv.core.model.HomeRow
import tv.own.myowntv.features.home.displayLabel
import tv.own.myowntv.features.home.displayTitle
import tv.own.myowntv.features.home.settingsDescription
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText

/** The Home rails a live-only app can show: the hero and the two live channel rows. */
private val LIVE_HOME_ROWS = setOf(HomeRow.HERO, HomeRow.RECENT_CHANNELS, HomeRow.FAVORITE_CHANNELS)

/**
 * Settings › Layout › Home screen: the Home rows (shown or hidden,
 * in Home's order; the focused row's Hide / Move / Mode in the panel, Hold OK = Move), then Keep watching
 * and Android TV home. Trending and the Movies/Series rails went with VOD — the row list shows only the
 * live rails Home still draws.
 */
@Composable
fun HomeSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: HomeSettingsViewModel = koinViewModel()
    val settingsVm: SettingsViewModel = koinViewModel()
    val config by vm.config.collectAsStateWithLifecycle()
    val heroPreviewEnabled by vm.heroPreviewEnabled.collectAsStateWithLifecycle()
    val androidTvHomeEnabled by settingsVm.androidTvHomeEnabled.collectAsStateWithLifecycle()
    val homeRows = config.settingsRows.filter { it in LIVE_HOME_ROWS }

    val rowsFocus = remember { FocusRequester() }
    val actionsFocus = remember { FocusRequester() }
    val rowFocus = remember { HomeRow.entries.associateWith { FocusRequester() } }
    // The row being moved with ▲ ▼ (Hold OK, or Move in the panel); OK or Back ends it.
    var moving by remember { mutableStateOf<HomeRow?>(null) }
    // The row whose actions the panel shows while focus is in them.
    var acting by remember { mutableStateOf<HomeRow?>(null) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { rowsFocus.requestFocus() } }

    val ok = stringResource(R.string.common_ok)
    val back = stringResource(R.string.common_back)
    val layout = stringResource(R.string.settings_group_layout)
    val rowCount = homeRows.size
    StageFullPage(
        parents = listOf(layout),
        title = stringResource(R.string.settings_home_screen),
        count = pluralStringResource(R.plurals.settings_home_row_count, rowCount, rowCount),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
        handleBack = moving == null,
    ) {
        StageSettingsHeading(stringResource(R.string.settings_sections), homeRows.size, first = true)
        val hiddenNote = stringResource(R.string.settings_hidden_sections)
        homeRows.forEach { row ->
            key(row) {
                val hidden = row in config.hidden
                val liveMode = when (row) {
                    HomeRow.RECENT_CHANNELS -> config.recentLiveMode
                    HomeRow.FAVORITE_CHANNELS -> config.favoriteLiveMode
                    else -> null
                }
                val focus = rowFocus.getValue(row)
                val startMove: () -> Unit = { moving = row; acting = null; runCatching { focus.requestFocus() } }
                val actions = listOfNotNull(
                    StageAction(
                        if (hidden) MyOwnTVIcon.PLAY_CIRCLE else MyOwnTVIcon.EYE_OFF,
                        stringResource(if (hidden) R.string.settings_home_show_row else R.string.settings_home_hide_row),
                        { vm.setRowHidden(row, !hidden) },
                    ),
                    StageAction(MyOwnTVIcon.MOVE, stringResource(R.string.settings_home_move_row), startMove),
                    liveMode?.let { mode -> StageAction(MyOwnTVIcon.GRID, mode.displayLabel(), { vm.setLiveRowMode(row, mode.toggled()) }) },
                )
                val isMoving = moving == row
                val title = row.displayTitle()
                val hiddenWord = stringResource(R.string.settings_hidden)
                StageSettingRow(
                    icon = row.icon,
                    title = title,
                    desc = if (hidden) listOfNotNull(hiddenWord, liveMode?.displayLabel()).joinToString(dotSeparator())
                        else row.settingsDescription(),
                    value = SettingValue.Custom {
                        Text(
                            if (hidden) hiddenWord else stringResource(R.string.settings_home_shown),
                            style = stageText(18, 700),
                            color = if (hidden) StageColors.Dim else stageAccent.accent,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = { if (isMoving) moving = null else { acting = row; runCatching { actionsFocus.requestFocus() } } },
                    onLongClick = startMove,
                    marked = isMoving,
                    keepPanel = acting == row,
                    help = SettingHelp(
                        title = title,
                        text = row.settingsDescription() + "\n" + hiddenNote,
                        hints = if (isMoving) {
                            listOf("▲ ▼" to stringResource(R.string.content_move), ok to stringResource(R.string.settings_done))
                        } else {
                            listOf(
                                "▶" to stringResource(R.string.settings_key_actions),
                                stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_move),
                                back to layout,
                            )
                        },
                        extra = { if (!isMoving) StageActionColumn(actions, focus, actionsFocus) },
                    ),
                    modifier = Modifier
                        .focusRequester(focus)
                        .focusProperties { if (!isMoving) right = actionsFocus }
                        .onFocusChanged { if (it.isFocused) acting = null }
                        .onPreviewKeyEvent { e ->
                            if (!isMoving) return@onPreviewKeyEvent false
                            val up = when (e.key) {
                                Key.DirectionUp -> true
                                Key.DirectionDown -> false
                                else -> return@onPreviewKeyEvent e.key == Key.DirectionLeft || e.key == Key.DirectionRight
                            }
                            if (e.type == KeyEventType.KeyDown) vm.move(row, up)
                            true
                        },
                )
            }
        }

        StageSettingsHeading(stringResource(R.string.settings_keep_watching), 2)
        HomeSwitchRow(MyOwnTVIcon.LIVE_TV, R.string.settings_live_keep_watching, R.string.settings_live_keep_watching_description, config.heroIncludeLive) {
            vm.setHeroInclude(!config.heroIncludeLive)
        }
        HomeSwitchRow(MyOwnTVIcon.PLAY, R.string.settings_hero_preview, R.string.settings_hero_preview_description, heroPreviewEnabled) {
            vm.setHeroPreviewEnabled(!heroPreviewEnabled)
        }

        StageSettingsHeading(stringResource(R.string.settings_android_tv_home), 1)
        HomeSwitchRow(MyOwnTVIcon.HISTORY, R.string.settings_android_tv_home, R.string.settings_android_tv_home_description, androidTvHomeEnabled) {
            settingsVm.setAndroidTvHomeEnabled(!androidTvHomeEnabled)
        }
    }
    BackHandler(enabled = moving != null) { moving = null }
}

@Composable
private fun HomeSwitchRow(icon: MyOwnTVIcon, titleRes: Int, descRes: Int, on: Boolean, onClick: () -> Unit) {
    val title = stringResource(titleRes)
    val desc = stringResource(descRes)
    val value = SettingValue.Switch(on)
    StageSettingRow(
        icon = icon,
        title = title,
        desc = desc,
        value = value,
        onClick = onClick,
        help = settingHelp(null, title, desc, value, pinnable = false),
    )
}

private val HomeRow.icon: MyOwnTVIcon get() = when (this) {
    HomeRow.HERO -> MyOwnTVIcon.PLAY_CIRCLE
    HomeRow.RECENT_CHANNELS -> MyOwnTVIcon.LIVE_TV
    HomeRow.FAVORITE_CHANNELS -> MyOwnTVIcon.FAVORITE
}
