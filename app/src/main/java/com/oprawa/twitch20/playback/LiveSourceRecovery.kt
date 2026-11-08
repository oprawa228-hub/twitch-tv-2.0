package com.oprawa.twitch20.playback

/** One automatic source restart per explicit play/retry. READY does not renew the budget. */
internal class LiveSourceRecovery {
    private var used = false

    fun reset() { used = false }

    fun claim(isLive: Boolean, isPlaylistFailure: Boolean, foreground: Boolean, hasItem: Boolean): Boolean {
        if (used || !isLive || !isPlaylistFailure || !foreground || !hasItem) return false
        used = true
        return true
    }
}

/** Bounded even for cyclic causes. Exception messages can contain signed media URIs. */
internal fun playbackErrorCauses(error: Throwable): List<Throwable> =
    generateSequence(error) { it.cause }.take(8).toList()

internal fun safePlaybackErrorCauseNames(error: Throwable): String =
    playbackErrorCauses(error).joinToString(">") { it.javaClass.simpleName.ifBlank { "Throwable" } }
