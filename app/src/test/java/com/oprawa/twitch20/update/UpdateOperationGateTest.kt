package com.oprawa.twitch20.update

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateOperationGateTest {
    @Test fun cancelledQueuedCheckNeverStartsWhenWorkerBecomesFree() {
        val gate = UpdateOperationGate()
        val occupied = CountDownLatch(1)
        val workerEntered = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        val started = AtomicInteger()
        try {
            worker.submit { workerEntered.countDown(); occupied.await() }
            assertTrue(workerEntered.await(2, TimeUnit.SECONDS))
            val queuedToken = gate.issue() // Must exist before waiting for a busy worker.
            val queued = worker.submit<Boolean> { gate.admit(queuedToken) { started.incrementAndGet() } }
            gate.invalidate()
            occupied.countDown()
            assertFalse(queued.get(2, TimeUnit.SECONDS))
            assertEquals(0, started.get())
        } finally { occupied.countDown(); worker.shutdownNow() }
    }

    @Test fun latestQueuedRequestWinsAndOlderRequestCannotPublish() {
        val gate = UpdateOperationGate()
        val first = gate.issue()
        val latest = gate.issue()
        var state = "idle"
        assertFalse(gate.admit(first) { state = "old" })
        assertTrue(gate.admit(latest) { state = "latest" })
        assertEquals("latest", state)
        gate.invalidate { state = "cancelled" }
        assertFalse(gate.admit(latest) { state = "late response" })
        assertEquals("cancelled", state)
    }
}
