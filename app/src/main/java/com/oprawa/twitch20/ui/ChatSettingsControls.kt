package com.oprawa.twitch20.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatPosition
import kotlin.math.roundToInt

/** Same editing actions in the player and global settings. Editing never activates a mode. */
@Composable
internal fun ChatSettingsControls(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    firstFocus: FocusRequester,
    onPreviewMode: (ChatMode?) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SettingChoice("Чат поверх видео", enabledLabel(settings.overlayEnabled),
            Modifier.focusRequester(firstFocus), onFocused = { onPreviewMode(null) }) {
            onChange(settings.toggleChat(ChatMode.OVERLAY)); onPreviewMode(null)
        }
        SettingChoice("Кинотеатр", enabledLabel(settings.theaterEnabled),
            onFocused = { onPreviewMode(null) }) {
            onChange(settings.toggleChat(ChatMode.THEATER)); onPreviewMode(null)
        }
        Text("Включение одного режима выключает другой", color = TvColors.Muted,
            fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
        for (mode in listOf(ChatMode.OVERLAY, ChatMode.THEATER)) {
            val style = settings.styleFor(mode)
            Text(chatModeLabel(mode), color = TvColors.Link, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 2.dp))
            val prefix = if (mode == ChatMode.OVERLAY) "Чат" else "Кинотеатр"
            SettingChoice("Размер текста", "${style.textSizeSp}", accessibilityLabel = "$prefix — размер текста",
                onFocused = { onPreviewMode(mode) }) { step ->
                onChange(settings.withStyle(mode, style.copy(textSizeSp = (style.textSizeSp + step * 2).coerceIn(12, 24))))
            }
            SettingChoice("Фон", "${(style.opacity * 100).roundToInt()}%", accessibilityLabel = "$prefix — фон",
                onFocused = { onPreviewMode(mode) }) { step ->
                val percent = ((style.opacity * 100).roundToInt() + step * 10).coerceIn(0, 100)
                onChange(settings.withStyle(mode, style.copy(opacity = percent / 100f)))
            }
            if (mode == ChatMode.OVERLAY) {
                SettingChoice("Положение", positionLabel(settings.chatPosition),
                    onFocused = { onPreviewMode(mode) }) { step ->
                    val positions = ChatPosition.entries
                    onChange(settings.copy(chatPosition = positions[(settings.chatPosition.ordinal + step + positions.size) % positions.size]))
                }
            }
        }
    }
}

internal fun enabledLabel(enabled: Boolean) = if (enabled) "Включён" else "Выключен"

internal fun chatModeLabel(mode: ChatMode) = when (mode) {
    ChatMode.OFF -> "Без чата"
    ChatMode.OVERLAY -> "Чат поверх видео"
    ChatMode.THEATER -> "Кинотеатр"
}

private fun positionLabel(position: ChatPosition) = when (position) {
    ChatPosition.TOP_LEFT -> "Слева сверху"
    ChatPosition.TOP_RIGHT -> "Справа сверху"
    ChatPosition.BOTTOM_LEFT -> "Слева снизу"
    ChatPosition.BOTTOM_RIGHT -> "Справа снизу"
}
