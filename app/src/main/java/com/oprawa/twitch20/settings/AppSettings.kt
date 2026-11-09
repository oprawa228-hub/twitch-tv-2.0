package com.oprawa.twitch20.settings

enum class ChatMode { OFF, OVERLAY, THEATER }
enum class ChatPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
enum class OverlaySize(val widthFraction: Float, val heightFraction: Float) {
    SMALL(.20f, .40f), MEDIUM(.24f, .52f), LARGE(.30f, .66f)
}

data class ChatStyle(val textSizeSp: Int = 18, val opacity: Float = .65f) {
    internal fun normalized(): ChatStyle = copy(textSizeSp = textSizeSp.coerceIn(12, 24),
        opacity = if (opacity.isFinite()) opacity.coerceIn(0f, 1f) else .65f)
}

data class AppSettings(
    val chatMode: ChatMode = ChatMode.OVERLAY,
    val overlayStyle: ChatStyle = ChatStyle(),
    val theaterStyle: ChatStyle = ChatStyle(),
    val chatPosition: ChatPosition = ChatPosition.TOP_RIGHT,
    val showClock: Boolean = true,
    val animationsEnabled: Boolean = true,
    val overlaySize: OverlaySize = OverlaySize.MEDIUM,
    val cardPreviewEnabled: Boolean = true,
    val followNotificationsEnabled: Boolean = true,
) {
    val chatEnabled: Boolean get() = chatMode != ChatMode.OFF
    val overlayEnabled: Boolean get() = chatMode == ChatMode.OVERLAY
    val theaterEnabled: Boolean get() = chatMode == ChatMode.THEATER
    val chatTextSizeSp: Int get() = styleFor(chatMode).textSizeSp
    val chatOpacity: Float get() = if (theaterEnabled) 1f else overlayStyle.opacity

    fun styleFor(mode: ChatMode): ChatStyle = if (mode == ChatMode.THEATER) theaterStyle else overlayStyle

    /** Editing a profile never changes which presentation is enabled. OFF uses the overlay profile. */
    fun withStyle(mode: ChatMode, style: ChatStyle): AppSettings =
        if (mode == ChatMode.THEATER) copy(theaterStyle = style.normalized())
        else copy(overlayStyle = style.normalized())

    fun toggleChat(mode: ChatMode): AppSettings =
        copy(chatMode = if (mode == ChatMode.OFF || mode == chatMode) ChatMode.OFF else mode)

    internal fun normalized(): AppSettings = copy(overlayStyle = overlayStyle.normalized(),
        theaterStyle = theaterStyle.normalized())
}

/** Migration is read-compatible; the first explicit save persists the new independent profiles. */
internal object SettingsCodec {
    fun decode(values: Map<String, *>): AppSettings {
        val legacyMode = if (values["chatEnabled"] as? Boolean == false) ChatMode.OFF
            else enumValue<ChatMode>(values["chatMode"]) ?: ChatMode.OVERLAY
        val legacyStyle = ChatStyle(values["chatTextSizeSp"] as? Int ?: 18,
            values["chatOpacity"] as? Float ?: .65f).normalized()
        fun style(prefix: String): ChatStyle = ChatStyle(
            values["${prefix}TextSizeSp"] as? Int ?: legacyStyle.textSizeSp,
            values["${prefix}Opacity"] as? Float ?: legacyStyle.opacity,
        ).normalized()
        return AppSettings(
            chatMode = enumValue<ChatMode>(values["activeChatMode"]) ?: legacyMode,
            overlayStyle = style("overlay"), theaterStyle = style("theater"),
            chatPosition = enumValue<ChatPosition>(values["chatPosition"]) ?: ChatPosition.TOP_RIGHT,
            showClock = values["showClock"] as? Boolean ?: true,
            animationsEnabled = values["animationsEnabled"] as? Boolean ?: true,
            overlaySize = enumValue<OverlaySize>(values["overlaySize"]) ?: OverlaySize.MEDIUM,
            cardPreviewEnabled = values["cardPreviewEnabled"] as? Boolean ?: true,
            followNotificationsEnabled = values["followNotificationsEnabled"] as? Boolean ?: true,
        )
    }

    fun encode(value: AppSettings): Map<String, Any> = value.normalized().let { safe ->
        mapOf("activeChatMode" to safe.chatMode.name,
            "overlayTextSizeSp" to safe.overlayStyle.textSizeSp, "overlayOpacity" to safe.overlayStyle.opacity,
            "theaterTextSizeSp" to safe.theaterStyle.textSizeSp, "theaterOpacity" to safe.theaterStyle.opacity,
            "chatPosition" to safe.chatPosition.name, "showClock" to safe.showClock,
            "animationsEnabled" to safe.animationsEnabled,
            "overlaySize" to safe.overlaySize.name, "cardPreviewEnabled" to safe.cardPreviewEnabled,
            "followNotificationsEnabled" to safe.followNotificationsEnabled)
    }

    private inline fun <reified T : Enum<T>> enumValue(value: Any?): T? =
        (value as? String)?.let { text -> enumValues<T>().firstOrNull { it.name == text } }
}
