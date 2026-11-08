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
import android.view.KeyEvent as AndroidKeyEvent

/** Shared immediate settings editor. Only a focused picker option remains transient. */
@Composable
internal fun ChatSettingsControls(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    firstFocus: FocusRequester,
    onPreviewMode: (ChatMode?) -> Unit = {},
    state: SettingsEditorState = rememberSettingsEditorState(),
    onCheckUpdates: (() -> Unit)? = null,
    onPreviewSettings: (AppSettings?) -> Unit = {},
    onCloseProfile: (() -> Unit)? = null,
    onAccount: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null,
) {
    var selection by remember(state.page) { mutableStateOf<SettingsSelection?>(null) }
    var lastChoice by remember(state.page) { mutableStateOf<SettingsChoice?>(null) }
    val previewCallback by rememberUpdatedState(onPreviewSettings)
    LaunchedEffect(selection) { previewCallback(selection?.preview()) }
    DisposableEffect(Unit) { onDispose { previewCallback(null) } }
    val launchers = remember {
        (listOf("ACCOUNT", "OVERLAY", "THEATER", "INTERFACE", "CLOCK", "ABOUT", "UPDATES") +
            SettingsChoice.entries.map { it.name }).associateWith { FocusRequester() }
    }
    BackHandler(enabled = state.page != SettingsEditorPage.OVERVIEW || selection != null) {
        if (selection != null) selection = null
        else if (onCloseProfile != null) onCloseProfile()
        else state.back()
    }
    LaunchedEffect(state.page, selection != null) {
        onPreviewMode(when (state.page) {
            SettingsEditorPage.OVERLAY -> ChatMode.OVERLAY
            SettingsEditorPage.THEATER -> ChatMode.THEATER
            else -> null
        })
        withFrameNanos { }
        val restore = when {
            selection != null -> firstFocus
            state.page == SettingsEditorPage.OVERVIEW -> when (state.overviewFocus) {
                "ACCOUNT" -> firstFocus
                "INTERFACE" -> if (onAccount == null) firstFocus else launchers.getValue("INTERFACE")
                else -> launchers[state.overviewFocus] ?: firstFocus
            }
            lastChoice != null -> launchers[lastChoice!!.name] ?: firstFocus
            else -> firstFocus
        }
        restore.requestFocus()
    }
    fun openChoice(mode: ChatMode, choice: SettingsChoice) {
        lastChoice = choice
        selection = SettingsSelection(settings, mode, choice)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val picker = selection
        if (picker != null) {
            Text(picker.title, color = TvColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            picker.options.forEachIndexed { index, option ->
                TextSizeOption(option.label, selected = index == picker.selectedIndex,
                    modifier = if (index == picker.selectedIndex) Modifier.focusRequester(firstFocus) else Modifier,
                    accessibilityLabel = "${picker.title}: ${option.label}",
                    onFocused = { selection = picker.focus(index) },
                    onConfirm = {
                        onChange(picker.focus(index).confirm())
                        selection = null
                    })
            }
        } else when (state.page) {
            SettingsEditorPage.OVERVIEW -> {
                if (onAccount != null) SettingsPageLink("Аккаунт", Modifier.focusRequester(firstFocus),
                    onFocused = { state.rememberOverviewFocus("ACCOUNT") }, onClick = onAccount)
                SettingsPageLink("Интерфейс", Modifier.focusRequester(if (onAccount == null) firstFocus else launchers.getValue("INTERFACE")),
                    onFocused = { state.rememberOverviewFocus("INTERFACE") }) { state.open(SettingsEditorPage.INTERFACE) }
                SettingsPageLink("Чат поверх видео", Modifier.focusRequester(launchers.getValue("OVERLAY")),
                    onFocused = { state.rememberOverviewFocus("OVERLAY") }) { state.open(SettingsEditorPage.OVERLAY) }
                SettingsPageLink("Кинотеатр", Modifier.focusRequester(launchers.getValue("THEATER")),
                    onFocused = { state.rememberOverviewFocus("THEATER") }) { state.open(SettingsEditorPage.THEATER) }
                SettingsPageLink("Часы", Modifier.focusRequester(launchers.getValue("CLOCK")),
                    onFocused = { state.rememberOverviewFocus("CLOCK") }) { state.open(SettingsEditorPage.CLOCK) }
                if (onAbout != null) SettingsPageLink("О приложении", Modifier.focusRequester(launchers.getValue("ABOUT")),
                    onFocused = { state.rememberOverviewFocus("ABOUT") }, onClick = onAbout)
                else if (onCheckUpdates != null) SettingsPageLink("Проверить обновления", Modifier.focusRequester(launchers.getValue("UPDATES")),
                    onFocused = { state.rememberOverviewFocus("UPDATES") }, onClick = onCheckUpdates)
            }
            SettingsEditorPage.OVERLAY, SettingsEditorPage.THEATER -> {
                val mode = if (state.page == SettingsEditorPage.OVERLAY) ChatMode.OVERLAY else ChatMode.THEATER
                val style = settings.styleFor(mode)
                val active = settings.chatMode == mode
                Text(chatModeLabel(mode), color = TvColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                SettingChoice("Показывать", enabledLabel(active), Modifier.focusRequester(firstFocus), selected = active) {
                    onChange(settings.toggleChat(mode))
                }
                if (mode == ChatMode.OVERLAY) SettingsPageLink("Размер чата: ${overlaySizeLabel(settings.overlaySize)}",
                    Modifier.focusRequester(launchers.getValue("OVERLAY_SIZE"))) { openChoice(mode, SettingsChoice.OVERLAY_SIZE) }
                SettingsPageLink("Размер текста: ${textSizeLabel(style.textSizeSp)}",
                    Modifier.focusRequester(launchers.getValue("TEXT_SIZE"))) { openChoice(mode, SettingsChoice.TEXT_SIZE) }
                if (mode == ChatMode.OVERLAY) {
                    SettingsPageLink("Фон чата: ${opacityLabel(style.opacity)}",
                        Modifier.focusRequester(launchers.getValue("OPACITY"))) { openChoice(mode, SettingsChoice.OPACITY) }
                    SettingsPageLink("Положение: ${positionLabel(settings.chatPosition)}",
                        Modifier.focusRequester(launchers.getValue("POSITION"))) { openChoice(mode, SettingsChoice.POSITION) }
                }
            }
            SettingsEditorPage.INTERFACE -> {
                Text("Интерфейс", color = TvColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                SettingChoice("Предпросмотр эфиров", enabledLabel(settings.cardPreviewEnabled), Modifier.focusRequester(firstFocus),
                    selected = settings.cardPreviewEnabled) { onChange(settings.copy(cardPreviewEnabled = !settings.cardPreviewEnabled)) }
                SettingChoice("Анимации", if (settings.animationsEnabled) "Включены" else "Выключены",
                    selected = settings.animationsEnabled) { onChange(settings.copy(animationsEnabled = !settings.animationsEnabled)) }
            }
            SettingsEditorPage.CLOCK -> {
                Text("Часы", color = TvColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                SettingChoice("На главной и в плеере", if (settings.showClock) "Показаны" else "Скрыты", Modifier.focusRequester(firstFocus),
                    selected = settings.showClock) { onChange(settings.copy(showClock = !settings.showClock)) }
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
