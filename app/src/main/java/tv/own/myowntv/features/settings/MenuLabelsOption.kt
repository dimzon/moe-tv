package tv.own.myowntv.features.settings

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.myowntv.core.settings.SettingsRepository.NavSize
import tv.own.myowntv.core.settings.SettingsRepository.NavStyle
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.stage.StagePopupOption
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx

/**
 * The Navigation popup's Menu labels row. OK steps to the next choice and writes it as the Size and
 * Widen on focus that give it, so the rows below follow straight away.
 */
@Composable
internal fun MenuLabelsOption(
    style: NavStyle,
    size: NavSize,
    widen: NavSize?,
    onSize: (NavSize) -> Unit,
    onWiden: (NavSize?) -> Unit,
) {
    val labels = MenuLabels.of(style, size, widen)
    StagePopupOption(
        title = stringResource(R.string.settings_nav_menu_labels),
        subtitle = stringResource(R.string.settings_nav_menu_labels_desc),
        value = stringResource(labels.labelRes),
        onClick = {
            val rail = MenuLabels.apply(MenuLabels.next(style, labels), style, size, widen)
            // Widen first: Size is what moves the content, so it changes once, with Widen already set.
            onWiden(rail.widen)
            onSize(rail.size)
        },
        leading = { MyOwnTVIcon(MyOwnTVIcon.TEXT_SIZE, StageColors.Text, Modifier.size(21.mpx)) },
    )
}

/** "Labels: When focused": the Navigation row's short summary, after the rail style. */
@Composable
internal fun menuLabelsSummary(style: NavStyle, size: NavSize, widen: NavSize?): String =
    stringResource(R.string.settings_nav_menu_labels_summary, stringResource(MenuLabels.of(style, size, widen).labelRes))
