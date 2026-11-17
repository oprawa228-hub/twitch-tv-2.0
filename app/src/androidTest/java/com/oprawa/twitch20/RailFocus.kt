package com.oprawa.twitch20

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.util.regex.Pattern

private val railLabel = Pattern.compile(
    "Поиск|Каналы|Записи|Категории|Рекомендации|Настройки|[^,]+, (?:в эфире, [^,]+|офлайн)"
)

// Compose can put the description on a child of the unnamed focused control.
// The identically worded title in the overlapping catalog has no button semantics.
internal fun ownsRemoteFocus(target: UiObject2): Boolean {
    var node: UiObject2? = target
    while (node != null) {
        if (node.isFocusable) return node.isFocused
        node = node.parent
    }
    return false
}

internal fun focusedRailItem(device: UiDevice): UiObject2? =
    device.findObjects(By.desc(railLabel)).firstOrNull(::ownsRemoteFocus)
