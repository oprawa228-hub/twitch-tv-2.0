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
    while (currentCoroutineContext().isActive) {
        pause(if (retryNeeded()) 60_000L else 3_600_000L)
        validate()
    }
}

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
