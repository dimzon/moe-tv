package tv.own.myowntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import tv.own.myowntv.core.setup.displayText
import tv.own.myowntv.core.util.FriendlySyncFailure

/** Resolve a classified sync failure only at the Compose presentation boundary; the sentences are core's. */
@Composable
fun FriendlySyncFailure.displayText(): String = displayText(LocalContext.current.resources)
