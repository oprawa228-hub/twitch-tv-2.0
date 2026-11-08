package com.oprawa.twitch20.playback

import org.junit.Assert.*
import org.junit.Test

class HlsPlaybackDiagnosticsTest {
    @Test fun `safe heartbeat distinguishes an unchanged playlist from advancing media`() {
        val diagnostics = HlsPlaybackDiagnostics()
        val first = diagnostics.sample(sequence = 100, nowMs = 1_000)!!
        assertTrue(first.contains("media_sequence=100"))
        assertTrue(first.contains("playlist_unchanged_ms=0"))
        assertNull(diagnostics.sample(sequence = 100, nowMs = 2_000))
        val stalled = diagnostics.sample(sequence = 100, nowMs = 6_000)!!
        assertTrue(stalled.contains("playlist_unchanged_ms=5000"))
        assertTrue(stalled.contains("sequence_delta=0"))
        assertNull(diagnostics.sample(sequence = 102, nowMs = 7_000))
        val moving = diagnostics.sample(sequence = 104, nowMs = 11_000)!!
        assertTrue(moving.contains("sequence_delta=4"))
        assertTrue(moving.contains("playlist_unchanged_ms=0"))
    }

    @Test fun `commercial metadata changes are reported without retaining secret tag contents`() {
        val diagnostics = HlsPlaybackDiagnostics()
        diagnostics.sample(nowMs = 1_000)
        val line = diagnostics.sample(nowMs = 1_100, tags = listOf(
            "#EXT-X-DATERANGE:ID=\"twitch-stitched-ad-secret-id\",X-TV-TWITCH-AD-URL=\"https://private.example/segment?token=secret-value\"",
            "#EXT-X-SESSION-DATA:VALUE=\"private-session-value\"",
        ))!!
        assertTrue(line.contains("commercial_metadata=1"))
        listOf("secret", "private", "https:", "token", "active_ad").forEach { assertFalse(line.contains(it)) }
        val ended = diagnostics.sample(nowMs = 1_200)!!
        assertTrue(ended.contains("commercial_metadata=0"))
    }

    @Test fun `low latency part growth is progress without a sequence increment`() {
        val diagnostics = HlsPlaybackDiagnostics()
        diagnostics.sample(nowMs = 1_000)
        assertNull(diagnostics.sample(parts = 1, nowMs = 4_000))
        val line = diagnostics.sample(parts = 1, nowMs = 6_000)!!
        assertTrue(line.contains("trailing_part_count=1"))
        assertTrue(line.contains("playlist_unchanged_ms=2000"))
    }

    @Test fun `new viewing attempt cannot inherit a previous stalled window`() {
        val diagnostics = HlsPlaybackDiagnostics()
        diagnostics.sample(sequence = 300, nowMs = 1_000)
        diagnostics.reset()
        val line = diagnostics.sample(sequence = 1, nowMs = 50_000)!!
        assertTrue(line.contains("sequence_delta=0"))
        assertTrue(line.contains("playlist_unchanged_ms=0"))
    }

    private fun HlsPlaybackDiagnostics.sample(sequence: Long = 100, parts: Int = 0,
        nowMs: Long, tags: List<String> = emptyList()) =
        observe(sequence, segments = 6, parts, durationUs = 12_000_000, ended = false, tags, nowMs)
}
