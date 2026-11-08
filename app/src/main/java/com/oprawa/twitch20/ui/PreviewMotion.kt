package com.oprawa.twitch20.ui

import kotlin.math.PI
import kotlin.math.sin

internal data class PreviewHeart(val x: Float, val y: Float, val scale: Float, val alpha: Float, val rotation: Float, val variant: Int)
internal data class PreviewFrame(
    val tankX: Float = 0f, val recoil: Float = 0f,
    val playerTilt: Float = 0f, val headsetLift: Float = 0f, val mugTilt: Float = 0f,
    val smokeAlpha: Float = 0f, val smokeScale: Float = 1f,
    val hearts: List<PreviewHeart> = emptyList(),
)

internal object PreviewMotion {
    const val CYCLE_MS = 8000L
    fun canRun(appEnabled: Boolean, resumed: Boolean, visible: Boolean, durationScale: Float): Boolean =
        appEnabled && resumed && visible && durationScale.isFinite() && durationScale > 0f

    fun frame(elapsedMs: Long): PreviewFrame {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        val time = (elapsed % CYCLE_MS).toFloat()
        val tank = 13f * ramp(time, 1200f, 2100f) * (1f - ramp(time, 5400f, 6500f))
        val recoil = 10f * pulse(time, 2200f, 90f, 320f)
        val smokeProgress = ((time - 2200f) / 600f).coerceIn(0f, 1f)
        val smoke = .65f * pulse(time, 2200f, 120f, 480f)
        val hearts = (0..2).mapNotNull { index ->
            val age = time - (2210f + index * 160f)
            if (age <= 0f || age >= 2300f) return@mapNotNull null
            val progress = age / 2300f
            val size = when (index) { 1 -> .78f; 2 -> .62f; else -> 1f }
            PreviewHeart(
                // Stay anchored at the muzzle, then drift into clear sky left of TOP_RIGHT chat.
                x = 491f + 8f * sin(PI.toFloat() * progress) - 120f * progress,
                y = 189f - 110f * progress - 8f * sin(PI.toFloat() * progress),
                scale = size * (.78f + .22f * easeOut((age / 180f).coerceIn(0f, 1f))),
                alpha = easeOut((age / 120f).coerceIn(0f, 1f)) *
                    (1f - ((age - 1900f) / 400f).coerceIn(0f, 1f)),
                rotation = 10f * sin(progress * PI.toFloat() * 3f + index * .4f),
                variant = index,
            )
        }
        return PreviewFrame(
            tankX = tank,
            recoil = recoil,
            playerTilt = -4f * pulse(time, 2300f, 150f, 580f) + pulse(time, 2910f, 140f, 310f),
            headsetLift = 7f * pulse(time, 2300f, 180f, 600f),
            mugTilt = if ((elapsed / CYCLE_MS) % 3L == 2L) -9f * pulse(time, 4300f, 400f, 850f) else 0f,
            smokeAlpha = smoke,
            smokeScale = if (smoke > 0f) .9f + 1.2f * smokeProgress else 1f,
            hearts = hearts,
        )
    }

    private fun ramp(time: Float, start: Float, end: Float): Float =
        bezier(((time - start) / (end - start)).coerceIn(0f, 1f), .77f, 0f, .175f, 1f)

    private fun pulse(time: Float, start: Float, attack: Float, release: Float): Float {
        val age = time - start
        return when {
            age <= 0f || age >= attack + release -> 0f
            age < attack -> easeOut(age / attack)
            else -> 1f - easeOut((age - attack) / release)
        }
    }

    private fun easeOut(value: Float) = bezier(value, .23f, 1f, .32f, 1f)

    // Shared craft curves from animate: on-screen movement / strong ease-out.
    // Invert x with bounded bisection so this choreography is deterministic and JVM-testable.
    private fun bezier(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        if (x <= 0f || x >= 1f) return x.coerceIn(0f, 1f)
        fun component(t: Float, first: Float, second: Float): Float {
            val rest = 1f - t
            return 3f * rest * rest * t * first + 3f * rest * t * t * second + t * t * t
        }
        var low = 0f
        var high = 1f
        repeat(15) {
            val middle = (low + high) * .5f
            if (component(middle, x1, x2) < x) low = middle else high = middle
        }
        return component((low + high) * .5f, y1, y2).coerceIn(0f, 1f)
    }
}
