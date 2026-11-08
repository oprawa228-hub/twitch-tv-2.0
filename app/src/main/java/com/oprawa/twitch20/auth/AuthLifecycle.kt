package com.oprawa.twitch20.auth

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/** Shared by startup and the long-lived account validator. */
internal suspend fun runAuthValidationLoop(
    validate: suspend () -> Unit,
    retryNeeded: () -> Boolean,
    pause: suspend (Long) -> Unit = { delay(it) },
) {
    validate()
    var waitedSinceValidation = 0L
    while (currentCoroutineContext().isActive) {
        // Account state can change while idle: a new DCF session may be saved
        // after startup. Re-read that state every minute without polling a
        // healthy account's network endpoint more than once an hour.
        pause(60_000L)
        waitedSinceValidation += 60_000L
        if (retryNeeded() || waitedSinceValidation >= 3_600_000L) {
            validate()
            waitedSinceValidation = 0L
        }
    }
}

internal fun needsAuthValidationRetry(hasSavedSession: Boolean, state: AuthState): Boolean =
    hasSavedSession && (!state.loggedIn || state.error != null)

/** Rotation must commit the replacement credentials even if a screen stops waiting. */
internal suspend fun <T> refreshTransaction(operation: suspend () -> T): T {
    currentCoroutineContext().ensureActive()
    return withContext(NonCancellable) {
        try { withTimeout(30_000L) { operation() } }
        catch (_: TimeoutCancellationException) {
            throw AuthException("Не удалось обновить сессию Twitch. Проверьте интернет и повторите.")
        }
    }
}

/** Logout invalidates responses even while a refresh is completing independently of its screen. */
internal class AuthGeneration {
    private val value = AtomicLong(0)
    fun get(): Long = value.get()
    fun incrementAndGet(): Long = value.incrementAndGet()
    fun requireCurrent(epoch: Long) {
        if (value.get() != epoch) throw CancellationException("Authorization session changed")
    }
}
