package com.oprawa.twitch20.ui

import com.oprawa.twitch20.data.VodChatPage
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VodChatPageRetryTest {
    @Test fun timeoutAndTemporaryFailureRecoverTheSamePage() = runTest {
        val attempts = mutableListOf<Long>()
        val retryStatus = mutableListOf<Long>()
        val page = VodChatPage(emptyList(), hasMore = true, throughMs = 12_000)

        val result = loadVodChatPageWithRetry(onRetry = { retryStatus += currentTime }) {
            attempts += currentTime
            if (attempts.size == 1) delay(8_000)
            if (attempts.size == 2) throw IOException("Temporary network failure")
            page
        }

        assertSame(page, result)
        assertEquals(listOf(0L, 8_000L, 10_000L), attempts)
        assertEquals(listOf(7_000L, 8_000L), retryStatus)
    }

    @Test fun persistentFailureStopsAfterCappedBackoff() = runTest {
        val attempts = mutableListOf<Long>()

        val result = loadVodChatPageWithRetry(onRetry = {}) {
            attempts += currentTime
            throw IOException("Network unavailable")
        }

        assertNull(result)
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L, 15_000L), attempts)
    }
}
