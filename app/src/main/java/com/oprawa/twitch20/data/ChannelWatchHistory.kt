package com.oprawa.twitch20.data

import android.content.Context
import org.json.JSONArray
import java.security.MessageDigest
import java.util.Locale

/** Local playback time for one account. Only validated channel logins and daily totals are stored. */
class ChannelWatchHistory(context: Context, accountKey: String) {
    private val prefs = context.applicationContext.getSharedPreferences("channel_watch_history_v1", Context.MODE_PRIVATE)
    private val key = MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun record(login: String, elapsedMs: Long) {
        val normalized = ChannelWatchPolicy.normalize(login) ?: return
        if (elapsedMs <= 0) return
        val today = ChannelWatchPolicy.day(System.currentTimeMillis())
        synchronized(storageLock) {
            val current = load(today)
            val updated = ChannelWatchPolicy.record(current, normalized, elapsedMs, today)
            if (updated == current) return
            val json = JSONArray()
            updated.forEach { json.put(JSONArray().put(it.login).put(it.day).put(it.elapsedMs)) }
            prefs.edit().putString(key, json.toString()).apply()
        }
    }

    fun rankedLogins(): List<String> = synchronized(storageLock) {
        ChannelWatchPolicy.rank(load(ChannelWatchPolicy.day(System.currentTimeMillis())))
    }

    private fun load(today: Long): List<ChannelWatchDay> = runCatching {
        val raw = prefs.getString(key, "[]").orEmpty()
        if (raw.length > 384_000) return@runCatching emptyList()
        val json = JSONArray(raw)
        buildList {
            for (index in 0 until json.length()) {
                val entry = json.optJSONArray(index) ?: continue
                val login = ChannelWatchPolicy.normalize(entry.optString(0)) ?: continue
                val day = entry.optLong(1, -1)
                val elapsedMs = entry.optLong(2, 0)
                if (day in today - 29..today && elapsedMs in 1..ChannelWatchPolicy.DAY_MS) {
                    add(ChannelWatchDay(login, day, elapsedMs))
                }
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        val storageLock = Any()
    }
}

internal data class ChannelWatchDay(val login: String, val day: Long, val elapsedMs: Long)

internal object ChannelWatchPolicy {
    const val DAY_MS = 86_400_000L
    private const val MAX_CHANNELS_PER_DAY = 200
    private val loginPattern = Regex("[a-z0-9_]{1,25}")

    fun normalize(login: String): String? = login.lowercase(Locale.ROOT).takeIf(loginPattern::matches)
    fun day(timeMs: Long): Long = timeMs / DAY_MS

    fun record(current: List<ChannelWatchDay>, login: String, elapsedMs: Long, today: Long): List<ChannelWatchDay> {
        val previousMs = current.firstOrNull { it.day == today && it.login == login }?.elapsedMs ?: 0
        val todayEntries = current.filter { it.day == today && it.login != login } +
            ChannelWatchDay(login, today, (previousMs + elapsedMs.coerceAtMost(DAY_MS)).coerceAtMost(DAY_MS))
        return current.filter { it.day in today - 29 until today } +
            todayEntries.sortedWith(compareByDescending<ChannelWatchDay> { it.elapsedMs }.thenBy { it.login })
                .take(MAX_CHANNELS_PER_DAY)
    }

    fun rank(entries: List<ChannelWatchDay>): List<String> =
        entries.groupBy { it.login }.entries.sortedWith(
            compareByDescending<Map.Entry<String, List<ChannelWatchDay>>> { entry -> entry.value.sumOf { it.elapsedMs } }
                .thenByDescending { entry -> entry.value.maxOf { it.day } }
                .thenBy { it.key }
        ).map { it.key }
}
