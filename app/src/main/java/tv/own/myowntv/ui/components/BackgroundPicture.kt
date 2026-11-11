package tv.own.myowntv.ui.components
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

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import tv.own.owntv.core.theme.BackgroundConfig
import tv.own.myowntv.ui.theme.BlurredBackdrop
import tv.own.myowntv.ui.theme.drawBackdropSlice

private const val BG_TAG = "BgImage"

/**
 * The user's picture as Glass & background draws it: with Blur above 0, the matching level of the
 * blurred copy stretched over the area (one texture, the same cost as the sharp picture; until the copy
 * exists the sharp picture shows), then Darken as the page colour over it (Sharp's 15% = the former fixed
 * 16% scrim). Used behind the whole app and, scaled, in the Settings preview.
 */
@Composable
fun BackgroundPicture(background: BackgroundConfig, blurred: BlurredBackdrop?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val path = background.imagePath
    // An explicit file:// Uri guarantees Coil's FileFetcher decodes it, and failures are logged.
    val request = remember(path) {
        coil3.request.ImageRequest.Builder(context).data(android.net.Uri.fromFile(java.io.File(path))).build()
    }
    Box(modifier) {
        val frost = if (background.blurPct > 0) blurred?.frostFor(background.blurPct / 100f) else null
        if (blurred != null && frost != null) {
            Canvas(Modifier.fillMaxSize()) { drawBackdropSlice(blurred, frost, Rect(Offset.Zero, size)) }
        } else {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                onError = { Log.w(BG_TAG, "background image load failed: ${it.result.throwable.message}") },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Light: the picture fades into the paper instead, and always far enough for dark ink to read on it
        // (Sharp 15% → 62%, Soft 50% → 78%, Dark 75% → 89%).
        val dim = background.dimPct / 100f
        val wash = if (tv.own.myowntv.ui.theme.LocalStagePalette.current.isLight) Color(0xFFF4F7F5).copy(alpha = 0.55f + 0.45f * dim)
        else Color(0xFF05080A).copy(alpha = dim)
        Box(Modifier.fillMaxSize().background(wash))
    }
}
