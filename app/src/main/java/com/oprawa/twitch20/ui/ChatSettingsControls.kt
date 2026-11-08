package com.oprawa.twitch20.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatPosition
import kotlin.math.roundToInt
import android.view.KeyEvent as AndroidKeyEvent

/** One navigation model for global settings and the player; the caller owns the whole draft. */
@Composable
internal fun ChatSettingsControls(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    firstFocus: FocusRequester,
    onPreviewMode: (ChatMode?) -> Unit = {},
    state: SettingsEditorState = rememberSettingsEditorState(),
    onCheckUpdates: (() -> Unit)? = null,
    onPreviewSettings: (AppSettings?) -> Unit = {},
) {
    var textSizeSelection by remember(state.page) { mutableStateOf<TextSizeSelection?>(null) }
    val previewCallback by rememberUpdatedState(onPreviewSettings)
    LaunchedEffect(textSizeSelection) { previewCallback(textSizeSelection?.preview()) }
    DisposableEffect(Unit) { onDispose { previewCallback(null) } }
    val launchers = remember {
        listOf("MODE_THEATER", "OVERLAY", "THEATER", "INTERFACE", "UPDATES")
            .associateWith { FocusRequester() }
    }
    BackHandler(enabled = state.page != SettingsEditorPage.OVERVIEW) {
        if (textSizeSelection != null) textSizeSelection = null else state.back()
    }
    LaunchedEffect(state.page, textSizeSelection != null) {
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
                val selection = textSizeSelection
                if (selection != null) {
                    Text("Размер текста", color = TvColors.Text, fontSize = 17.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    selection.options.forEach { size ->
                        TextSizeOption(textSizeLabel(size), selected = size == selection.selectedSizeSp,
                            modifier = if (size == selection.selectedSizeSp) Modifier.focusRequester(firstFocus) else Modifier,
                            accessibilityLabel = "$prefix — размер текста: ${textSizeLabel(size)}",
                            onFocused = { textSizeSelection = selection.focus(size) },
                            onConfirm = {
                                onChange(selection.focus(size).confirm())
                                textSizeSelection = null
                            })
                    }
                    Text("↑ ↓ — посмотреть · OK — выбрать\nНазад — отменить выбор", color = TvColors.Muted,
                        fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                    SettingsPageLink("Отменить выбор", onClick = { textSizeSelection = null })
                } else {
                    SettingsPageLink("Размер текста: ${textSizeLabel(style.textSizeSp)}", Modifier.focusRequester(firstFocus)) {
                        textSizeSelection = TextSizeSelection(settings, mode)
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

/** Focus previews; only a completed OK press confirms. Radio mark stays on the draft value. */
@Composable
private fun TextSizeOption(label: String, selected: Boolean, modifier: Modifier,
    accessibilityLabel: String, onFocused: () -> Unit, onConfirm: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) TvColors.Background else TvColors.Text
    Row(modifier.fillMaxWidth().heightIn(min = 46.dp)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode in setOf(AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)) {
                if (key.action == AndroidKeyEvent.ACTION_UP && !key.isCanceled) onConfirm()
                true
            } else false
        }
        .semantics(mergeDescendants = true) {
            role = Role.RadioButton
            this.selected = selected
            contentDescription = accessibilityLabel
            onClick { onConfirm(); true }
        }
        .focusable()
        .background(if (focused) TvColors.Focus else TvColors.Surface, RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = foreground, unselectedColor = foreground))
        Text(label, fontSize = 16.sp, color = foreground)
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
