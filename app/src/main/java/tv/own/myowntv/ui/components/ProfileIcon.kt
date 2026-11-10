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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Rank 1 — Profile icon. Person silhouette + star accent on 100-grid.
 */
@Composable
fun ProfileIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 100f
        val soft = color.copy(alpha = 0.43f)
        val fill = color
        val stroke = Stroke(width = 7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun o(x: Float, y: Float) = Offset(x * s, y * s)

        // Head — circle
        drawCircle(fill, radius = 13f * s, center = o(50f, 38f), style = stroke)
        // Body arc — cubic bezier
        val body = Path().apply {
            moveTo(24f * s, 81f * s)
            cubicTo(28f * s, 67f * s, 37f * s, 60f * s, 50f * s, 60f * s)
            cubicTo(63f * s, 60f * s, 72f * s, 67f * s, 76f * s, 81f * s)
        }
        drawPath(body, fill, style = stroke)
        // Star accent
        val star = Path().apply {
            moveTo(76f * s, 19f * s); lineTo(78f * s, 24f * s); lineTo(83f * s, 26f * s)
            lineTo(78f * s, 28f * s); lineTo(76f * s, 33f * s); lineTo(74f * s, 28f * s)
            lineTo(69f * s, 26f * s); lineTo(74f * s, 24f * s); close()
        }
        drawPath(star, soft, style = stroke)
    }
}
