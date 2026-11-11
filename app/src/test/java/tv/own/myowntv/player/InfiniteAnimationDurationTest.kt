package tv.own.myowntv.player
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

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 4.2.3 crashed full-screen Live TV for every user who had **Animations = Off**: the new LIVE badge
 * pulsed with `infiniteRepeatable(ownTvTween(900))`, and `ownTvTween` collapses to 0 ms at that
 * setting. Compose rejects a 0-duration infinite animation in the spec's constructor, so the throw
 * landed in composition — the HUD died the instant the channel went full screen.
 *
 * The first test pins the platform behaviour (so nobody "fixes" the badge back). The second is the
 * one that actually protects us: it fails the build if the forbidden pairing reappears anywhere.
 */
class InfiniteAnimationDurationTest {

    @Test
    fun `a zero duration infinite repeatable throws while composing`() {
        try {
            infiniteRepeatable<Float>(tween(durationMillis = 0), RepeatMode.Reverse)
            fail("Compose now tolerates a 0-duration infinite animation — re-check the badge fix")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("0-duration"))
        }
    }

    @Test
    fun `a fixed duration infinite repeatable is safe`() {
        val spec = infiniteRepeatable<Float>(tween(durationMillis = 900), RepeatMode.Reverse)
            .vectorize(Float.VectorConverter)
        val from = Float.VectorConverter.convertToVector(1f)
        val to = Float.VectorConverter.convertToVector(0.25f)
        val zero = Float.VectorConverter.convertToVector(0f)
        val value = Float.VectorConverter.convertFromVector(
            spec.getValueFromNanos(450L * 1_000_000L, from, to, zero),
        )
        assertTrue(value in 0.25f..1f)
    }

    @Test
    fun `no source hands ownTvTween to infiniteRepeatable`() {
        val sources = File("src/main/java")
        assertTrue("expected to run from the app module, cwd=${File(".").absolutePath}", sources.isDirectory)
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains(Regex("""infiniteRepeatable\s*\(\s*ownTvTween""")) }
            .map { it.path }
            .toList()
        assertEquals("ownTvTween is 0 ms when Animations = Off; see its KDoc", emptyList<String>(), offenders)
    }
}
