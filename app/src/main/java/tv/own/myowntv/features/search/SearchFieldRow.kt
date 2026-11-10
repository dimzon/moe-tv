package tv.own.myowntv.features.search
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

import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.stage.StageSearchField
import tv.own.myowntv.ui.stage.StageTool
import tv.own.myowntv.ui.theme.mpx

/**
 * Search's field, with the voice-search microphone on its right where the device has a speech
 * recogniser. What the user says becomes the query, so it searches exactly as typing it would.
 * ▼ from either goes to [focusDown].
 */
@Composable
internal fun SearchFieldRow(
    query: String,
    onQueryChange: (String) -> Unit,
    fieldFocus: FocusRequester,
    focusDown: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
        StageSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.search_field_hint),
            modifier = Modifier
                .weight(1f)
                .focusRequester(fieldFocus)
                .focusProperties { down = focusDown },
        )
        VoiceSearchButton(onQuery = onQueryChange, modifier = Modifier.focusProperties { down = focusDown })
    }
}

/** The 48-high microphone; absent when no recogniser answers, or once one has refused to start. */
@Composable
private fun VoiceSearchButton(onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var available by remember(context) { mutableStateOf(VoiceSearch.isAvailable(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        VoiceSearch.spokenQuery(result.resultCode, matches)?.let(onQuery)
    }
    if (!available) return
    val label = stringResource(R.string.search_voice)
    val prompt = stringResource(R.string.search_voice_prompt)
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    StageTool(
        text = null,
        icon = MyOwnTVIcon.MIC,
        boxed = true,
        onClick = {
            // Resolving is not a promise: the recogniser can be disabled between the check and OK.
            // The recogniser is a screen OwnTV opens itself, so it must not open picture-in-picture.
            try {
                tv.own.myowntv.features.pip.startAsHandOff(VoiceSearch.intent(prompt, language)) { launcher.launch(it) }
            } catch (_: ActivityNotFoundException) {
                available = false
            }
        },
        modifier = modifier.semantics { contentDescription = label },
    )
}
