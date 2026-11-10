package tv.own.myowntv.features.categorylock
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
import tv.own.owntv.core.profile.*
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.onDpadLeft
import tv.own.myowntv.ui.stage.StageButton
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageText

/**
 * What a section's item list shows when it has nothing to list: [text]. A [sealed] category adds an
 * Enter PIN button that asks the PIN through [onEnterPin]. The button holds [pinFocus], the requester
 * the first item would hold, so the ways into the list (the rail's right, the search field's down)
 * land on it as they would on a title. [onNavigateLeft] is the button's ◀, the way out a title's ◀
 * would take (the categories); null leaves ◀ to the focus search.
 */
@Composable
internal fun CategoryEmptyState(
    text: String,
    sealed: Boolean,
    onEnterPin: () -> Unit,
    pinFocus: FocusRequester,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    onNavigateLeft: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.mpx), horizontalAlignment = horizontalAlignment) {
        Text(text, style = stageText(20, 500), color = StageColors.Muted)
        if (sealed) {
            StageButton(
                text = stringResource(R.string.settings_customize_enter_pin),
                onClick = onEnterPin,
                icon = MyOwnTVIcon.LOCK,
                modifier = Modifier.focusRequester(pinFocus).onDpadLeft({ onNavigateLeft != null }) { onNavigateLeft?.invoke() },
            )
        }
    }
}
