package com.oprawa.twitch20.ui

import org.junit.Assert.*
import org.junit.Test

class PreviewMotionTest {
    @Test fun motionRequiresEveryVisibilityAndAccessibilityGate() {
        assertTrue(PreviewMotion.canRun(true, true, true, 1f))
        assertFalse(PreviewMotion.canRun(false, true, true, 1f))
        assertFalse(PreviewMotion.canRun(true, false, true, 1f))
        assertFalse(PreviewMotion.canRun(true, true, false, 1f))
        for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(PreviewMotion.canRun(true, true, true, scale))
        }
    }

    @Test fun quietPoseAtStartAndLoopBoundary() {
        assertEquals(PreviewFrame(), PreviewMotion.frame(-10))
        assertEquals(PreviewFrame(), PreviewMotion.frame(0))
        assertEquals(PreviewFrame(), PreviewMotion.frame(PreviewMotion.CYCLE_MS - 1))
        assertEquals(PreviewFrame(), PreviewMotion.frame(PreviewMotion.CYCLE_MS))
    }

    @Test fun shotHasRecoilAndVisibleHeartsThenSettles() {
        assertTrue(PreviewMotion.frame(2300).recoil > 0f)
        assertTrue(PreviewMotion.frame(2700).hearts.count { it.alpha > 0.5f } >= 2)
        assertTrue(PreviewMotion.frame(2700).headsetLift > 0f)
        assertEquals(0f, PreviewMotion.frame(6000).recoil, 0f)
        assertTrue(PreviewMotion.frame(6000).hearts.isEmpty())
    }

    @Test fun mugToastIsRareAndDeterministic() {
        assertEquals(0f, PreviewMotion.frame(4700).mugTilt, 0f)
        assertEquals(0f, PreviewMotion.frame(PreviewMotion.CYCLE_MS + 4700).mugTilt, 0f)
        assertTrue(PreviewMotion.frame(2 * PreviewMotion.CYCLE_MS + 4700).mugTilt < -5f)
        assertEquals(PreviewMotion.frame(20700), PreviewMotion.frame(20700))
    }

    @Test fun heartsLaunchAtMuzzleThenClearDefaultTopRightChat() {
        for (variant in 0..2) {
            val launch = 2210L + variant * 160L
            val first = PreviewMotion.frame(launch + 1).hearts.single { it.variant == variant }
            assertEquals(491f, first.x, 0.2f)
            assertEquals(189f, first.y, 0.2f)
            // Include the rotated heart's full 22-unit radius, not just its center.
            // From 0.9 s onward the visible flight must clear chat's left edge (~475).
            for (age in 900L..1900L step 50L) {
                val heart = PreviewMotion.frame(launch + age).hearts.single { it.variant == variant }
                assertTrue(heart.alpha > 0.9f)
                assertTrue(heart.x + 22f * heart.scale < 475f)
                assertTrue(heart.y < 160f)
            }
        }
    }

    @Test fun fullChoreographyRemainsInBoundsWithoutLargeFrameJumps() {
        var previous = PreviewMotion.frame(0)
        for (ms in 0L..(3 * PreviewMotion.CYCLE_MS) step 16) {
            val frame = PreviewMotion.frame(ms)
            assertTrue(frame.tankX in 0f..13f)
            assertTrue(frame.recoil in 0f..10f)
            assertTrue(frame.smokeAlpha in 0f..0.65f)
            // Under 0.25% of the 640-unit scene width per 16 ms, including the strong ease curve.
            assertTrue(kotlin.math.abs(frame.tankX - previous.tankX) < 1.5f)
            assertTrue(kotlin.math.abs(frame.mugTilt - previous.mugTilt) < 2f)
            frame.hearts.forEach {
                assertTrue(it.alpha in 0f..1f)
                assertTrue(it.x in 370f..495f)
                assertTrue(it.y in 45f..195f)
                assertTrue(it.scale in 0.4f..1.1f)
            }
            previous = frame
        }
    }
}
