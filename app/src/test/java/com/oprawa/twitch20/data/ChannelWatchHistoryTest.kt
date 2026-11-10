package com.oprawa.twitch20.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelWatchHistoryTest {
    @Test fun rankingUsesTotalWatchTimeAndExpiresOldDays() {
        val current = listOf(
            ChannelWatchDay("expired", 70, 50_000),
            ChannelWatchDay("alpha", 71, 1_000),
            ChannelWatchDay("alpha", 99, 2_000),
            ChannelWatchDay("bravo", 100, 2_000),
        )
        val updated = ChannelWatchPolicy.record(current, "bravo", 500, today = 100)
        assertEquals(listOf("alpha", "bravo"), ChannelWatchPolicy.rank(updated))
        assertEquals(2_500L, updated.single { it.login == "bravo" }.elapsedMs)
    }

    @Test fun loginNormalizationRejectsUrlsAndAllowsMixedCase() {
        assertEquals("alpha_42", ChannelWatchPolicy.normalize("Alpha_42"))
        assertNull(ChannelWatchPolicy.normalize("https://twitch.tv/alpha_42"))
    }
}
