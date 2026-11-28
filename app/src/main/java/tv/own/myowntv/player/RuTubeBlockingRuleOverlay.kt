package tv.own.myowntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.stage.StageButton
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageText
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.i18n.firstLocaleCompat
import tv.own.owntv.core.player.contentKey
import tv.own.owntv.core.sync.RutubeBlockingRule
import tv.own.owntv.core.sync.UnavailableReason
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.player.PlaybackFailure

/** RuTube's own blocking picture and localized explanation. Text never waits for the image. */
@Composable
internal fun RuTubeBlockingRuleOverlay(
    rule: RutubeBlockingRule,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onBack: (() -> Unit)? = null,
    onChannelUp: (() -> Unit)? = null,
    onChannelDown: (() -> Unit)? = null,
    onOpenChannelList: (() -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val locale = LocalConfiguration.current.firstLocaleCompat()
    val language = remember(rule, locale) { selectBlockingRuleLanguage(rule, locale) }
    val imageUrl = remember(rule.imageUrl) { safeBlockingRuleImageUrl(rule.imageUrl) }
    val context = LocalContext.current
    val imageRequest = remember(imageUrl, compact) {
        imageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(if (compact) 320 else 960, if (compact) 180 else 540)
                .build()
        }
    }
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val title = language?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_error_channel)
    val description = language?.description?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.mpx),
                horizontalArrangement = Arrangement.spacedBy(14.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(132.mpx).aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.mpx))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageRequest != null && !imageFailed) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            onState = { state -> if (state is AsyncImagePainter.State.Error) imageFailed = true },
                        )
                    } else {
                        MyOwnTVIcon(MyOwnTVIcon.WARNING, tint = Color.White, modifier = Modifier.size(28.mpx))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = stageText(16, 700), color = Color.White, maxLines = 3)
                    if (description != null) {
                        Text(
                            description,
                            style = stageText(12, 400),
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 3,
                            modifier = Modifier.padding(top = 4.mpx),
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(0.72f).padding(24.mpx),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.mpx),
            ) {
                Box(
                    Modifier.fillMaxWidth(0.62f).aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.mpx))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageRequest != null && !imageFailed) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            onState = { state -> if (state is AsyncImagePainter.State.Error) imageFailed = true },
                        )
                    } else {
                        MyOwnTVIcon(MyOwnTVIcon.WARNING, tint = Color.White, modifier = Modifier.size(56.mpx))
                    }
                }
                Text(
                    title,
                    style = stageText(32, 700),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (description != null) {
                    Text(
                        description,
                        style = stageText(20, 400),
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                    )
                }
                if (onBack != null || onChannelUp != null || onChannelDown != null || onOpenChannelList != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                        onChannelUp?.let {
                            StageButton(
                                stringResource(R.string.settings_remote_button_previous),
                                onClick = it,
                                icon = MyOwnTVIcon.SKIP_PREVIOUS,
                            )
                        }
                        onOpenChannelList?.let {
                            StageButton(
                                stringResource(R.string.settings_player_ok_channel_list),
                                onClick = it,
                                icon = MyOwnTVIcon.LIST,
                            )
                        }
                        onChannelDown?.let {
                            StageButton(
                                stringResource(R.string.settings_remote_button_next),
                                onClick = it,
                                icon = MyOwnTVIcon.SKIP_NEXT,
                            )
                        }
                        if (onBack != null) {
                            StageButton(
                                stringResource(R.string.common_back),
                                onClick = onBack,
                                modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                                icon = MyOwnTVIcon.BACK,
                                tinted = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rich blocking overlay for the shared Live/Guide preview engine, guarded by channel identity. */
@Composable
internal fun LivePreviewFailureOverlay(
    engine: LivePreviewEngine,
    channel: ChannelEntity?,
    showVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val error by engine.error.collectAsStateWithLifecycle()
    val meta by engine.currentMeta.collectAsStateWithLifecycle()
    if (!showVideo || state != LivePreviewEngine.State.ERROR || channel == null) return
    val unavailable = (error as? PlaybackFailure.RuTubeUnavailable)?.unavailable ?: return
    if (unavailable.reason != UnavailableReason.BLOCKING_RULE) return
    val rule = unavailable.blockingRule ?: return
    if (meta.contentKey != contentKey(channel.remoteId)) return
    RuTubeBlockingRuleOverlay(rule = rule, compact = true, modifier = modifier.fillMaxSize())
}
