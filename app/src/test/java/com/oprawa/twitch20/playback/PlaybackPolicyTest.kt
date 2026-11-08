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
}
