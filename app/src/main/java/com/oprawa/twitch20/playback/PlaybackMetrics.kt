package com.oprawa.twitch20.playback

/** Reasons excluded from network rebuffer totals are still measured separately. */
internal enum class BufferingReason { STARTUP, REBUFFER, SEEK, FOREGROUND, RECOVERY, QUALITY_SWITCH }

internal data class PlaybackMetricsSnapshot(
    val attemptId: Int,
    val sourceResolveMs: Long,
    val requestFirstFrameMs: Long?,
    val prepareFirstFrameMs: Long?,
    val rebufferCount: Int,
    val bufferingMs: Map<BufferingReason, Long>,
) {
    val rebufferDurationMs: Long get() = bufferingMs[BufferingReason.REBUFFER] ?: 0
}

/** Monotonic timestamps and counters only. No source URLs, content names or account data. */
internal class PlaybackMetrics {
    private var attemptId = 0
    private var requestStart = 0L
    private var prepareStart = 0L
    private var firstFrameAt: Long? = null
    private var pendingPreparation: BufferingReason? = null
    private var activeReason: BufferingReason? = null
    private var activeStart = 0L
    private var rebufferCount = 0
    private val durations = mutableMapOf<BufferingReason, Long>()

    fun beginAttempt(requestStartedAtMs: Long, preparedAtMs: Long) {
        attemptId++
        requestStart = requestStartedAtMs
        prepareStart = preparedAtMs
        firstFrameAt = null
        activeReason = null
        rebufferCount = 0
        durations.clear()
        pendingPreparation = BufferingReason.STARTUP
    }

    fun firstFrame(nowMs: Long) {
        if (attemptId > 0 && firstFrameAt == null) firstFrameAt = nowMs
    }

    fun markPreparation(reason: BufferingReason, nowMs: Long) {
        if (attemptId == 0) return
        // Initial resume-position seeks and foreground/live recovery seeks are part
        // of that preparation, not independent user seeks.
        if (reason == BufferingReason.SEEK && pendingPreparation != null) return
        finish(nowMs)
        pendingPreparation = reason
    }

    fun onState(status: PlaybackStatus, playWhenReady: Boolean, foreground: Boolean, nowMs: Long) {
        if (attemptId == 0) return
        if (status == PlaybackStatus.Ready) {
            finish(nowMs)
            pendingPreparation = null
            return
        }
        if (status != PlaybackStatus.Buffering || !playWhenReady || !foreground) {
            finish(nowMs)
            return
        }
        val reason = pendingPreparation ?: if (firstFrameAt == null) BufferingReason.STARTUP else BufferingReason.REBUFFER
        if (activeReason == reason) return
        finish(nowMs)
        activeReason = reason
        activeStart = nowMs
        if (reason == BufferingReason.REBUFFER) rebufferCount++
    }

    fun finish(nowMs: Long) {
        activeReason?.let { durations[it] = (durations[it] ?: 0L) + (nowMs - activeStart).coerceAtLeast(0) }
        activeReason = null
    }

    fun snapshot(nowMs: Long): PlaybackMetricsSnapshot {
        val elapsed = BufferingReason.entries.associateWith { durations[it] ?: 0L }.toMutableMap()
        activeReason?.let { elapsed[it] = elapsed.getValue(it) + (nowMs - activeStart).coerceAtLeast(0) }
        return PlaybackMetricsSnapshot(attemptId, (prepareStart - requestStart).coerceAtLeast(0),
            firstFrameAt?.let { (it - requestStart).coerceAtLeast(0) },
            firstFrameAt?.let { (it - prepareStart).coerceAtLeast(0) }, rebufferCount, elapsed)
    }
}
