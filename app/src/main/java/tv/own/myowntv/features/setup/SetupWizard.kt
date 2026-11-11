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

import android.icu.text.ListFormatter
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.myowntv.features.settings.EpgSyncDialog
import tv.own.myowntv.features.settings.FirstRunLanguageSelector
import tv.own.myowntv.features.settings.SettingValue
import tv.own.myowntv.features.settings.SetupLocalSyncScreen
import tv.own.myowntv.features.settings.StageSettingRow
import tv.own.myowntv.features.shell.PendingShellRequest
import tv.own.myowntv.ui.components.BrandMark
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.detailText
import tv.own.myowntv.ui.components.displayText
import tv.own.myowntv.ui.format.localizedInteger
import tv.own.myowntv.ui.components.primaryText
import tv.own.myowntv.ui.components.remainderText
import tv.own.myowntv.ui.components.summaryText
import tv.own.myowntv.ui.components.warningText
import tv.own.myowntv.ui.stage.stageBackground
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText

private enum class Step { WELCOME, DISPLAY_SIZE, DISCLAIMER, SETUP_CHOICE, SYNC_DEVICE, ADD_CONTENT, ADD_SOURCE_REMOTE, ADD_SOURCE, IMPORTING }

/**
 * First-run onboarding: language, screen size, then content (the built-in RuTube catalogue, a new
 * source, a device sync, or skip). Sources are global — there are no profiles. [onDone] enters the
 * app. Every step is a [WizardFrame]; adding a playlist reuses the Settings › Add a source pages.
 */
