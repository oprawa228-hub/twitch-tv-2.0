package com.oprawa.twitch20.data

import org.junit.Assert.*
import org.junit.Test

class PlaybackHistoryTest {
    @Test fun adding101stVideoEvictsOldestOnly() {
        val current = (1..100).map { PlaybackPosition("vod", it.toString(), it * 1000L) }
        val result = HistoryPolicy.save(current, "vod", "101", 5000)
        assertEquals(100, result.size)
        assertFalse(result.any { it.id == "1" })
        assertEquals("2", result.first().id)
        assertEquals("101", result.last().id)
    }

    @Test fun updatingExistingVideoRefreshesRecencyWithoutDuplicate() {
        val current = listOf(PlaybackPosition("vod", "1", 1000), PlaybackPosition("vod", "2", 2000))
        val result = HistoryPolicy.save(current, "vod", "1", 9000)
        assertEquals(2, result.size)
        assertEquals("2", result.first().id)
        assertEquals(PlaybackPosition("vod", "1", 9000), result.last())
    }

    @Test fun liveAndSignedUrlAreNeverPersisted() {
        assertTrue(HistoryPolicy.save(emptyList(), "live", "channel", 1000).isEmpty())
        assertTrue(HistoryPolicy.save(emptyList(), "vod", "https://example.test/video?token=secret", 1000).isEmpty())
    }

    @Test fun zeroPositionClearsOldResumePoint() {
        val current = listOf(PlaybackPosition("clip", "slug", 3000))
        assertTrue(HistoryPolicy.save(current, "clip", "slug", 0).isEmpty())
    }
}
