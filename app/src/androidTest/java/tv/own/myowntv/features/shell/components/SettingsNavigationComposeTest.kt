package tv.own.myowntv.features.shell.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationComposeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rootBackClearsSearchBeforeExiting() {
        val state = mutableStateOf(
            SettingsRootBackState(searchQuery = "theme", searchMode = true, quickReturn = true),
        )
        val events = mutableListOf<String>()

        compose.setContent {
            SettingsRootBackHandler(
                state = state.value,
                onClearSearch = { events += "clear" },
                onCloseSearch = { events += "close_search" },
                onReturnToQuick = { events += "quick" },
                onExit = { events += "exit" },
            )
        }

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.runOnIdle { assertEquals(listOf("clear"), events) }
    }

    @Test
    fun specialisedModalBackKeepsPriorityOverRoot() {
        var modalDismissals = 0
        var rootExits = 0

        compose.setContent {
            SettingsRootBackHandler(
                state = SettingsRootBackState(modalOpen = true),
                onClearSearch = {},
                onCloseSearch = {},
                onReturnToQuick = {},
                onExit = { rootExits++ },
            )
            BackHandler { modalDismissals++ }
        }

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.runOnIdle {
            assertEquals(1, modalDismissals)
            assertEquals(0, rootExits)
        }
    }

    @Test
    fun unattachedReturnTargetFallsBackToAttachedSearchAndClearsKey() {
        var returnKey by mutableStateOf<String?>("removed")
        var consumed by mutableStateOf(false)

        compose.setContent {
            val staleReturn = remember { FocusRequester() }
            val rowsFocus = remember { FocusRequester() }
            val searchFocus = remember { FocusRequester() }
            Box(
                Modifier
                    .size(48.dp)
                    .testTag("search_fallback")
                    .focusRequester(searchFocus)
                    .focusable(),
            )
            SettingsMenuFocusRestore(
                request = 1,
                menuOpen = false,
                returnKey = returnKey,
                hasRows = false,
                returnFocus = staleReturn,
                rowsFocus = rowsFocus,
                searchFocus = searchFocus,
                onReturnConsumed = {
                    returnKey = null
                    consumed = true
                },
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) { consumed }
        compose.runOnIdle { assertNull(returnKey) }
        compose.onNodeWithTag("search_fallback").assertIsFocused()
    }
}
