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
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.myowntv.features.settings.FirstRunLanguageSelector
import tv.own.myowntv.features.settings.SettingValue
import tv.own.myowntv.features.settings.StageRadio
import tv.own.myowntv.features.settings.StageSettingRow
import tv.own.myowntv.features.settings.dataRefreshPolicyLabel
import tv.own.myowntv.features.settings.descriptionRes
import tv.own.myowntv.ui.components.BrandMark
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.format.localizedInteger
import tv.own.myowntv.ui.stage.stageBackground
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText

private enum class Step { WELCOME, DISPLAY_SIZE, REFRESH, DISCLAIMER }

/**
 * First-run onboarding: language, screen size, how often the catalogue and guide refresh, and the
 * disclaimer. Content is no longer imported here — the built-in RuTube source is a constant and the
 * application process fills missing data before this wizard finishes, whatever policy is chosen.
 * Sources are global — there are no profiles. [onDone] enters the app. Every step is a [WizardFrame].
 */
@Composable
fun Onboarding(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SetupViewModel = koinViewModel()
    val policy by vm.policy.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }

    val accent = stageAccent.accent
    Box(modifier = modifier.fillMaxSize().stageBackground(accent)) {
        when (step) {
            Step.WELCOME -> WelcomeStep(onNext = { step = Step.DISPLAY_SIZE })
            // Before the disclaimer, which is the first screen with a paragraph of real text on it:
            // if the interface is too small to read, that is the screen it first hurts on.
            Step.DISPLAY_SIZE -> DisplaySizeStep(onNext = { step = Step.REFRESH }, onBack = { step = Step.WELCOME })
            Step.REFRESH -> RefreshPolicyStep(
                selected = policy,
                onSelect = vm::setPolicy,
                onNext = { step = Step.DISCLAIMER },
                onBack = { step = Step.DISPLAY_SIZE },
            )
            Step.DISCLAIMER -> WizardFrame(
                step = WizardStep.READY,
                title = stringResource(R.string.setup_before_you_start),
                sub = stringResource(R.string.setup_disclaimer),
                back = WizardAction(stringResource(R.string.common_back), { step = Step.REFRESH }),
                next = WizardAction(stringResource(R.string.setup_i_understand), onDone, rememberFirstFocus()),
                onBack = onDone,
            )
        }
    }
}

/** A focus requester that takes focus once the step is on screen (the step's first control). */
@Composable
private fun rememberFirstFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { fr.requestFocus() } }
    return fr
}

/** The language as a row (OK opens the list), then Get started. */
@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    WizardFrame(
        step = WizardStep.LANGUAGE,
        title = stringResource(R.string.setup_welcome_title),
        sub = stringResource(R.string.setup_welcome_sub),
        back = null,
        next = WizardAction(stringResource(R.string.setup_get_started), onNext),
    ) {
        FirstRunLanguageSelector(Modifier.focusRequester(rememberFirstFocus()))
    }
}

/**
 * The one content decision left: how often the catalogue and the guide refresh themselves. All three
 * choices write the same policy into both per-source maps; the selected one shows a filled radio.
 */
