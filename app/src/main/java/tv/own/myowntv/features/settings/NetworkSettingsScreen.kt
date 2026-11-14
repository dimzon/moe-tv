package tv.own.myowntv.features.settings
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

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import androidx.compose.ui.res.pluralStringResource
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.components.MyOwnTVIcon

/** This screen's rows as Settings search finds them (the host/port fields are found by the proxy keywords). */
internal val PROXY_SEARCH_ROWS: List<Int> = listOf(R.string.settings_use_proxy, R.string.settings_test_proxy)

/**
 * Network → Proxy: one app-wide HTTP proxy. Enabling it routes all app traffic (playlist,
 * API, EPG, images, updates) and live stream playback through the proxy.
 */
@Composable
fun NetworkSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val config by vm.proxyConfig.collectAsStateWithLifecycle()
    val testState by vm.proxyTest.collectAsStateWithLifecycle()

    var seeded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    LaunchedEffect(config) {
        if (!seeded) {
            enabled = config.enabled
            host = config.host
            port = if (config.port > 0) config.port.toString() else ""
            user = config.username
            pass = config.password
            seeded = true
        }
    }

    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }

    val portInt = port.trim().toIntOrNull() ?: 0
    val save = {
        vm.saveProxy(enabled, host, portInt, user, pass)
        vm.resetProxyTest()
    }

    // Use proxy, the four fields as rows and Test proxy; the privacy notes in the panel.
    val privacy = stringResource(R.string.settings_proxy_privacy)
    val limits = stringResource(R.string.settings_proxy_limitations)
    val about = stringResource(R.string.settings_proxy_description)
    val notes: @Composable () -> Unit = {
        Text(privacy, style = tv.own.myowntv.ui.theme.stageText(16, 500), color = tv.own.myowntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(top = 14.mpx))
        Text(limits, style = tv.own.myowntv.ui.theme.stageText(16, 500), color = tv.own.myowntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(top = 8.mpx))
    }
    val fieldHelp: @Composable (String) -> SettingHelp = { label -> SettingHelp(label, about, extra = notes) }
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_network)),
        title = stringResource(R.string.common_proxy),
        count = pluralStringResource(R.plurals.settings_setting_count, 6, 6),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
    ) {
        val useTitle = stringResource(R.string.settings_use_proxy)
        val useValue = SettingValue.Switch(enabled)
        StageSettingRow(
            icon = MyOwnTVIcon.NETWORK,
            title = useTitle,
            desc = about,
            value = useValue,
            onClick = { enabled = !enabled; save() },
            help = settingHelp(null, useTitle, about, useValue, pinnable = false).copy(extra = notes),
        )
        val hostLabel = stringResource(R.string.settings_host)
        StageFieldRow(MyOwnTVIcon.PENCIL, hostLabel, host, { host = it }, fieldHelp(hostLabel), placeholder = stringResource(R.string.settings_proxy_host_hint), onDone = save)
        val portLabel = stringResource(R.string.settings_port)
        StageFieldRow(
            MyOwnTVIcon.PENCIL, portLabel, port, { port = it.filter { c -> c.isDigit() }.take(5) }, fieldHelp(portLabel),
            placeholder = stringResource(R.string.settings_proxy_port_hint), keyboardType = KeyboardType.Number, onDone = save,
        )
        val userLabel = stringResource(R.string.settings_username_optional)
        StageFieldRow(MyOwnTVIcon.PENCIL, userLabel, user, { user = it }, fieldHelp(userLabel), onDone = save)
        val passLabel = stringResource(R.string.settings_password_optional)
        StageFieldRow(MyOwnTVIcon.PENCIL, passLabel, pass, { pass = it }, fieldHelp(passLabel), password = true, onDone = save)
        val testTitle = stringResource(if (testState is SettingsViewModel.ProxyTestState.Testing) R.string.settings_testing else R.string.settings_test_proxy)
        StageSettingRow(
            icon = MyOwnTVIcon.REFRESH,
            title = testTitle,
            desc = proxyTestText(testState),
            value = null,
            onClick = { vm.testProxy(host, portInt, user, pass) },
            help = SettingHelp(testTitle, about, hints = settingHints(null, pinnable = false), extra = notes),
        )
    }
}

@Composable
private fun proxyTestText(state: SettingsViewModel.ProxyTestState): String? = when (state) {
    is SettingsViewModel.ProxyTestState.Ok -> stringResource(R.string.settings_proxy_connected, state.millis)
    is SettingsViewModel.ProxyTestState.Fail -> when (val failure = state.failure) {
        SettingsViewModel.ProxyFailure.InvalidAddress -> stringResource(R.string.settings_proxy_invalid_address)
        SettingsViewModel.ProxyFailure.HostUnreachable -> stringResource(R.string.settings_proxy_host_unreachable)
        SettingsViewModel.ProxyFailure.TimedOut -> stringResource(R.string.settings_proxy_timed_out)
        SettingsViewModel.ProxyFailure.ConnectionFailed -> stringResource(R.string.settings_proxy_connection_failed)
        is SettingsViewModel.ProxyFailure.Http -> stringResource(R.string.settings_proxy_http, failure.code)
        is SettingsViewModel.ProxyFailure.Unknown -> failure.rawMessage ?: stringResource(R.string.settings_proxy_failed)
    }
    else -> null
}
