package com.oprawa.twitch20.ui

import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode

/** Named choices are presentation only: existing stored values are never rounded on read. */
internal enum class TextSizePreset(val sizeSp: Int, val label: String) {
    SMALL(14, "Маленький"),
    COMPACT(16, "Компактный"),
    MEDIUM(18, "Средний"),
    LARGE(22, "Большой"),
}

internal fun textSizeLabel(sizeSp: Int): String =
    TextSizePreset.entries.firstOrNull { it.sizeSp == sizeSp }?.label ?: "Размер $sizeSp"

/** A transient focus preview. Only [confirm] can produce a replacement settings draft. */
internal data class TextSizeSelection(
    val original: AppSettings,
    val mode: ChatMode,
    val focusedSizeSp: Int = original.styleFor(mode).textSizeSp,
) {
    val selectedSizeSp: Int get() = original.styleFor(mode).textSizeSp
    val options: List<Int> get() =
        (TextSizePreset.entries.map { it.sizeSp } + selectedSizeSp).distinct().sorted()

    fun focus(sizeSp: Int): TextSizeSelection {
        require(sizeSp in options)
        return copy(focusedSizeSp = sizeSp)
    }

    fun preview(): AppSettings = original.withStyle(mode,
        original.styleFor(mode).copy(textSizeSp = focusedSizeSp))

    fun confirm(): AppSettings = preview()
    fun cancel(): AppSettings = original
}
