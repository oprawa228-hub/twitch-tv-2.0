package com.oprawa.twitch20.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FollowingMonitorTest {
    private fun channel(id: String, stream: String, live: Boolean = true) =
        Channel(id,id,id,"","","",live = live,liveStreamId = stream)

    @Test fun initialLiveListIsSilentAndOnlyNewStreamIdentityNotifies() {
        val tracker = LiveStartTracker()
        assertTrue(tracker.update(listOf(channel("a","1"))).isEmpty())
        assertTrue(tracker.update(listOf(channel("a","1").copy(viewers=20))).isEmpty())
        assertEquals(listOf("b"), tracker.update(listOf(channel("a","1"),channel("b","2"))).map { it.id })
        assertTrue(tracker.update(listOf(channel("a","",false))).isEmpty())
        assertTrue(tracker.update(listOf(channel("b","2"))).isEmpty())
        assertEquals("3", tracker.update(listOf(channel("b","3"))).single().liveStreamId)
    }

    @Test fun longRunningStreamDoesNotNotifyAgainWhenOldStartsAreEvicted() {
        val tracker = LiveStartTracker()
        val continuous = channel("continuous", "one")
        tracker.update(listOf(continuous))
        repeat(4_005) { index ->
            val starts = tracker.update(listOf(continuous, channel("other", "$index")))
            assertEquals(listOf("other"), starts.map { it.id })
        }
        assertTrue(tracker.update(listOf(continuous)).isEmpty())
    }

    @Test fun transientFailureKeepsChannelsAndNotificationBaseline() = runTest {
        var fail = false
        val monitor = FollowingMonitor { if (fail) error("private transport details") else listOf(channel("a","1")) }
        val starts = mutableListOf<LiveStart>()
        val collector = backgroundScope.launch { monitor.starts.collect { starts.add(it) } }
        runCurrent(); monitor.refresh(); fail=true; monitor.refresh()
        assertEquals("a",monitor.state.value.channels.single().id)
        assertNotNull(monitor.state.value.error)
        assertFalse(monitor.state.value.error!!.contains("private"))
        fail=false; monitor.refresh(); runCurrent()
        assertTrue(starts.isEmpty()); assertNull(monitor.state.value.error); collector.cancel()
    }

    @Test fun concurrentRefreshDoesNotDuplicateLoadAndCancellationReleasesOwnership() = runTest {
        var calls=0
        val gate=CompletableDeferred<Unit>()
        val monitor=FollowingMonitor { calls++;gate.await();emptyList() }
        val job=launch { monitor.refresh() };runCurrent()
        monitor.refresh();assertEquals(1,calls)
        job.cancel();runCurrent();assertFalse(monitor.state.value.loading)
        gate.complete(Unit);monitor.refresh();assertEquals(2,calls)
    }
}
