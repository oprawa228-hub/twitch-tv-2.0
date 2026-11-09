package com.oprawa.twitch20

import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only device inventory. No stream data, account values or playback URLs are inspected. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AvcDecoderInventoryTest {
    @Test fun reportAvailableAvcDecodersAndMedia3Order() {
        val candidates = MediaCodecSelector.DEFAULT.getDecoderInfos("video/avc", false, false)
        assertTrue("No AVC decoder is available", candidates.isNotEmpty())
        candidates.forEachIndexed { index, codec ->
            Log.i("TwitchPlayback", "avc_media3_candidate index=$index name=${codec.name}" +
                " hardware=${codec.hardwareAccelerated} software=${codec.softwareOnly}" +
                " adaptive=${codec.adaptive} secure=${codec.secure}")
        }
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { type -> type.equals("video/avc", true) } }
            .forEach { codec ->
                val caps = codec.getCapabilitiesForType("video/avc")
                val video = caps.videoCapabilities
                val flags = if (Build.VERSION.SDK_INT >= 29)
                    " hardware=${codec.isHardwareAccelerated} software=${codec.isSoftwareOnly} alias=${codec.isAlias}" else ""
                Log.i("TwitchPlayback", "avc_platform_candidate name=${codec.name}$flags" +
                    " adaptive=${caps.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback)}" +
                    " size1080_60=${video?.areSizeAndRateSupported(1920, 1080, 60.0)}" +
                    " size720_60=${video?.areSizeAndRateSupported(1280, 720, 60.0)}" +
                    " max_instances=${caps.maxSupportedInstances}" +
                    " profiles=${caps.profileLevels.joinToString(",") { "${it.profile}:${it.level}" }}")
            }
    }
}
