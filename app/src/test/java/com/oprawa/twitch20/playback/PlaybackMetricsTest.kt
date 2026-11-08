package com.oprawa.twitch20.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackMetricsTest {
    @Test fun `request first frame includes resolver but prepare metric does not`() {
        val metrics = PlaybackMetrics()
        metrics.beginAttempt(requestStartedAtMs = 1_000, preparedAtMs = 1_400)
        metrics.onState(PlaybackStatus.Buffering, true, true, 1_400)
        metrics.onState(PlaybackStatus.Ready, true, true, 1_900)
        metrics.firstFrame(2_000)
        val result = metrics.snapshot(2_000)
        assertEquals(400L, result.sourceResolveMs)
        assertEquals(600L, result.prepareFirstFrameMs)
        assertEquals(1_000L, result.requestFirstFrameMs)
        assertEquals(0, result.rebufferCount)
        assertEquals(500L, result.bufferingMs[BufferingReason.STARTUP])
    }

    @Test fun `repeated buffering events count one stall and stop on ready`() {
        val metrics = playingMetrics()
        metrics.onState(PlaybackStatus.Buffering, true, true, 1_000)
        metrics.onState(PlaybackStatus.Buffering, true, true, 1_100)
        assertEquals(200L, metrics.snapshot(1_200).rebufferDurationMs)
        metrics.onState(PlaybackStatus.Ready, true, true, 1_500)
        assertEquals(1, metrics.snapshot(2_000).rebufferCount)
        assertEquals(500L, metrics.snapshot(2_000).rebufferDurationMs)
    }

    @Test fun `pause background and error close stalls without counting idle time`() {
        val metrics = playingMetrics()
        metrics.onState(PlaybackStatus.Buffering, true, true, 1_000)
        metrics.onState(PlaybackStatus.Buffering, false, true, 1_100)
        metrics.onState(PlaybackStatus.Buffering, true, true, 2_000)
        metrics.onState(PlaybackStatus.Idle, false, false, 2_200)
        metrics.onState(PlaybackStatus.Buffering, true, true, 3_000)
        metrics.onState(PlaybackStatus.Error, true, true, 3_300)
        val result = metrics.snapshot(10_000)
        assertEquals(3, result.rebufferCount)
        assertEquals(600L, result.rebufferDurationMs)
    }

    @Test fun `seek and foreground preparation have distinct excluded durations`() {
        val metrics = playingMetrics()
        metrics.markPreparation(BufferingReason.SEEK, 1_000)
        metrics.onState(PlaybackStatus.Buffering, true, true, 1_000)
        metrics.onState(PlaybackStatus.Ready, true, true, 1_200)
        metrics.markPreparation(BufferingReason.FOREGROUND, 2_000)
        metrics.onState(PlaybackStatus.Buffering, true, true, 2_000)
        metrics.onState(PlaybackStatus.Ready, true, true, 2_500)
        val result = metrics.snapshot(3_000)
        assertEquals(0, result.rebufferCount)
        assertEquals(200L, result.bufferingMs[BufferingReason.SEEK])
        assertEquals(500L, result.bufferingMs[BufferingReason.FOREGROUND])
    }

    @Test fun `retry creates a separate timing attempt and finish closes active stall`() {
        val metrics = playingMetrics()
        metrics.onState(PlaybackStatus.Buffering, true, true, 1_000)
        metrics.finish(1_250)
        assertEquals(250L, metrics.snapshot(8_000).rebufferDurationMs)
        metrics.beginAttempt(10_000, 10_400)
        metrics.firstFrame(11_000)
        metrics.firstFrame(12_000) // Surface reattachment cannot replace the first-frame sample.
        val result = metrics.snapshot(12_000)
        assertEquals(2, result.attemptId)
        assertEquals(0, result.rebufferCount)
        assertEquals(1_000L, result.requestFirstFrameMs)
    }

    @Test fun `source replacement and internal seeks do not become network stalls`() {
        val metrics = playingMetrics()
        metrics.markPreparation(BufferingReason.QUALITY_SWITCH, 1_000)
        metrics.markPreparation(BufferingReason.SEEK, 1_001)
        metrics.onState(PlaybackStatus.Buffering, true, true, 1_010)
        metrics.onState(PlaybackStatus.Ready, true, true, 1_310)
        metrics.markPreparation(BufferingReason.RECOVERY, 2_000)
        metrics.markPreparation(BufferingReason.SEEK, 2_001)
        metrics.onState(PlaybackStatus.Buffering, true, true, 2_010)
        metrics.onState(PlaybackStatus.Ready, true, true, 2_410)
        val result = metrics.snapshot(3_000)
        assertEquals(0, result.rebufferCount)
        assertEquals(300L, result.bufferingMs[BufferingReason.QUALITY_SWITCH])
        assertEquals(400L, result.bufferingMs[BufferingReason.RECOVERY])
    }

    private fun playingMetrics() = PlaybackMetrics().apply {
        beginAttempt(0, 100)
        onState(PlaybackStatus.Ready, true, true, 200)
        firstFrame(200)
    }
}
