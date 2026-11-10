package com.oprawa.twitch20.ui

import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatPosition
import com.oprawa.twitch20.settings.ClockPlacement
import com.oprawa.twitch20.settings.ClockStyle
import com.oprawa.twitch20.settings.OverlaySize

internal enum class SettingsChoice { TEXT_SIZE, OVERLAY_SIZE, OPACITY, POSITION }
internal data class SettingsOption(val label: String, val settings: AppSettings)

internal fun overlaySizeLabel(size: OverlaySize): String = when (size) {
    OverlaySize.SMALL -> "Маленький"
    OverlaySize.MEDIUM -> "Средний"
    OverlaySize.LARGE -> "Большой"
    OverlaySize.TALL -> "Высокий"
}

internal fun clockStyleLabel(style: ClockStyle): String = when (style) {
    ClockStyle.CLASSIC -> "Классический"
    ClockStyle.COMPACT -> "Компактный"
    ClockStyle.SEGMENTS -> "Сегменты"
}

internal fun clockPlacementLabel(placement: ClockPlacement): String = when (placement) {
    ClockPlacement.VIDEO -> "На видео"
    ClockPlacement.CHAT -> "В чате"
}

internal fun positionLabel(position: ChatPosition): String = when (position) {
    ChatPosition.TOP_LEFT -> "Слева сверху"
    ChatPosition.TOP_RIGHT -> "Справа сверху"
    ChatPosition.BOTTOM_LEFT -> "Слева снизу"
    ChatPosition.BOTTOM_RIGHT -> "Справа снизу"
}

private val opacityPresets = listOf("Лёгкий" to .25f, "Средний" to .65f, "Плотный" to .90f)
internal fun opacityLabel(opacity: Float): String = opacityPresets.firstOrNull { it.second == opacity }?.first ?: when {
    opacity <= 0f -> "Прозрачный"
    opacity < .25f -> "Едва заметный"
    opacity < .65f -> "Полупрозрачный"
    opacity < .90f -> "Насыщенный"
    opacity < 1f -> "Почти непрозрачный"
    else -> "Непрозрачный"
}

/** The current setting and the values reached with directional input. */
internal data class SettingsSelection(
    val original: AppSettings,
    val mode: ChatMode,
    val choice: SettingsChoice,
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
                else opacityPresets + (opacityLabel(current) to current)
            values.sortedBy { it.second }.map { (label, alpha) -> SettingsOption(label, original.withStyle(ChatMode.OVERLAY,
                original.overlayStyle.copy(opacity = alpha))) }
        }
        SettingsChoice.POSITION -> ChatPosition.entries.map { position ->
            SettingsOption(positionLabel(position), original.copy(chatPosition = position))
        }
    }
    val selectedIndex: Int get() = options.indexOfFirst { it.settings == original }.coerceAtLeast(0)
    fun step(delta: Int): AppSettings = options[(selectedIndex + delta).mod(options.size)].settings
}
