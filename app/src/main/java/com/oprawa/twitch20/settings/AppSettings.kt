package com.oprawa.twitch20.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChatMode { OVERLAY, THEATER }
enum class ChatPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
data class AppSettings(
    val chatEnabled: Boolean = true,
    val chatMode: ChatMode = ChatMode.OVERLAY,
    val chatTextSizeSp: Int = 16,
    val chatOpacity: Float = .65f,
    val chatPosition: ChatPosition = ChatPosition.TOP_RIGHT,
    val showClock: Boolean = true,
    val animationsEnabled: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private fun read() = AppSettings(
        chatEnabled = prefs.getBoolean("chatEnabled", true),
        chatMode = runCatching { ChatMode.valueOf(prefs.getString("chatMode", null) ?: "OVERLAY") }.getOrDefault(ChatMode.OVERLAY),
        chatTextSizeSp = prefs.getInt("chatTextSizeSp", 16).coerceIn(12, 24),
        chatOpacity = prefs.getFloat("chatOpacity", .65f).coerceIn(0f, 1f),
        chatPosition = runCatching { ChatPosition.valueOf(prefs.getString("chatPosition", null) ?: "TOP_RIGHT") }.getOrDefault(ChatPosition.TOP_RIGHT),
        showClock = prefs.getBoolean("showClock", true),
        animationsEnabled = prefs.getBoolean("animationsEnabled", true),
    )
    fun save(value: AppSettings) {
        val safe = value.copy(chatTextSizeSp = value.chatTextSizeSp.coerceIn(12,24), chatOpacity = value.chatOpacity.coerceIn(0f,1f))
        prefs.edit().putBoolean("chatEnabled",safe.chatEnabled).putString("chatMode",safe.chatMode.name)
            .putInt("chatTextSizeSp",safe.chatTextSizeSp).putFloat("chatOpacity",safe.chatOpacity)
            .putString("chatPosition",safe.chatPosition.name).putBoolean("showClock",safe.showClock)
            .putBoolean("animationsEnabled",safe.animationsEnabled).apply()
        mutable.value = safe
    }
}
