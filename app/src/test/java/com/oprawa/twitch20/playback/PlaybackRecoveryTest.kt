package com.oprawa.twitch20.playback

import org.junit.Assert.*
import org.junit.Test

/** Pure transition coverage; actual ExoPlayer lifecycle and D-pad wiring need Android QA. */
class PlaybackRecoveryTest {
    @Test fun `buffering with playback requested still offers pause`() {
        val buffering = PlaybackState(status = PlaybackStatus.Buffering,
            isPlaying = false, playWhenReady = true)
        assertTrue(buffering.showPauseAction)
    }

    @Test fun `paused buffering offers resume and finished media offers replay`() {
        assertFalse(PlaybackState(status = PlaybackStatus.Buffering,
            isPlaying = false, playWhenReady = false).showPauseAction)
        assertFalse(PlaybackState(status = PlaybackStatus.Ended,
            isPlaying = false, playWhenReady = true).showPauseAction)
    }

    @Test fun `foreground retry discards old failure and retains media and quality`() {
        val failed = PlaybackState(mediaId = "vod:123", status = PlaybackStatus.Error,
            positionMs = 42_000, durationMs = 90_000, error = "Нет соединения",
            qualityOptions = listOf(QualityOption("0:1", "720p")), selectedQualityId = "0:1")
        val reconnecting = PlaybackPolicy.foregroundState(failed, resumePlayback = true)
        assertNull(reconnecting.error)
        assertEquals(PlaybackStatus.Buffering, reconnecting.status)
        assertFalse(reconnecting.isPlaying)
        assertTrue(reconnecting.playWhenReady)
        assertEquals(failed.mediaId, reconnecting.mediaId)
        assertEquals(failed.positionMs, reconnecting.positionMs)
        assertEquals(failed.durationMs, reconnecting.durationMs)
        assertEquals(failed.qualityOptions, reconnecting.qualityOptions)
        assertEquals(failed.selectedQualityId, reconnecting.selectedQualityId)
    }

    @Test fun `foreground preparation does not resume a recording paused before Home`() {
        val paused = PlaybackState(mediaId = "vod:123", status = PlaybackStatus.Ready,
            positionMs = 42_000, playWhenReady = false)
        val reconnecting = PlaybackPolicy.foregroundState(paused, resumePlayback = false)
        assertFalse(reconnecting.playWhenReady)
        assertFalse(reconnecting.isPlaying)
        assertEquals(42_000L, reconnecting.positionMs)
    }

    @Test fun `progressive quality change preserves position and paused intent`() {
        for (requested in listOf(false, true)) {
            val original = PlaybackState(mediaId = "clip:example", status = PlaybackStatus.Ready,
                positionMs = 23_000, durationMs = 50_000, playWhenReady = requested,
                qualityOptions = listOf(QualityOption("1080", "1080p"), QualityOption("480", "480p")),
                selectedQualityId = "1080", canAutoSelectQuality = false)
            val switched = PlaybackPolicy.qualitySwitchState(original, "480", "480p")
            assertEquals("clip:example", switched.mediaId)
            assertEquals(23_000L, switched.positionMs)
            assertEquals(requested, switched.playWhenReady)
            assertFalse(switched.isPlaying)
            assertFalse(switched.canAutoSelectQuality)
            assertEquals(original.qualityOptions, switched.qualityOptions)
            assertEquals("480", switched.selectedQualityId)
            assertEquals("480p", switched.activeQuality)
        }
    }
}
