package com.oprawa.twitch20.playback

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LiveSourceRecoveryTest {
    @Test fun `only one restart is allowed across repeated READY and failure cycles`() {
        val recovery = LiveSourceRecovery()
        assertTrue(recovery.claim(true, true, true, true))
        repeat(4) { assertFalse(recovery.claim(true, true, true, true)) }
        recovery.reset() // An explicit user play/retry opens a new bounded attempt.
        assertTrue(recovery.claim(true, true, true, true))
    }

    @Test fun `VOD generic IO background and missing item do not consume live recovery`() {
        val recovery = LiveSourceRecovery()
        assertFalse(recovery.claim(false, true, true, true))
        assertFalse(recovery.claim(true, false, true, true))
        assertFalse(recovery.claim(true, true, false, true))
        assertFalse(recovery.claim(true, true, true, false))
        assertTrue(recovery.claim(true, true, true, true))
    }

    @Test fun `cause diagnostics contain only types and never exception messages`() {
        val error = IllegalStateException("https://secret.example/master?token=private",
            IOException("signed-uri-and-account-identifier"))
        assertEquals("IllegalStateException>IOException", safePlaybackErrorCauseNames(error))
    }
}
