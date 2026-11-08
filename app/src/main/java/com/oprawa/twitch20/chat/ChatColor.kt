package com.oprawa.twitch20.chat

import kotlin.math.pow

/** Preserve the sender's hue, lifting dark IRC colours against the app's dark chat surface. */
object ChatColor {
    const val background: Int = 0x0C0D12
    private const val fallback: Int = 0xC6A3FF

    fun readable(value: String): Int {
        val rgb = if (value.matches(Regex("#[0-9a-fA-F]{6}"))) value.drop(1).toInt(16) else fallback
        if (contrast(rgb, background) >= 4.5) return rgb or (0xFF shl 24)
        // Mix toward white without changing the channel ordering (and thus the colour family).
        for (step in 1..100) {
            val amount = step / 100.0
            fun lift(shift: Int): Int {
                val channel = (rgb shr shift) and 255
                return (channel + (255 - channel) * amount).toInt()
            }
            val candidate = (lift(16) shl 16) or (lift(8) shl 8) or lift(0)
            if (contrast(candidate, background) >= 4.5) return candidate or (0xFF shl 24)
        }
        return -1
    }

    internal fun contrast(first: Int, second: Int): Double {
        fun luminance(rgb: Int): Double {
            fun channel(shift: Int): Double {
                val v = ((rgb shr shift) and 255) / 255.0
                return if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val a = luminance(first); val b = luminance(second)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }
}
