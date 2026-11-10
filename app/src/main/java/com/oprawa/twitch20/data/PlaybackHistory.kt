package com.oprawa.twitch20.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded resume positions. IDs only; source URLs and tokens never enter storage. */
class PlaybackHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("playback_history_v1", Context.MODE_PRIVATE)
    private var entries = load()

    @Synchronized fun read(kind: String, id: String): Long =
        entries.lastOrNull { it.kind == kind && it.id == id }?.positionMs ?: 0L

    @Synchronized fun save(kind: String, id: String, positionMs: Long) {
        val updated = HistoryPolicy.save(entries, kind, id, positionMs)
        if (updated == entries) return
        entries = updated
        val json = JSONArray().apply {
            entries.forEach { put(JSONObject().put("kind", it.kind).put("id", it.id).put("positionMs", it.positionMs)) }
        }
        prefs.edit().putString("positions", json.toString()).apply()
    }

    @Synchronized fun readSpeed(id: String): Float =
        if (HistoryPolicy.validId(id)) VodPlaybackSpeed.sanitize(prefs.getFloat("vod_speed_$id", 1f)) else 1f

    @Synchronized fun saveSpeed(id: String, speed: Float) {
        if (!HistoryPolicy.validId(id) || speed !in VodPlaybackSpeed.options) return
        val editor = prefs.edit()
        if (speed == 1f) editor.remove("vod_speed_$id") else editor.putFloat("vod_speed_$id", speed)
        editor.apply()
    }

    private fun load(): List<PlaybackPosition> = runCatching {
        val raw = prefs.getString("positions", "[]").orEmpty()
        if (raw.length > 64_000) return@runCatching emptyList()
        val json = JSONArray(raw)
        var result = emptyList<PlaybackPosition>()
        for (index in 0 until json.length()) {
            val entry = json.optJSONObject(index) ?: continue
            result = HistoryPolicy.save(result, entry.optString("kind"), entry.optString("id"), entry.optLong("positionMs", 0))
        }
        result
    }.getOrDefault(emptyList())
}

internal data class PlaybackPosition(val kind: String, val id: String, val positionMs: Long)

internal object HistoryPolicy {
    private val identifier = Regex("[A-Za-z0-9_-]{1,200}")

    fun validId(id: String): Boolean = identifier.matches(id)

    fun save(current: List<PlaybackPosition>, kind: String, id: String, positionMs: Long): List<PlaybackPosition> {
        if (kind !in setOf("vod", "clip") || !validId(id)) return current
        val remaining = current.filterNot { it.kind == kind && it.id == id }
        return if (positionMs <= 0) remaining else (remaining + PlaybackPosition(kind, id, positionMs)).takeLast(100)
    }
}


internal object VodPlaybackSpeed {
    val options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    fun sanitize(speed: Float): Float = speed.takeIf { it in options } ?: 1f
    fun label(speed: Float): String = speed.toString().removeSuffix(".0") + "×"
}
