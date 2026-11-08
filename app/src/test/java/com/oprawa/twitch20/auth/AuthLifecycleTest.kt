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
    @Test fun cancellingLoginValidationStillRecoversAlreadySavedCredentials() = runTest {
        var savedSession = false
        var state = AuthState()
        val validator = launch {
            runAuthValidationLoop(
                validate = { if (savedSession) state = AuthState(loggedIn = true, displayName = "viewer") },
                retryNeeded = { needsAuthValidationRetry(savedSession, state) },
            )
        }
        runCurrent()
        val login = launch {
            savedSession = true // DCF exchange completed and persisted before final validation.
            awaitCancellation() // Leaving the account screen cancels this validation without an error.
        }
        runCurrent()
        login.cancelAndJoin()
        assertFalse(state.loggedIn)
        assertNull(state.error)
        advanceTimeBy(60_000)
        runCurrent()
        validator.cancelAndJoin()
        assertTrue("Saved credentials must be validated even when cancellation produced no error", state.loggedIn)
    }

    @Test fun aNewSessionRetriesAfterLoginValidationFailsWhileTheValidatorIsIdle() = runTest {
        var savedSession = false
        var retryNeeded = false
        var loggedIn = false
        var attempts = 0
        val validator = launch {
            runAuthValidationLoop(
                validate = {
                    attempts++
                    if (savedSession) { loggedIn = true; retryNeeded = false }
                },
                retryNeeded = { savedSession && retryNeeded },
            )
        }
        runCurrent() // Startup has no saved account; the validator enters its idle interval.
        advanceTimeBy(5_000)
        // The device-code exchange saves a session, but its immediate validate fails offline.
        // These are the same signals written by awaitLogin's save and exception handler.
        savedSession = true
        retryNeeded = true
        // The network is available for the next validation; no new login is requested.
        advanceTimeBy(60_000)
        runCurrent()
        val recoveredWithoutNewLogin = loggedIn
        val attemptsAfterRecovery = attempts
        validator.cancelAndJoin()
        assertTrue("A session saved after startup must recover within a minute", recoveredWithoutNewLogin)
        assertEquals(2, attemptsAfterRecovery)
    }

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
