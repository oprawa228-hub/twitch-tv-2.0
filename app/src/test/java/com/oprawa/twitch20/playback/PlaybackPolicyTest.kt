package com.oprawa.twitch20.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPolicyTest {
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
