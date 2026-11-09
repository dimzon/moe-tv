package tv.own.myowntv.features.settings
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
import tv.own.owntv.player.ffmpeg.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.PopupFontScale
import tv.own.owntv.core.theme.UiFontScale
import tv.own.myowntv.ui.components.MyOwnTVIcon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Settings › Appearance › Fonts & text size as a Stage page (no popup for simple values).
 * Font (its list in the panel), Text size and Popup text size (◀ ▶ in place, OK opens the value in the
 * panel — popup text size with a real-size sample popup), then Reset. Each change applies at once.
 */
@Composable
fun FontSettingsScreen(
    current: FontCustomization,
    onSet: (FontCustomization) -> Unit,
    familyLabel: @Composable (AppFontFamily) -> String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<FontEdit?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val percent: @Composable (Int) -> String = { stringResource(R.string.common_percent, it) }
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_appearance)),
        // The row's own name for it ("Fonts & text size"), so the page is called what opened it.
        title = settingWords("fonts", stringResource(R.string.settings_font_customization), null).title,
        count = pluralStringResource(R.plurals.settings_setting_count, 4, 4),
        onBack = onBack,
        modifier = modifier,
    ) {
        val families = AppFontFamily.entries.map { it.name to familyLabel(it) }
        val fontTitle = stringResource(R.string.settings_main_interface_font)
        val fontValue = SettingValue.Choice(familyLabel(current.mainFamily))
        StageSettingRow(
            icon = MyOwnTVIcon.TEXT_SIZE,
            title = fontTitle,
            desc = stringResource(R.string.settings_font_preview),
            value = fontValue,
            onClick = { editing = FontEdit.FONT },
            help = settingHelp(null, fontTitle, stringResource(R.string.settings_choose_font), fontValue, families.map { it.second }, pinnable = false),
            modifier = Modifier.focusRequester(first),
        )
        val sizeTitle = stringResource(R.string.settings_font_size)
        val sizeValue = SettingValue.Stepper(percent(current.sizePercent))
        val stepSize = { d: Int -> onSet(current.copy(sizePercent = UiFontScale.clamp(current.sizePercent + d * UiFontScale.STEP))) }
        StageSettingRow(
            icon = MyOwnTVIcon.TEXT_SIZE,
            title = sizeTitle,
            desc = stringResource(R.string.settings_font_size_range, UiFontScale.MIN, UiFontScale.MAX),
            value = sizeValue,
            onClick = { editing = FontEdit.SIZE },
            onStep = stepSize,
            help = settingHelp(null, sizeTitle, stringResource(R.string.settings_font_size_range, UiFontScale.MIN, UiFontScale.MAX), sizeValue, pinnable = false),
        )
        val popupTitle = stringResource(R.string.settings_popup_font_size)
        val popupValue = SettingValue.Stepper(percent(current.popupFontSizePercent))
        val stepPopup = { d: Int -> onSet(current.copy(popupFontSizePercent = PopupFontScale.clamp(current.popupFontSizePercent + d * PopupFontScale.STEP))) }
        StageSettingRow(
            icon = MyOwnTVIcon.TEXT_SIZE,
            title = popupTitle,
            desc = stringResource(R.string.settings_popup_font_size_description),
            value = popupValue,
            onClick = { editing = FontEdit.POPUP },
            onStep = stepPopup,
            help = settingHelp(null, popupTitle, stringResource(R.string.settings_popup_font_size_description), popupValue, pinnable = false),
        )
        val reset = stringResource(R.string.common_reset)
        StageSettingRow(
            icon = MyOwnTVIcon.REFRESH,
            title = reset,
            desc = null,
            value = null,
            // Popup size has its own row in Appearance; Reset leaves it alone.
            onClick = { onSet(FontCustomization(popupSizePercent = current.popupSizePercent)) },
            help = SettingHelp(reset, stringResource(R.string.settings_font_customization_description), hints = settingHints(null, pinnable = false)),
        )

        when (editing) {
            FontEdit.FONT -> PickerDialog(
                title = fontTitle,
                options = families,
                selected = current.mainFamily.name,
                // One family for the interface and its popups: popups follow the main font.
                onSelect = { name -> AppFontFamily.valueOf(name).let { onSet(current.copy(mainFamily = it, popupFamily = it)) }; editing = null },
                onDismiss = { editing = null },
            )
            FontEdit.SIZE -> panelEditor({ editing = null }) {
                PanelStepper(percent(current.sizePercent), onStep = stepSize, onReset = { onSet(current.copy(sizePercent = UiFontScale.DEFAULT)) }, onDone = { editing = null })
            }
            FontEdit.POPUP -> panelEditor({ editing = null }) {
                PanelStepper(
                    percent(current.popupFontSizePercent), onStep = stepPopup,
                    onReset = { onSet(current.copy(popupFontSizePercent = PopupFontScale.DEFAULT)) }, onDone = { editing = null },
                    preview = { PopupSizeSample(current.popupSizePercent) },
                )
            }
            null -> Unit
        }
    }
}

private enum class FontEdit { FONT, SIZE, POPUP }
