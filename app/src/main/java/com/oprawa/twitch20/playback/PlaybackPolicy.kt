package com.oprawa.twitch20.playback

internal object PlaybackPolicy {
    fun positionToSave(positionMs: Long, durationMs: Long, ended: Boolean): Long =
        if (ended || (durationMs > 0 && positionMs >= durationMs)) 0L else positionMs.coerceAtLeast(0)

    fun foregroundState(previous: PlaybackState, resumePlayback: Boolean): PlaybackState =
        previous.copy(status = PlaybackStatus.Buffering, error = null,
            isPlaying = false, playWhenReady = resumePlayback)

    fun qualitySwitchState(previous: PlaybackState, id: String, label: String): PlaybackState =
        previous.copy(status = PlaybackStatus.Buffering, error = null, isPlaying = false,
            selectedQualityId = id, activeQuality = label)

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