@Composable
private fun RefreshPolicyStep(
    selected: DataRefreshPolicy,
    onSelect: (DataRefreshPolicy) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val first = rememberFirstFocus()
    val options = listOf(
        DataRefreshPolicy.EVERY_6_HOURS to MyOwnTVIcon.REFRESH,
        DataRefreshPolicy.STARTUP to MyOwnTVIcon.POWER,
        DataRefreshPolicy.ONLY_FIRST to MyOwnTVIcon.DOWNLOADS,
    )
    WizardFrame(
        step = WizardStep.PLAYLIST,
        title = stringResource(R.string.setup_refresh_title),
        sub = stringResource(R.string.setup_refresh_sub),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(stringResource(R.string.setup_continue), onNext),
    ) {
        options.forEachIndexed { index, (policy, icon) ->
            StageSettingRow(
                icon = icon,
                title = dataRefreshPolicyLabel(policy),
                desc = stringResource(policy.descriptionRes()),
                value = SettingValue.Custom { StageRadio(on = policy == selected) },
                onClick = { onSelect(policy) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

/**
 * First-run interface size: UI zoom and text size as steppers (◀ ▶), the app icon, and a
 * sample row to judge them by. Both steppers write straight through, so the whole step — rows and
 * sample alike — resizes as the user presses. No draft state and no Apply.
 */
@Composable
private fun DisplaySizeStep(onNext: () -> Unit, onBack: () -> Unit) {
    val vm: DisplaySizeViewModel = koinViewModel()
    val zoom by vm.uiZoomPercent.collectAsStateWithLifecycle()
    val fontSize by vm.fontSizePercent.collectAsStateWithLifecycle()
    val appIcon by vm.appIcon.collectAsStateWithLifecycle()
    val zoomFocus = rememberFirstFocus()
    // Zoom below LOW_RAM_WARN can OOM a 2 GB device, so the first step under the line is gated
    // exactly as the Settings dialog gates it. Accepting once arms the rest of this visit; arriving
    // already below the line (a restored backup) must not nag.
    var lowZoomAccepted by remember { mutableStateOf(zoom < UiZoom.LOW_RAM_WARN) }
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    var pickIcon by remember { mutableStateOf(false) }
    val stepZoom: (Int) -> Unit = { dir ->
        if (dir > 0) vm.setZoom(zoom + UiZoom.STEP)
        else {
            val next = UiZoom.clamp(zoom - UiZoom.STEP)
            if (next < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) pendingLowZoom = next else vm.setZoom(next)
        }
    }
    WizardFrame(
        step = WizardStep.DISPLAY,
        title = stringResource(R.string.setup_display_size_title),
        sub = stringResource(R.string.setup_display_sub),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(stringResource(R.string.setup_continue), onNext),
        extraLeft = listOf(WizardAction(stringResource(R.string.settings_reset), { vm.reset() })),
    ) {
        StageSettingRow(
            icon = MyOwnTVIcon.EXPAND,
            title = stringResource(R.string.settings_ui_zoom),
            desc = stringResource(R.string.setup_line_zoom),
            value = SettingValue.Stepper(stringResource(R.string.common_percent, zoom)),
            onClick = { stepZoom(1) },
            onStep = stepZoom,
            modifier = Modifier.focusRequester(zoomFocus),
        )
        StageSettingRow(
            icon = MyOwnTVIcon.PENCIL,
            title = stringResource(R.string.settings_font_size),
            desc = stringResource(R.string.setup_line_font),
            value = SettingValue.Stepper(stringResource(R.string.common_percent, fontSize)),
            onClick = { vm.setFontSize(fontSize + UiFontScale.STEP) },
            onStep = { dir -> vm.setFontSize(fontSize + dir * UiFontScale.STEP) },
        )
        // No restart prompt here: nothing is on the home screen yet, and the pick applies as soon as
        // the app is next in the background.
        val icons = AppIcon.entries
        val at = icons.indexOf(appIcon).coerceAtLeast(0)
        StageSettingRow(
            icon = MyOwnTVIcon.SPARKLE,
            title = stringResource(R.string.settings_app_icon),
            desc = stringResource(R.string.setup_line_app_icon),
            value = SettingValue.Custom { AppIconSwatches(icons, appIcon) },
            onClick = { pickIcon = true },
            onStep = { dir -> vm.setAppIcon(icons[(at + dir + icons.size) % icons.size]) },
        )
        // The sample: a row's own type at the size it will really be.
        Row(
            Modifier
                .padding(top = 14.mpx)
                .fillMaxWidth()
                .height(84.mpx)
                .background(StageColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(20.mpx))
                .padding(horizontal = 22.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_panel_width_preview).uppercase(), style = stageText(13, 800, androidx.compose.ui.unit.TextUnit(0.13f, androidx.compose.ui.unit.TextUnitType.Em)), color = StageColors.Dim)
            Text(stringResource(R.string.home_row_recent_channels), style = stageText(21, 700), color = StageColors.Text, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.common_on), style = stageText(18, 700), color = stageAccent.accent)
        }
    }

    // OK on App icon: the icon tiles in a popup (no restart question — nothing is on the home screen yet).
    if (pickIcon) {
        val tileFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { tileFocus.requestFocus() } }
        tv.own.myowntv.ui.stage.StagePopup(onDismiss = { pickIcon = false }, title = stringResource(R.string.settings_app_icon), eyebrow = null, width = 880.mpx) {
            tv.own.myowntv.ui.components.AppIconPicker(
                selected = appIcon,
                onPick = { vm.setAppIcon(it); pickIcon = false },
                firstFocus = tileFocus,
            )
        }
    }

    // Accept-the-risk gate for zoom below LOW_RAM_WARN, the same prompt Settings shows. One
    // button, focus locked in every D-pad direction — OK accepts and applies the pending step, Back
    // cancels and leaves the zoom where it was.
    pendingLowZoom?.let { target ->
        // The same low-memory question Settings asks, as a Stage question with focus on Cancel.
        tv.own.myowntv.ui.stage.StageConfirm(
            title = stringResource(R.string.settings_low_zoom_warning_title),
            body = stringResource(R.string.settings_low_zoom_warning, UiZoom.LOW_RAM_WARN, UiZoom.LOW_RAM_WARN),
            confirm = stringResource(R.string.settings_low_zoom_accept),
            onConfirm = {
                lowZoomAccepted = true
                pendingLowZoom = null
                vm.setZoom(target)
                runCatching { zoomFocus.requestFocus() }
            },
            onCancel = {
                pendingLowZoom = null
                runCatching { zoomFocus.requestFocus() }
            },
            focusCancel = true,
            eyebrow = null,
        )
    }
}

/** The app icon row's value: the first five icon colours, the chosen one ringed, then "+N". */
@Composable
private fun AppIconSwatches(icons: List<AppIcon>, chosen: AppIcon) {
    val a = stageAccent
    val shown = (icons.take(5) + chosen).distinct().take(5)
    Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { icon ->
            Box(
                Modifier
                    .size(34.mpx)
                    .then(if (icon == chosen) Modifier.border(2.mpx, a.accent, RoundedCornerShape(9.mpx)) else Modifier),
                contentAlignment = Alignment.Center,
            ) { BrandMark(icon, 30.mpx, followAccent = false) }
        }
        if (icons.size > shown.size) {
            Text("+" + localizedInteger(icons.size - shown.size, grouping = false), style = stageText(17, 700), color = StageColors.Muted)
        }
    }
}
