package tv.own.owntv.player

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * What the HUD's volume number means, identically in every live engine.
 *
 * - **0–100 %** is linear amplitude: 50 % is half, −6 dB. ExoPlayer's `volume` is already linear.
 * - **100–150 %** is a boost of [BOOST_DB_PER_PERCENT] per percent, so 150 % ≈ +10 dB, via the
 *   LoudnessEnhancer ([VolumeBoost]).
 *
 * The curve is shared by every engine (preview, Multiview pool, hero) precisely because the remembered
 * per-channel number moves with a channel between them: a remembered "130 %" must sound the same in
 * each. An earlier flat +3.5 dB ceiling made 150 % and 50 % land in different places everywhere.
 */
internal object VolumeCurve {
    const val MAX_PERCENT = 150
    const val BOOST_DB_PER_PERCENT = 0.2

    /** Linear amplitude for a HUD [percent]. */
    fun amplitude(percent: Int): Double {
        val p = percent.coerceIn(0, MAX_PERCENT)
        return if (p <= 100) p / 100.0 else 10.0.pow((p - 100) * BOOST_DB_PER_PERCENT / 20.0)
    }

    /** LoudnessEnhancer target gain in millibels; 0 at or below 100 %. */
    fun boostMillibels(percent: Int): Int =
        ((percent.coerceIn(0, MAX_PERCENT) - 100).coerceAtLeast(0) * BOOST_DB_PER_PERCENT * 100).roundToInt()

    /** The HUD percent [db] decibels below (negative) or above [percent]. */
    fun shiftedByDb(percent: Int, db: Double): Int {
        val a = amplitude(percent) * 10.0.pow(db / 20.0)
        val p = if (a <= 1.0) a * 100.0 else 100.0 + 20.0 * log10(a) / BOOST_DB_PER_PERCENT
        return p.roundToInt().coerceIn(0, MAX_PERCENT)
    }
}
