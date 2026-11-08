package com.oprawa.twitch20.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthLifecycleTest {
    @Test fun logoutPreventsALateRotationFromRestoringCredentials() = runTest {
        val generation = AuthGeneration()
        val epoch = generation.get()
        val response = CompletableDeferred<String>()
        var persisted: String? = "old-pair"
        val request = launch {
            refreshTransaction {
                val replacement = response.await()
                generation.requireCurrent(epoch)
                persisted = replacement
            }
        }
        runCurrent()
        generation.incrementAndGet()
        persisted = null
        response.complete("rotated-pair")
        request.join()
        assertNull(persisted)
    }

    @Test fun leavingTheScreenStillPersistsTheAcceptedRefreshResponse() = runTest {
        val serverAccepted = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        var persisted = "old-pair"
        val request = launch {
            refreshTransaction {
                serverAccepted.complete(Unit)
                val replacement = response.await()
                persisted = replacement
            }
        }
        serverAccepted.await()
        request.cancel()
        runCurrent()
        response.complete("rotated-pair")
        request.join()
        assertEquals("rotated-pair", persisted)
    }

    @Test fun unresponsiveRefreshEndsWithinTheHttpDeadline() = runTest {
        var finished = false
        var failure: Throwable? = null
        val request = launch {
            try { failure = runCatching { refreshTransaction<Unit> { awaitCancellation() } }.exceptionOrNull() }
            finally { finished = true }
        }
        runCurrent()
        advanceTimeBy(30_001)
        runCurrent()
        val finishedByDeadline = finished
        request.cancelAndJoin()
        assertTrue("Refresh must be bounded even while its caller is cancelled", finishedByDeadline)
        assertTrue("A timeout is retryable, not cancellation of the hourly validator", failure is AuthException)
    }

    @Test fun savedSessionRetriesAfterOfflineStartThenReturnsToHourlyValidation() = runTest {
        var savedSession = true
        var offline = true
        var loggedIn = false
        var attempts = 0
        val validator = launch {
            runAuthValidationLoop(
                validate = { attempts++; if (!offline) loggedIn = true },
                retryNeeded = { savedSession && !loggedIn },
            )
        }
        runCurrent()
        assertEquals(1, attempts)
        offline = false
        advanceTimeBy(60_000)
        runCurrent()
        val recovered = loggedIn
        val attemptsAfterRecovery = attempts
        advanceTimeBy(3_599_999)
        runCurrent()
        val attemptsBeforeHourly = attempts
        advanceTimeBy(1)
        runCurrent()
        val attemptsAfterHourly = attempts
        savedSession = false
        validator.cancelAndJoin()
        assertTrue("An existing account must recover without a new device login", recovered)
        assertEquals(2, attemptsAfterRecovery)
        assertEquals(2, attemptsBeforeHourly)
        assertEquals(3, attemptsAfterHourly)
    }

    @Test fun anEmptyVaultDoesNotPollEveryMinute() = runTest {
        var attempts = 0
        val validator = launch { runAuthValidationLoop({ attempts++ }, { false }) }
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        validator.cancelAndJoin()
        assertEquals(1, attempts)
    }
}
