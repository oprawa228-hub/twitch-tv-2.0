package com.oprawa.twitch20.ui

import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatPosition
import com.oprawa.twitch20.settings.OverlaySize

internal enum class SettingsChoice { TEXT_SIZE, OVERLAY_SIZE, OPACITY, POSITION }
internal data class SettingsOption(val label: String, val settings: AppSettings)

internal fun overlaySizeLabel(size: OverlaySize): String = when (size) {
    OverlaySize.SMALL -> "Маленький"
    OverlaySize.MEDIUM -> "Средний"
    OverlaySize.LARGE -> "Большой"
}

internal fun positionLabel(position: ChatPosition): String = when (position) {
    ChatPosition.TOP_LEFT -> "Слева сверху"
    ChatPosition.TOP_RIGHT -> "Справа сверху"
    ChatPosition.BOTTOM_LEFT -> "Слева снизу"
    ChatPosition.BOTTOM_RIGHT -> "Справа снизу"
}

private val opacityPresets = listOf("Лёгкий" to .25f, "Средний" to .65f, "Плотный" to .90f)
internal fun opacityLabel(opacity: Float): String = opacityPresets.firstOrNull { it.second == opacity }?.first ?: "Свой вариант"

/** Focus never persists settings or switches the active chat presentation. */
internal data class SettingsSelection(
    val original: AppSettings,
    val mode: ChatMode,
    val choice: SettingsChoice,
    val focusedIndex: Int? = null,
) {
    val title: String get() = when (choice) {
        SettingsChoice.TEXT_SIZE -> "Размер текста"
        SettingsChoice.OVERLAY_SIZE -> "Размер чата"
        SettingsChoice.OPACITY -> "Фон чата"
        SettingsChoice.POSITION -> "Положение чата"
    }
    val options: List<SettingsOption> get() = when (choice) {
        SettingsChoice.TEXT_SIZE -> TextSizeSelection(original, mode).options.map { size ->
            SettingsOption(textSizeLabel(size), original.withStyle(mode, original.styleFor(mode).copy(textSizeSp = size)))
        }
        SettingsChoice.OVERLAY_SIZE -> OverlaySize.entries.map { size ->
            SettingsOption(overlaySizeLabel(size), original.copy(overlaySize = size))
        }
        SettingsChoice.OPACITY -> {
            val current = original.overlayStyle.opacity
            val values = if (opacityPresets.any { it.second == current }) opacityPresets
                else opacityPresets + ("Свой вариант" to current)
            values.map { (label, alpha) -> SettingsOption(label, original.withStyle(ChatMode.OVERLAY,
                original.overlayStyle.copy(opacity = alpha))) }
        }
        SettingsChoice.POSITION -> ChatPosition.entries.map { position ->
            SettingsOption(positionLabel(position), original.copy(chatPosition = position))
        }
    }
    val selectedIndex: Int get() = options.indexOfFirst { it.settings == original }.coerceAtLeast(0)
    fun focus(index: Int): SettingsSelection {
        require(index in options.indices)
        return copy(focusedIndex = index)
    }
    fun preview(): AppSettings = options[focusedIndex ?: selectedIndex].settings
    fun confirm(): AppSettings = preview()
    fun cancel(): AppSettings = original
}
