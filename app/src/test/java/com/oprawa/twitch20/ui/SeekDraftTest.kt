package com.oprawa.twitch20.ui

import org.junit.Assert.*
import org.junit.Test

class SeekDraftTest {
    @Test fun previewUsesCapturedPositionUntilConfirmation() {
        val draft = SeekDraft().begin(35_000, 300_000).move(1)
        assertEquals(45_000L, draft.targetMs)
        // A player position update must not reset a user's in-progress target.
        assertEquals(draft, draft.begin(38_000, 300_000))
    }

    @Test fun cancelDiscardsTargetAndNextSeekStartsAtCurrentPlaybackPosition() {
        val cancelled = SeekDraft().begin(35_000, 300_000).move(1).cancel()
        assertFalse(cancelled.active)
        assertNull(cancelled.confirm().targetMs)
        assertEquals(42_000L, cancelled.begin(42_000, 300_000).targetMs)
    }

    @Test fun consumingConfirmationMakesRepeatedOkAnoop() {
        val result = SeekDraft().begin(35_000, 300_000).move(-1).confirm()
        assertEquals(25_000L, result.targetMs)
        assertFalse(result.next.active)
        assertNull(result.next.confirm().targetMs)
    }

    @Test fun accelerationUsesCurrentPressDurationAndResetsOnNewPress() {
        var draft = SeekDraft().begin(0, 900_000)
        draft = draft.move(1, 1_499)
        assertEquals(10_000L, draft.targetMs)
        draft = draft.move(1, 1_500)
        assertEquals(40_000L, draft.targetMs)
        draft = draft.move(1, 3_999)
        assertEquals(70_000L, draft.targetMs)
        draft = draft.move(1, 4_000)
        assertEquals(130_000L, draft.targetMs)
        draft = draft.move(-1, 0)
        assertEquals(120_000L, draft.targetMs)
    }

    @Test fun seekingClampsToBothEndsIncludingVeryShortClips() {
        assertEquals(0L, SeekDraft().begin(2_000, 5_000).move(-1, 5_000).targetMs)
        assertEquals(5_000L, SeekDraft().begin(2_000, 5_000).move(1, 5_000).targetMs)
        assertEquals(5_000L, SeekDraft().begin(5_000, 5_000).move(1).targetMs)
    }

    @Test fun invalidDurationCannotCreateASeekTarget() {
        for (duration in listOf(0L, -1L, Long.MIN_VALUE)) {
            val draft = SeekDraft().begin(35_000, duration).move(1)
            assertFalse(draft.active)
            assertNull(draft.confirm().targetMs)
        }
        assertNull(SeekDraft().move(1).targetMs)
    }

    @Test fun initialPositionIsBoundedAndPositiveStepCannotOverflow() {
        assertEquals(0L, SeekDraft().begin(-1, 300_000).targetMs)
        assertEquals(300_000L, SeekDraft().begin(400_000, 300_000).targetMs)
        assertEquals(Long.MAX_VALUE,
            SeekDraft().begin(Long.MAX_VALUE - 5_000, Long.MAX_VALUE).move(1, 5_000).targetMs)
    }

    @Test fun neutralDirectionDoesNotChangeTarget() {
        val draft = SeekDraft().begin(35_000, 300_000)
        assertEquals(draft, draft.move(0, 10_000))
    }
}
