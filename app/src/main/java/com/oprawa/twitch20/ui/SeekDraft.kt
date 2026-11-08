package com.oprawa.twitch20.ui

/**
 * A position preview, separate from the player's actual position.
 *
 * Callers replace their state with [SeekConfirmation.next] before applying its target to the
 * player. A second confirmation then has no target, so repeated OK events cannot seek twice.
 * No playback operation (including pause or resume) is needed to create or cancel a preview.
 */
data class SeekDraft private constructor(val targetMs: Long?, val durationMs: Long) {
    constructor() : this(null, 0)

    val active: Boolean get() = targetMs != null

    /** An unknown/invalid duration does not offer a misleading seekable timeline. */
    fun begin(positionMs: Long, durationMs: Long): SeekDraft = when {
        active -> this
        durationMs <= 0 -> this
        else -> SeekDraft(positionMs.coerceIn(0, durationMs), durationMs)
    }

    /**
     * [heldMs] is the duration of the current key press, not time spent in the seek UI.
     * A new press therefore starts at ten seconds again; there is no independent repeat timer
     * that could keep moving the target after the key is released.
     */
    fun move(direction: Int, heldMs: Long = 0): SeekDraft {
        val current = targetMs ?: return this
        val step = when {
            heldMs >= 4_000 -> 60_000L
            heldMs >= 1_500 -> 30_000L
            else -> 10_000L
        }
        val target = when {
            direction > 0 -> current + minOf(step, durationMs - current)
            direction < 0 -> (current - step).coerceAtLeast(0)
            else -> current
        }
        return copy(targetMs = target)
    }

    fun cancel(): SeekDraft = SeekDraft()

    fun confirm(): SeekConfirmation = SeekConfirmation(next = SeekDraft(), targetMs = targetMs)
}

data class SeekConfirmation(val next: SeekDraft, val targetMs: Long?)
