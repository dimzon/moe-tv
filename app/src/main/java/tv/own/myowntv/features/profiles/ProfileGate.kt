package tv.own.myowntv.features.profiles
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

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.myowntv.ui.components.MyOwnTVAvatar
import tv.own.myowntv.ui.components.MyOwnTVIcon

/**
 * The "Who's watching?" launch gate. Shown when more than one profile exists. Picking a
 * profile makes it active (and prompts for a PIN if it's locked); [onEnter] records the selected
 * profile id and proceeds into the shell.
 */
@Composable
fun ProfileGate(onEnter: (Long) -> Unit, onAddProfile: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ProfilesViewModel = koinViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()

    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(profiles.isNotEmpty()) {
        if (profiles.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    fun choose(p: ProfileEntity) {
        if (p.pinHash != null) {
            pinError = false
            pinFor = p
        } else {
            vm.switchTo(p) { onEnter(p.id) }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(tv.own.myowntv.ui.theme.LocalStagePalette.current.page),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.profiles_gate_title), style = tv.own.myowntv.ui.theme.stageText(54, 700), color = tv.own.myowntv.ui.theme.StageColors.Text)
            Spacer(Modifier.height(36.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.focusGroup().padding(horizontal = 48.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    ProfileTile(
                        profile = p,
                        modifier = if (p.id == profiles.first().id) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { choose(p) },
                    )
                }
                item { AddTile(onClick = onAddProfile) }
            }
        }
    }

    pinFor?.let { p ->
        PinDialog(
            title = if (pinError) stringResource(R.string.profiles_wrong_pin) else stringResource(R.string.profiles_enter_pin, p.name),
            onSubmit = { pin ->
                if (vm.verifyPin(p, pin)) {
                    vm.switchTo(p) { onEnter(p.id) }
                } else pinError = true
            },
            onDismiss = { pinFor = null },
        )
    }
}

@Composable
private fun ProfileTile(profile: ProfileEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        tv.own.myowntv.ui.stage.StageSurface(
            onClick = onClick,
            radius = 24.dp,
            modifier = modifier.size(132.dp),
            focusStyle = tv.own.myowntv.ui.stage.StageFocus.FX,
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(contentAlignment = Alignment.TopEnd) {
                MyOwnTVAvatar(avatarId = profile.avatarId, imagePath = profile.avatarPath.orEmpty(), modifier = Modifier.size(104.dp))
                if (profile.pinHash != null) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(tv.own.myowntv.ui.theme.StageColors.Ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        MyOwnTVIcon(MyOwnTVIcon.SETTINGS, tint = tv.own.myowntv.ui.theme.StageColors.Text, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(profile.name, style = tv.own.myowntv.ui.theme.stageText(32, 600), color = tv.own.myowntv.ui.theme.StageColors.Text, textAlign = TextAlign.Center)
        if (profile.isKids) {
            Text(stringResource(R.string.profiles_kids_badge), style = tv.own.myowntv.ui.theme.stageText(20, 500), color = tv.own.myowntv.ui.theme.stageAccent.accent)
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        tv.own.myowntv.ui.stage.StageSurface(
            onClick = onClick,
            radius = 24.dp,
            modifier = Modifier.size(132.dp),
            focusStyle = tv.own.myowntv.ui.stage.StageFocus.FX,
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(
                modifier = Modifier.size(104.dp).clip(CircleShape).background(tv.own.myowntv.ui.theme.StageColors.Ink.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                MyOwnTVIcon(MyOwnTVIcon.ADD, tint = tv.own.myowntv.ui.theme.StageColors.Muted, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.profiles_add), style = tv.own.myowntv.ui.theme.stageText(32, 600), color = tv.own.myowntv.ui.theme.StageColors.Muted)
    }
}
