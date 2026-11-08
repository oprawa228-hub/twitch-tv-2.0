package com.oprawa.twitch20.playback

internal object PlaybackPolicy {
    fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val position = positionMs.coerceAtLeast(0)
        val target = when {
            deltaMs > 0 && position > Long.MAX_VALUE - deltaMs -> Long.MAX_VALUE
            deltaMs < 0 && deltaMs < -position -> 0L
            else -> position + deltaMs
        }
        return if (durationMs > 0) target.coerceIn(0, durationMs) else target.coerceAtLeast(0)
    }
}
