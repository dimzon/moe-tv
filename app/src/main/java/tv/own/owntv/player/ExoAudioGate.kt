package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/** Which audio ExoPlayer can play — the "no playable audio" check behind the live engine's watchdog. */
@UnstableApi
object ExoAudioGate {

    /** Why the gate let a codec through, or [UNSUPPORTED]. */
    enum class Verdict(val safe: Boolean) {
        ALLOWLIST(true), DEVICE_DECODER(true), PASSTHROUGH(true), UNSUPPORTED(false)
    }

    /**
     * Engine codec names every device decodes (MediaCodec or Media3's own), matched by prefix. MP2 is not
     * one of them: Media3 has no MP2→MP3 substitution and many decoders declare only MP3, so it goes
     * through the checks below like DTS and TrueHD.
     */
    private val ALWAYS_DECODABLE = setOf("aac", "ac3", "eac3", "mp3", "opus", "vorbis", "flac", "pcm", "alac")

    private val deviceDecodable = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** The device offers a MediaCodec decoder for this audio MIME type, whatever its advertised limits. */
    fun deviceDecodes(mime: String): Boolean = deviceDecodable.getOrPut(mime) {
        runCatching { MediaCodecUtil.getDecoderInfos(mime, false, false).isNotEmpty() }.getOrDefault(false)
    }

    /** The Android MIME type for a bare audio codec name (as containers and demuxers report it), or
     *  null when Media3 has none for it (MLP). */
    fun mimeFor(codec: String): String? = when {
        codec.startsWith("eac3") || codec.startsWith("e-ac-3") -> MimeTypes.AUDIO_E_AC3
        codec.startsWith("ac3") || codec.startsWith("ac-3") -> MimeTypes.AUDIO_AC3
        codec.startsWith("dts") || codec.startsWith("dca") -> MimeTypes.AUDIO_DTS
        codec.startsWith("truehd") -> MimeTypes.AUDIO_TRUEHD
        codec.startsWith("mp2") -> MimeTypes.AUDIO_MPEG_L2
        codec.startsWith("mp3") -> MimeTypes.AUDIO_MPEG
        codec.startsWith("aac") -> MimeTypes.AUDIO_AAC
        codec.startsWith("opus") -> MimeTypes.AUDIO_OPUS
        codec.startsWith("vorbis") -> MimeTypes.AUDIO_VORBIS
        codec.startsWith("flac") -> MimeTypes.AUDIO_FLAC
        codec.startsWith("alac") -> MimeTypes.AUDIO_ALAC
        else -> null
    }

    /**
     * Can ExoPlayer play audio reported as [codec] (lower case)? Through a device decoder, or bitstreamed
     * to a receiver that takes it — the last only when the caller allows passthrough at all.
     */
    fun verdict(
        codec: String,
        deviceDecoder: (String) -> Boolean,
        passthrough: (String) -> Boolean,
    ): Verdict {
        if (ALWAYS_DECODABLE.any { codec.startsWith(it) }) return Verdict.ALLOWLIST
        val mime = mimeFor(codec) ?: return Verdict.UNSUPPORTED
        return when {
            deviceDecoder(mime) -> Verdict.DEVICE_DECODER
            passthrough(mime) -> Verdict.PASSTHROUGH
            else -> Verdict.UNSUPPORTED
        }
    }

    /**
     * Some audio track ExoPlayer can play: a track within its decoder's advertised channels, rate and
     * profile. (A track that only *seems* supported — beyond those limits — is not: nothing here can
     * software-decode it any more, so it would play silent.)
     */
    fun anyPlayableAudio(tracks: Tracks): Boolean = tracks.groups.any { g ->
        g.type == C.TRACK_TYPE_AUDIO && (0 until g.length).any { g.isTrackSupported(it) }
    }
}
