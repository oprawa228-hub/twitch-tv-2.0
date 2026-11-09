package com.oprawa.twitch20.playback

import org.junit.Assert.*
import org.junit.Test

class LivePlaylistProgressGuardTest {
    private fun LivePlaylistProgressGuard.sample(count: Int, now: Long, marker: Boolean = true,
        sequence: Long = 0, ended: Boolean = false, start: Boolean = true) = observe(
        sequence, count, count * 2_000L, 5_000, start, marker, ended, now)

    @Test fun `only a regressed window with subsequent progress gets grace`() {
        val guard = LivePlaylistProgressGuard()
        guard.sample(16, 0); guard.sample(5, 5_000)
        assertFalse(guard.shouldDefer(5_000))
        guard.sample(7, 10_000)
        assertTrue(guard.shouldDefer(10_000))
        guard.sample(16, 28_000)
        assertTrue(guard.shouldDefer(30_000))
        guard.sample(17, 33_000)
        assertFalse("Tracker can accept the newer snapshot normally", guard.shouldDefer(33_000))
    }

    @Test fun `frozen response loses grace even while HTTP polling succeeds`() {
        val guard = LivePlaylistProgressGuard()
        guard.sample(16, 0); guard.sample(5, 5_000); guard.sample(6, 7_500)
        guard.sample(6, 17_000)
        assertFalse(guard.shouldDefer(17_501))
    }

    @Test fun `absolute deadline cannot be extended by continued progress`() {
        val guard = LivePlaylistProgressGuard()
        guard.sample(100, 0); guard.sample(5, 5_000)
        (1..12).forEach { guard.sample(5 + it, 5_000L + it * 5_000) }
        assertFalse(guard.shouldDefer(65_000))
    }

    @Test fun `a second regression never renews the first grace`() {
        val guard = LivePlaylistProgressGuard()
        guard.sample(16, 0); guard.sample(5, 5_000); guard.sample(7, 10_000)
        guard.sample(4, 12_000); guard.sample(8, 15_000)
        assertFalse(guard.shouldDefer(15_000))
    }

    @Test fun `unmarked normal and ended playlists retain default errors`() {
        for (marker in listOf(false, true)) {
            val guard = LivePlaylistProgressGuard()
            guard.sample(16, 0, marker); guard.sample(16, 10_000, marker)
            assertFalse(guard.shouldDefer(20_000))
        }
        val unmarked = LivePlaylistProgressGuard()
        unmarked.sample(16, 0, marker = false); unmarked.sample(5, 5_000, marker = false); unmarked.sample(7, 10_000, marker = false)
        assertFalse(unmarked.shouldDefer(10_000))
        val ended = LivePlaylistProgressGuard()
        ended.sample(16, 0); ended.sample(5, 5_000); ended.sample(7, 10_000, ended = true)
        assertFalse(ended.shouldDefer(10_000))
    }

    @Test fun `sequence change and nonzero start do not match this defect`() {
        val changed = LivePlaylistProgressGuard()
        changed.sample(16, 0); changed.sample(5, 5_000, sequence = 1); changed.sample(7, 10_000, sequence = 1)
        assertFalse(changed.shouldDefer(10_000))
        val offset = LivePlaylistProgressGuard()
        offset.sample(16, 0, start = false); offset.sample(5, 5_000, start = false); offset.sample(7, 10_000, start = false)
        assertFalse(offset.shouldDefer(10_000))
    }
}
