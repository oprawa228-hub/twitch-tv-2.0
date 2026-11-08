package com.oprawa.twitch20.playback

internal object PlaybackPolicy {
    /** An explicit return to live follows available complete segments, not EXT-X-START. */
    fun liveEdgeTarget(positionMs: Long, timelineDurationMs: Long, playlistDurationMs: Long,
        segmentStartsMs: List<Long>): Long? {
        if (timelineDurationMs <= 0 || timelineDurationMs != playlistDurationMs || segmentStartsMs.isEmpty()) return null
        if (segmentStartsMs.any { it < 0 || it >= playlistDurationMs } ||
            segmentStartsMs.zipWithNext().any { (previous, next) -> next <= previous }) return null
        val target = segmentStartsMs[(segmentStartsMs.size - 2).coerceAtLeast(0)]
        // A stale/slate manifest may describe less media than the decoder has played.
        // Neither resume nor the live button should replay old segments in that case.
        return target.takeIf { it > positionMs.coerceAtLeast(0) }
    }

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
