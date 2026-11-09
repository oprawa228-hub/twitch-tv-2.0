package com.oprawa.twitch20.playback

/** One bounded grace period for a full Twitch preroll snapshot followed by a shorter,
 * demonstrably growing snapshot at the same sequence. Does not edit or replace snapshots.
 */
internal class LivePlaylistProgressGuard {
    private var sequence = Long.MIN_VALUE
    private var maximumCount = 0
    private var maximumDurationMs = 0L
    private var maximumEligible = false
    private var used = false
    private var beganMs: Long? = null
    private var count = 0
    private var durationMs = 0L
    private var lastProgressMs = 0L
    private var progressSeen = false
    private var freshnessMs = 0L

    @Synchronized fun observe(sequence: Long, segments: Int, durationMs: Long, targetMs: Long,
        startAtZero: Boolean, commercialMetadata: Boolean, ended: Boolean, nowMs: Long) {
        val eligible = startAtZero && commercialMetadata && !ended && targetMs > 0 && segments > 0
        if (this.sequence != sequence) {
            this.sequence = sequence
            maximumCount = segments
            maximumDurationMs = durationMs
            maximumEligible = eligible
            used = false; beganMs = null
            return
        }
        if (ended || segments > maximumCount) {
            maximumCount = segments; maximumDurationMs = durationMs; maximumEligible = eligible
            beganMs = null
            return
        }
        if (!eligible) { beganMs = null; return }
        if (beganMs == null && !used && maximumEligible && segments < maximumCount &&
            durationMs < maximumDurationMs && maximumDurationMs > targetMs * 3.5) {
            used = true; beganMs = nowMs
            count = segments; this.durationMs = durationMs
            lastProgressMs = nowMs; progressSeen = false
            freshnessMs = (targetMs * 2).coerceIn(5_000, 15_000)
            return
        }
        if (beganMs != null) {
            if (segments < count || durationMs < this.durationMs) {
                // A second reset is not evidence of catching up. Never extend the deadline.
                beganMs = null
            } else if (segments > count && durationMs > this.durationMs) {
                count = segments; this.durationMs = durationMs
                lastProgressMs = nowMs; progressSeen = true
            }
        }
    }

    @Synchronized fun shouldDefer(nowMs: Long): Boolean {
        val began = beganMs ?: return false
        return progressSeen && nowMs >= began && nowMs - began < 60_000 &&
            nowMs >= lastProgressMs && nowMs - lastProgressMs <= freshnessMs
    }
}
