package tv.own.myowntv.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import tv.own.myowntv.core.settings.RemoteShortcutAction
import tv.own.myowntv.core.settings.RemoteShortcutBinding

data class RemoteShortcutEnvironment(
    val enabled: Boolean = false,
    val bindings: List<RemoteShortcutBinding> = emptyList(),
    val dispatch: (RemoteShortcutAction) -> Unit = {},
)

val LocalRemoteShortcuts = staticCompositionLocalOf { RemoteShortcutEnvironment() }
