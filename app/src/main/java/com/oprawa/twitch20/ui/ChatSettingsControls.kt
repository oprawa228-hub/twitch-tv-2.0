package com.oprawa.twitch20.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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

/** One navigation model for global settings and the player; the caller owns the whole draft. */
@Composable
internal fun ChatSettingsControls(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    firstFocus: FocusRequester,
    onPreviewMode: (ChatMode?) -> Unit = {},
    state: SettingsEditorState = rememberSettingsEditorState(),
    onCheckUpdates: (() -> Unit)? = null,
) {
    val launchers = remember {
        listOf("MODE_THEATER", "OVERLAY", "THEATER", "INTERFACE", "UPDATES")
            .associateWith { FocusRequester() }
    }
    BackHandler(enabled = state.page != SettingsEditorPage.OVERVIEW) { state.back() }
    LaunchedEffect(state.page) {
        val restoreFocus = if (state.page == SettingsEditorPage.OVERVIEW)
            launchers[state.overviewFocus] ?: firstFocus else firstFocus
        onPreviewMode(when (state.page) {
            SettingsEditorPage.OVERLAY -> ChatMode.OVERLAY
            SettingsEditorPage.THEATER -> ChatMode.THEATER
            else -> null
        })
        withFrameNanos { }
        restoreFocus.requestFocus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (state.page) {
            SettingsEditorPage.OVERVIEW -> {
                SettingChoice("Чат поверх видео", enabledLabel(settings.overlayEnabled),
                    Modifier.focusRequester(firstFocus), selected = settings.overlayEnabled,
                    onFocused = { state.rememberOverviewFocus("MODE_OVERLAY") }) {
                    onChange(settings.toggleChat(ChatMode.OVERLAY))
                }
                SettingChoice("Кинотеатр", enabledLabel(settings.theaterEnabled),
                    Modifier.focusRequester(launchers.getValue("MODE_THEATER")), selected = settings.theaterEnabled,
                    onFocused = { state.rememberOverviewFocus("MODE_THEATER") }) {
                    onChange(settings.toggleChat(ChatMode.THEATER))
                }
                SettingsPageLink("Настроить чат поверх видео", Modifier.focusRequester(launchers.getValue("OVERLAY")),
                    onFocused = { state.rememberOverviewFocus("OVERLAY") }) { state.open(SettingsEditorPage.OVERLAY) }
                SettingsPageLink("Настроить кинотеатр", Modifier.focusRequester(launchers.getValue("THEATER")),
                    onFocused = { state.rememberOverviewFocus("THEATER") }) { state.open(SettingsEditorPage.THEATER) }
                SettingsPageLink("Интерфейс", Modifier.focusRequester(launchers.getValue("INTERFACE")),
                    onFocused = { state.rememberOverviewFocus("INTERFACE") }) { state.open(SettingsEditorPage.INTERFACE) }
                if (onCheckUpdates != null) SettingsPageLink("Проверить обновления",
                    Modifier.focusRequester(launchers.getValue("UPDATES")),
                    onFocused = { state.rememberOverviewFocus("UPDATES") }, onClick = onCheckUpdates)
            }
            SettingsEditorPage.OVERLAY, SettingsEditorPage.THEATER -> {
                val mode = if (state.page == SettingsEditorPage.OVERLAY) ChatMode.OVERLAY else ChatMode.THEATER
                val style = settings.styleFor(mode)
                val prefix = if (mode == ChatMode.OVERLAY) "Чат" else "Кинотеатр"
                Text(chatModeLabel(mode), color = TvColors.Text, fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                SettingChoice("Размер текста", "${style.textSizeSp}", Modifier.focusRequester(firstFocus),
                    accessibilityLabel = "$prefix — размер текста") { step ->
                    onChange(settings.withStyle(mode, style.copy(textSizeSp = (style.textSizeSp + step * 2).coerceIn(12, 24))))
                }
                SettingChoice("Фон", "${(style.opacity * 100).roundToInt()}%", accessibilityLabel = "$prefix — фон") { step ->
                    val percent = ((style.opacity * 100).roundToInt() + step * 10).coerceIn(0, 100)
                    onChange(settings.withStyle(mode, style.copy(opacity = percent / 100f)))
                }
                if (mode == ChatMode.OVERLAY) SettingChoice("Положение", positionLabel(settings.chatPosition)) { step ->
                    val positions = ChatPosition.entries
                    onChange(settings.copy(chatPosition = positions[(settings.chatPosition.ordinal + step + positions.size) % positions.size]))
                }
                Text("Меняется только этот профиль. Режим выбирается в обзоре.", color = TvColors.Muted,
                    fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                SettingsPageLink("Назад к обзору", onClick = { state.back() })
            }
            SettingsEditorPage.INTERFACE -> {
                Text("Интерфейс", color = TvColors.Text, fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                SettingChoice("Часы", if (settings.showClock) "Показаны" else "Скрыты", Modifier.focusRequester(firstFocus),
                    selected = settings.showClock) { onChange(settings.copy(showClock = !settings.showClock)) }
                SettingChoice("Анимации", if (settings.animationsEnabled) "Включены" else "Выключены",
                    selected = settings.animationsEnabled) { onChange(settings.copy(animationsEnabled = !settings.animationsEnabled)) }
                Text("При выключенных анимациях пример остаётся неподвижным.", color = TvColors.Muted,
                    fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                SettingsPageLink("Назад к обзору", onClick = { state.back() })
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
