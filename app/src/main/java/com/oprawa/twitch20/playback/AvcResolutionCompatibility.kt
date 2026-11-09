package com.oprawa.twitch20.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** Goldfish can retain the old output size after an adaptive AVC resolution change.
 * Recreate only that decoder, preserving the source, decoder order and other capabilities.
 * The separate OMX probe did not fix its fatal error and is never enabled in production.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal object AvcResolutionCompatibility {
    const val GOLDFISH_AVC = "c2.goldfish.h264.decoder"
    val DIAGNOSTIC_TARGETS = setOf("OMX.google.h264.decoder", GOLDFISH_AVC)

    fun selector(includeSoftwareProbe: Boolean = false) = MediaCodecSelector { mime, secure, tunneling ->
        MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
            .map { adjust(it, includeSoftwareProbe) }
    }

    fun adjust(codec: MediaCodecInfo, includeSoftwareProbe: Boolean = false): MediaCodecInfo {
        val affected = codec.name == GOLDFISH_AVC ||
            (includeSoftwareProbe && codec.name == "OMX.google.h264.decoder")
        return if (!affected || codec.mimeType != "video/avc") codec
        else MediaCodecInfo.newInstance(codec.name, codec.mimeType, codec.codecMimeType,
            codec.capabilities, codec.hardwareAccelerated, codec.softwareOnly, codec.vendor,
            /* forceDisableAdaptive = */ true, /* forceSecure = */ codec.secure)
    }
}
