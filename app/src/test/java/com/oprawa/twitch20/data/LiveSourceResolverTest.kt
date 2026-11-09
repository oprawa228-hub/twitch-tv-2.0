package com.oprawa.twitch20.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LiveSourceResolverTest {
    @Test fun `preview and fullscreen keep the account and consume the same source once`() = runTest {
        val f = Fixture()
        assertEquals("account:a:kai:1", f.resolver.preview("kai"))
        assertEquals("account:a:kai:1", f.resolver.play("kai"))
        assertEquals(1, f.calls)
        assertEquals("account:a:kai:2", f.resolver.play("kai"))
        assertEquals(0, f.guestCalls)
    }

    @Test fun `different channel account or expired preview requires fresh source`() = runTest {
        val f = Fixture()
        f.resolver.preview("kai")
        assertEquals("account:a:other:2", f.resolver.play("other"))
        f.resolver.preview("kai")
        f.account = "b"
        assertEquals("account:b:kai:4", f.resolver.play("kai"))
        f.resolver.preview("kai")
        f.now += 60_001
        assertEquals("account:b:kai:6", f.resolver.play("kai"))
    }

    @Test fun `logout cannot hand an account source to guest playback`() = runTest {
        val f = Fixture()
        f.resolver.preview("kai")
        f.account = null
        assertEquals("guest:kai:2", f.resolver.play("kai"))
        assertEquals(1, f.guestCalls)
    }

    @Test fun `retry ignores preview and clear invalidates it`() = runTest {
        val f = Fixture()
        f.resolver.preview("kai")
        assertEquals("account:a:kai:2", f.resolver.play("kai", allowPreview = false))
        f.resolver.preview("kai")
        f.resolver.clear()
        assertEquals("account:a:kai:4", f.resolver.play("kai"))
    }

    @Test fun `account failure never falls back to guest or cached source`() = runTest {
        val f = Fixture()
        f.resolver.preview("kai")
        f.failSession = true
        assertTrue(runCatching { f.resolver.play("kai") }.isFailure)
        assertEquals(0, f.guestCalls)
        f.failSession = false
        assertEquals("account:a:kai:2", f.resolver.play("kai"))
    }

    @Test fun `late preview cannot replace a newer preview or resurrect a consumed handoff`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        var calls = 0
        val resolver = LiveSourceResolver(session = { "a" }, guest = { error("Unexpected guest") },
            authenticated = { login, _ ->
                val call = ++calls
                if (call == 1) { firstStarted.complete(Unit); finishFirst.await() }
                "$login:$call"
            }, nowMs = { 0L })
        val first = async { resolver.preview("old") }
        firstStarted.await()
        assertEquals("new:2", resolver.preview("new"))
        finishFirst.complete(Unit)
        first.await()
        assertEquals("new:2", resolver.play("new"))
        assertEquals(2, calls)
        assertEquals("old:3", resolver.play("old"))
    }

    @Test fun `failed authenticated request remains an error`() = runTest {
        var guestCalls = 0
        val resolver = LiveSourceResolver(session = { "a" }, guest = { guestCalls++; "guest" },
            authenticated = { _, _ -> throw TwitchException(TwitchError.ACCESS_DENIED) })
        assertTrue(runCatching { resolver.preview("kai") }.exceptionOrNull() is TwitchException)
        assertTrue(runCatching { resolver.play("kai") }.exceptionOrNull() is TwitchException)
        assertEquals(0, guestCalls)
    }

    @Test fun `cancelled preview cannot leave a source for the next opening`() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val resolver = LiveSourceResolver(session = { "a" }, guest = { error("Unexpected guest") },
            authenticated = { _, _ ->
                val call = ++calls
                if (call == 1) withContext(NonCancellable) { started.complete(Unit); finish.await() }
                "source:$call"
            })
        val preview = async { resolver.preview("kai") }
        started.await()
        preview.cancel()
        finish.complete(Unit)
        preview.join()
        assertEquals("source:2", resolver.play("kai"))
    }

    private class Fixture {
        var account: String? = "a"
        var failSession = false
        var now = 0L
        var calls = 0
        var guestCalls = 0
        val resolver = LiveSourceResolver(session = {
            if (failSession) error("Session unavailable")
            account
        }, guest = { login -> guestCalls++; "guest:$login:${++calls}" },
            authenticated = { login, account -> "account:$account:$login:${++calls}" }, nowMs = { now })
    }
}
