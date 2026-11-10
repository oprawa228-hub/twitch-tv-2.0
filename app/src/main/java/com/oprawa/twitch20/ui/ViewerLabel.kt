package com.oprawa.twitch20.ui

internal fun viewerLabel(count: Int): String {
    val value = count.coerceAtLeast(0)
    val noun = when {
        value % 100 in 11..14 -> "зрителей"
        value % 10 == 1 -> "зритель"
        value % 10 in 2..4 -> "зрителя"
        else -> "зрителей"
    }
    return "$value $noun"
}