@Composable
fun Onboarding(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SetupViewModel = koinViewModel()
    val defaultIptvName = stringResource(R.string.setup_default_iptv)
    val defaultPlaylistName = stringResource(R.string.setup_name_default_playlist)
    val defaultPortalName = stringResource(R.string.setup_default_portal)
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    val importState by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val epgSync by vm.epgSync.collectAsStateWithLifecycle()
    // Where "Try Again" returns to when an import fails.
    var importOrigin by remember { mutableStateOf(Step.ADD_SOURCE) }
    // The playlist being imported, for "Importing <name>".
    var importName by remember { mutableStateOf<String?>(null) }

    val accent = stageAccent.accent
    Box(modifier = modifier.fillMaxSize().stageBackground(accent)) {
        when (step) {
            Step.WELCOME -> WelcomeStep(onNext = { step = Step.DISPLAY_SIZE })
            // Before the disclaimer, which is the first screen with a paragraph of real text on it:
            // if the interface is too small to read, that is the screen it first hurts on.
            Step.DISPLAY_SIZE -> DisplaySizeStep(onNext = { step = Step.DISCLAIMER }, onBack = { step = Step.WELCOME })
            Step.DISCLAIMER -> WizardFrame(
                step = WizardStep.DISPLAY,
                title = stringResource(R.string.setup_before_you_start),
                sub = stringResource(R.string.setup_disclaimer),
                back = WizardAction(stringResource(R.string.common_back), { step = Step.DISPLAY_SIZE }),
                next = WizardAction(stringResource(R.string.setup_i_understand), { step = Step.SETUP_CHOICE }, rememberFirstFocus()),
            )
            // First decision: pull down the built-in catalogue now, or bring everything back from
            // another television in the house.
            Step.SETUP_CHOICE -> {
                val first = rememberFirstFocus()
                WizardFrame(
                    step = WizardStep.PLAYLIST,
                    title = stringResource(R.string.setup_set_up_owntv),
                    sub = stringResource(R.string.setup_setup_choice_description),
                    back = WizardAction(stringResource(R.string.common_back), { step = Step.DISCLAIMER }),
                    next = null,
                ) {
                    WizardCards(
                        listOf(
                            WizardCard(MyOwnTVIcon.PERSON, stringResource(R.string.setup_new_profile), stringResource(R.string.setup_create_profile_add_sources)) {
                                importName = "RuTube"; vm.startRutube(); importOrigin = Step.SETUP_CHOICE; step = Step.IMPORTING
                            },
                            WizardCard(MyOwnTVIcon.PHONE, stringResource(R.string.setup_sync_device), stringResource(R.string.setup_sync_device_description)) { step = Step.SYNC_DEVICE },
                        ),
                        first,
                    )
                }
            }
            // A sync brings the other device's whole setup with it, so it finishes the wizard the
            // same way the built-in catalogue does: enter the app once the data has landed.
            Step.SYNC_DEVICE -> SetupLocalSyncScreen(
                onRestored = onDone,
                onBack = { step = Step.SETUP_CHOICE },
            )
            Step.ADD_CONTENT -> AddContentStep(
                onPhone = { step = Step.ADD_SOURCE_REMOTE },
                onType = { step = Step.ADD_SOURCE },
                onSkip = { vm.finish(onDone) },
            )
            Step.ADD_SOURCE_REMOTE -> RemoteSetupScreen(
                state = vm.remoteState.collectAsStateWithLifecycle().value,
                payloads = vm.remotePayloads,
                onStartListener = { port -> vm.startRemoteListener(port) },
                onStopListener = { vm.stopRemoteListener() },
                // A remote submission hands off to the pre-filled form (the user presses Start Import).
                onPayloadReceived = { step = Step.ADD_SOURCE },
                onBack = { vm.stopRemoteListener(); step = Step.ADD_CONTENT },
            )
            Step.ADD_SOURCE -> AddSourceScreen(
                onStartXtream = { name, server, user, pass, ua, ref, epg, refresh, live, _, preferHls ->
                    importName = name.ifBlank { defaultIptvName }
                    vm.startXtream(importName!!, server, user, pass, ua, epg, refresh, live, preferHls, httpReferer = ref)
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                onStartM3u = { name, url, ua, ref, epg, refresh, _ ->
                    importName = name.ifBlank { defaultPlaylistName }
                    vm.startM3u(importName!!, url, ua, epg, refresh, httpReferer = ref)
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                onStartStalker = { name, portalUrl, mac, serialNumber, deviceId, deviceId2, signature, ua, ref, refresh, _, live, login, password ->
                    importName = name.ifBlank { defaultPortalName }
                    vm.startStalker(
                        importName!!, portalUrl, mac, serialNumber, deviceId,
                        deviceId2, signature, ua, refresh, live, httpReferer = ref,
                        login = login, password = password,
                    )
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                // Submissions from the phone page land here pre-filled (type + fields).
                remotePayload = vm.remotePayload,
                onRemotePayloadConsumed = { vm.consumeRemotePayload() },
                onBack = { step = Step.ADD_CONTENT },
                showDefaultToggle = false, // first playlist in setup: nothing to be "default" over yet
            )
            Step.IMPORTING -> ImportProgressStep(
                state = importState,
                progress = progress,
                name = importName,
                onContinue = { vm.finish(onDone) }, // playlist + its EPG synced (auto)
                // "Add a TV guide": finish setup, then the shell opens Settings › EPG sources › Add.
                onAddGuide = { PendingShellRequest.addEpg = true; vm.finish(onDone) },
                onRetry = { vm.reset(); step = importOrigin },
                onCancel = { vm.cancelImport(); step = importOrigin },
                onBackground = { vm.continueInBackground(onDone) }, // enter the app; sync keeps running
            )
        }
        // Semi-auto EPG: after the first playlist imports, ask → sync (live count) → done (overlays "All set!").
        EpgSyncDialog(
            state = epgSync,
            onSync = vm::syncPendingEpg,
            onDismiss = vm::dismissPendingEpg,
            onBackground = { vm.syncEpgInBackground(onDone) }, // enter the app; guide keeps downloading
        )
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

/** Add a playlist: From your phone, or Type it here. */
@Composable
private fun AddContentStep(onPhone: () -> Unit, onType: () -> Unit, onSkip: () -> Unit) {
    val first = rememberFirstFocus()
    WizardFrame(
        step = WizardStep.PLAYLIST,
        title = stringResource(R.string.setup_add_playlist),
        sub = stringResource(R.string.setup_add_playlist_description),
        back = WizardAction(stringResource(R.string.setup_skip_for_now), onSkip),
        next = null,
        // Back on the remote does not skip: this step had no Back before, and skipping is a choice.
        onBack = null,
    ) {
        WizardCards(
            listOf(
                WizardCard(MyOwnTVIcon.PHONE, stringResource(R.string.setup_from_phone), stringResource(R.string.setup_phone_card_line), onPhone),
                WizardCard(MyOwnTVIcon.PENCIL, stringResource(R.string.settings_add_type_here), stringResource(R.string.settings_line_add_type_here), onType),
            ),
            first,
        )
    }
}

/**
 * "Importing <name>" with the bar and where the import is, Cancel and Run in background; then
 * "All set!" with what arrived and an Add a TV guide row; or the failure with Back and Try again.
 */
@Composable
private fun ImportProgressStep(
    state: SourceImporter.ImportState,
    progress: tv.own.owntv.core.sync.ImportStage?,
    name: String?,
    onContinue: () -> Unit,
    onAddGuide: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
) {
    when (state) {
        SourceImporter.ImportState.Running, SourceImporter.ImportState.Idle,
        is SourceImporter.ImportState.NeedPassword -> {
            val display = progress?.importProgressDisplay()
            WizardFrame(
                step = WizardStep.PLAYLIST,
                title = name?.let { stringResource(R.string.setup_importing_title, it) } ?: stringResource(R.string.setup_importing_catalog),
                sub = stringResource(R.string.setup_watching_during_import),
                back = WizardAction(stringResource(R.string.common_cancel), onCancel),
                // Enter the app right away; the import keeps running (VM is activity-scoped) and channels
                // appear as they land — no need to sit through a big playlist sync.
                next = WizardAction(stringResource(R.string.setup_run_in_background), onBackground, rememberFirstFocus()),
            ) {
                StageSettingRow(
                    icon = MyOwnTVIcon.DOWNLOADS,
                    title = display?.primaryText() ?: stringResource(R.string.setup_preparing_catalog),
                    desc = display?.detailText(),
                    value = null,
                    onClick = {},
                )
            }
        }
        is SourceImporter.ImportState.Success -> {
            val lines = listOfNotNull(
                state.counts?.summaryText(includeEpg = true),
                state.restoredItems?.let { pluralStringResource(R.plurals.setup_restored_items, it, it) },
                if (state.passwordsOmitted) stringResource(R.string.setup_passwords_omitted) else null,
                state.needPassword.takeIf { it.isNotEmpty() }?.let {
                    stringResource(R.string.setup_need_password, ListFormatter.getInstance(LocalConfiguration.current.locales[0]).format(it))
                },
                state.skippedSources.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.setup_skipped_sources, it, it) },
                if (state.invalidLocale) stringResource(R.string.setup_invalid_locale) else null,
                state.warnings.warningText(),
                state.remainder.remainderText(),
            )
            WizardFrame(
                step = WizardStep.READY,
                title = stringResource(R.string.setup_all_set),
                sub = lines.joinToString("\n"),
                back = null,
                next = WizardAction(stringResource(R.string.setup_continue), onContinue, rememberFirstFocus()),
                onBack = onContinue,
            ) {
                StageSettingRow(
                    icon = MyOwnTVIcon.EPG,
                    title = stringResource(R.string.setup_add_guide),
                    desc = stringResource(R.string.setup_add_guide_line),
                    value = SettingValue.Opens(null),
                    onClick = onAddGuide,
                )
            }
        }
        is SourceImporter.ImportState.Failed -> WizardFrame(
            step = WizardStep.PLAYLIST,
            title = stringResource(R.string.setup_import_failed),
            sub = state.failure.displayText(),
            back = WizardAction(stringResource(R.string.common_back), onCancel),
            next = WizardAction(stringResource(R.string.setup_try_again_caps), onRetry, rememberFirstFocus()),
        )
    }
}
