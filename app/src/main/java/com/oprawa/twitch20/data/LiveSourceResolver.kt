package com.oprawa.twitch20.data

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Source resolution only; never owns a player or persists signed URLs. */
internal class LiveSourceResolver<A : Any>(
    private val session: suspend () -> A?,
    private val guest: suspend (String) -> String,
    private val authenticated: suspend (String, A) -> String,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onEvent: (String) -> Unit = {},
) {
    // One short-lived handoff only. No disk cache, token/URL logging or player sharing.
    private class Handoff<A>(val login: String, val account: A?, val source: String, val createdAtMs: Long)
    private val lock = Any()
    private var generation = 0L
    private var handoff: Handoff<A>? = null

    suspend fun preview(login: String): String {
        val attempt = synchronized(lock) { handoff = null; ++generation }
        val account = session()
        val source = resolve(login, account)
        coroutineContext.ensureActive()
        synchronized(lock) {
            if (attempt == generation) handoff = Handoff(login, account, source, nowMs())
        }
        onEvent("preview_source_ready")
        return source
    }

    suspend fun play(login: String, allowPreview: Boolean = true): String {
        val candidate = synchronized(lock) {
            ++generation
            handoff.also { handoff = null }
        }
        // Validate the current identity even when a preview is available. An auth failure
        // must never reuse its old URL or silently turn the request into guest playback.
        val account = session()
        coroutineContext.ensureActive()
        if (allowPreview && candidate != null && candidate.login == login && candidate.account == account &&
            nowMs() - candidate.createdAtMs in 0..60_000) {
            onEvent("preview_source_handoff")
            return candidate.source
        }
        onEvent("fullscreen_source_fresh")
        return resolve(login, account)
    }

    fun clear() { synchronized(lock) { ++generation; handoff = null } }

    private suspend fun resolve(login: String, account: A?): String {
        return if (account == null) guest(login) else authenticated(login, account)
    }
}
