package com.oprawa.twitch20.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun `resume follows the tail of a growing window instead of its pinned start`() {
        assertEquals(116_000L, PlaybackPolicy.liveEdgeTarget(40_000, 120_000, 120_000,
            (0L until 120_000L step 2_000L).toList()))
    }

    @Test fun `resume cannot replay older content when already ahead of safe tail`() {
        assertNull(PlaybackPolicy.liveEdgeTarget(118_000, 120_000, 120_000,
            (0L until 120_000L step 2_000L).toList()))
        assertNull(PlaybackPolicy.liveEdgeTarget(30_101, 6_000, 6_000, listOf(0, 2_000, 4_000)))
    }

    @Test fun `unknown mismatched or malformed snapshots never imply seek to zero`() {
        assertNull(PlaybackPolicy.liveEdgeTarget(0, -1, 6_000, listOf(0, 2_000, 4_000)))
        assertNull(PlaybackPolicy.liveEdgeTarget(0, 8_000, 6_000, listOf(0, 2_000, 4_000)))
        assertNull(PlaybackPolicy.liveEdgeTarget(0, 6_000, 6_000, emptyList()))
        assertNull(PlaybackPolicy.liveEdgeTarget(0, 6_000, 6_000, listOf(0, 4_000, 2_000)))
    }

    @Test fun `rewind cannot seek before start`() {
        assertEquals(0L, PlaybackPolicy.seekTarget(4_000, -10_000, 90_000))
    }
    @Test fun `forward cannot seek beyond duration`() {
        assertEquals(90_000L, PlaybackPolicy.seekTarget(86_000, 10_000, 90_000))
    }
    @Test fun `unknown duration does not block a valid seek`() {
        assertEquals(32_000L, PlaybackPolicy.seekTarget(22_000, 10_000, -1))
    }
    @Test fun `large seek does not overflow into a negative timestamp`() {
        assertEquals(90_000L, PlaybackPolicy.seekTarget(80_000, Long.MAX_VALUE, 90_000))
    }

    @Test fun `completed recording clears its resume position`() {
        assertEquals(0L, PlaybackPolicy.positionToSave(30_000, 30_000, ended = true))
        assertEquals(0L, PlaybackPolicy.positionToSave(29_999, 30_000, ended = true))
    }

    @Test fun `position at or beyond known end cannot reopen at the end`() {
        assertEquals(0L, PlaybackPolicy.positionToSave(30_000, 30_000, ended = false))
        assertEquals(0L, PlaybackPolicy.positionToSave(35_000, 30_000, ended = false))
    }

    @Test fun `unfinished recording retains position even before duration is known`() {
        assertEquals(29_000L, PlaybackPolicy.positionToSave(29_000, 30_000, ended = false))
        assertEquals(29_000L, PlaybackPolicy.positionToSave(29_000, -1, ended = false))
        assertEquals(0L, PlaybackPolicy.positionToSave(-1, 30_000, ended = false))
    }
}
