package com.oprawa.twitch20.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oprawa.twitch20.BuildConfig
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ClockPlacement
import com.oprawa.twitch20.settings.ClockStyle

@Composable
internal fun ChatSettingsControls(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    firstFocus: FocusRequester,
    onPreviewMode: (ChatMode?) -> Unit = {},
    state: SettingsEditorState = rememberSettingsEditorState(),
    onCheckUpdates: (() -> Unit)? = null,
    onCloseProfile: (() -> Unit)? = null,
    onAccount: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null,
    accountName: String? = null,
    onNotificationExample: (() -> Unit)? = null,
) {
    var expanded by remember(state.page) { mutableStateOf<String?>(null) }
    var returningTo by remember(state.page) { mutableStateOf<String?>(null) }
    val launchers = remember(state.page) { mutableMapOf<String, FocusRequester>() }
    val editorFocus = remember { FocusRequester() }
    BackHandler(enabled = state.page != SettingsEditorPage.OVERVIEW || expanded != null) {
        when {
            expanded != null -> {
                returningTo = expanded
                expanded?.let(state::focusSetting)
                expanded = null
            }
            onCloseProfile != null -> onCloseProfile()
            else -> state.back()
        }
    }
    LaunchedEffect(state.page, expanded) {
        onPreviewMode(when (state.page) {
            SettingsEditorPage.OVERLAY -> ChatMode.OVERLAY
            SettingsEditorPage.THEATER -> ChatMode.THEATER
            else -> null
        })
        withFrameNanos { }
        val key = returningTo ?: if (state.page == SettingsEditorPage.OVERVIEW) state.overviewFocus else state.focusedSetting
        val restore = if (expanded != null) editorFocus else launchers[key] ?: firstFocus
        restore.requestFocus()
        returningTo = null
    }

    @Composable
    fun Link(key: String, label: String, first: Boolean = false, summary: String? = null, action: () -> Unit) {
        val created = remember(key, first) { if (first) firstFocus else FocusRequester() }
        val focus = launchers.getOrPut(key) { created }
        SettingsPageLink(label, Modifier.focusRequester(focus), summary = summary, onFocused = {
            if (state.page == SettingsEditorPage.OVERVIEW) state.rememberOverviewFocus(key)
            else state.focusSetting(key)
        }, onClick = action)
    }

    @Composable
    fun Parameter(key: String, label: String, value: String, first: Boolean = false, change: (Int) -> Unit) {
        val created = remember(key, first) { if (first) firstFocus else FocusRequester() }
        val focus = launchers.getOrPut(key) { created }
        val open = expanded == key
        SettingsPageLink(label, Modifier.focusRequester(focus), summary = value, expanded = open,
            onFocused = { state.focusSetting(key) }) { expanded = if (open) null else key }
        if (open) {
            SettingChoice("Значение", value, Modifier.padding(start = 12.dp).focusRequester(editorFocus),
                accessibilityLabel = label, onConfirm = { returningTo = key; expanded = null }, change = change)
        }
    }

    @Composable
    fun Toggle(key: String, label: String, enabled: Boolean, first: Boolean = false, change: () -> Unit) {
        Parameter(key, label, enabledLabel(enabled), first) { change() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state.page) {
            SettingsEditorPage.OVERVIEW -> {
                if (onAccount != null) Link("ACCOUNT", "Аккаунт", first = true,
                    summary = accountName ?: "Гость", action = onAccount)
                val previewStatus = if (settings.cardPreviewEnabled) "Предпросмотр вкл." else "Предпросмотр выкл."
                val animationStatus = if (settings.animationsEnabled) "анимации вкл." else "анимации выкл."
                val clockStatus = if (settings.showClock) "часы вкл." else "часы скрыты"
                Link("INTERFACE", "Интерфейс", first = onAccount == null,
                    summary = "$previewStatus · $animationStatus · $clockStatus") {
                    state.open(SettingsEditorPage.INTERFACE)
                }
                Link("VIEWING", "Просмотр", summary = chatModeLabel(settings.chatMode)) {
                    state.open(SettingsEditorPage.VIEWING)
                }
                Link("NOTIFICATIONS", "Уведомления", summary = enabledLabel(settings.followNotificationsEnabled)) {
                    state.open(SettingsEditorPage.NOTIFICATIONS)
                }
                if (onAbout != null || onCheckUpdates != null) Link("ABOUT", "О приложении", summary = BuildConfig.VERSION_NAME) {
                    state.open(SettingsEditorPage.ABOUT)
                }
            }
            SettingsEditorPage.ABOUT -> {
                SettingsSectionTitle("О приложении · ${BuildConfig.VERSION_NAME}")
                if (onCheckUpdates != null) Link("UPDATES", "Проверить обновления", first = true, action = onCheckUpdates)
                if (onAbout != null) Link("LICENSES", "Авторы и лицензии", first = onCheckUpdates == null, action = onAbout)
            }
            SettingsEditorPage.VIEWING -> {
                SettingsSectionTitle("Просмотр")
                Link("OVERLAY", "Чат поверх видео", first = true, summary = if (settings.overlayEnabled)
                    overlaySizeLabel(settings.overlaySize) else "Выключен") { state.open(SettingsEditorPage.OVERLAY) }
                Link("THEATER", "Кинотеатр", summary = if (settings.theaterEnabled)
                    textSizeLabel(settings.theaterStyle.textSizeSp) else "Выключен") { state.open(SettingsEditorPage.THEATER) }
            }
            SettingsEditorPage.OVERLAY, SettingsEditorPage.THEATER -> {
                val mode = if (state.page == SettingsEditorPage.OVERLAY) ChatMode.OVERLAY else ChatMode.THEATER
                SettingsSectionTitle(chatModeLabel(mode))
                Toggle("ENABLED", "Показывать", settings.chatMode == mode, first = true) {
                    onChange(settings.toggleChat(mode))
                }
                val choices = if (mode == ChatMode.OVERLAY) listOf(SettingsChoice.OVERLAY_SIZE, SettingsChoice.TEXT_SIZE,
                    SettingsChoice.OPACITY, SettingsChoice.POSITION) else listOf(SettingsChoice.TEXT_SIZE)
                for (choice in choices) {
                    val selection = SettingsSelection(settings, mode, choice)
                    Parameter(choice.name, selection.title, selection.options[selection.selectedIndex].label) {
                        onChange(selection.step(it))
                    }
                }
            }
            SettingsEditorPage.INTERFACE -> {
                SettingsSectionTitle("Интерфейс")
                Toggle("CARD_PREVIEW", "Предпросмотр эфиров", settings.cardPreviewEnabled, first = true) {
                    onChange(settings.copy(cardPreviewEnabled = !settings.cardPreviewEnabled))
                }
                Toggle("PREVIEW_SOUND", "Звук предпросмотра", settings.previewSoundEnabled) {
                    onChange(settings.copy(previewSoundEnabled = !settings.previewSoundEnabled))
                }
                Toggle("ANIMATIONS", "Анимации", settings.animationsEnabled) {
                    onChange(settings.copy(animationsEnabled = !settings.animationsEnabled))
                }
                Link("CLOCK", "Часы", summary = if (settings.showClock) clockStyleLabel(settings.clockStyle) else "Скрыты") {
                    state.open(SettingsEditorPage.CLOCK)
                }
            }
            SettingsEditorPage.CLOCK -> {
                SettingsSectionTitle("Часы")
                Toggle("CLOCK_ENABLED", "На главной и в плеере", settings.showClock, first = true) {
                    onChange(settings.copy(showClock = !settings.showClock))
                }
                Parameter("CLOCK_STYLE", "Стиль", clockStyleLabel(settings.clockStyle)) { delta ->
                    val styles = ClockStyle.entries
                    onChange(settings.copy(clockStyle = styles[(settings.clockStyle.ordinal + delta).mod(styles.size)]))
                }
                Parameter("CLOCK_PLACEMENT", "В плеере", clockPlacementLabel(settings.clockPlacement)) { delta ->
                    val placements = ClockPlacement.entries
                    onChange(settings.copy(clockPlacement = placements[(settings.clockPlacement.ordinal + delta).mod(placements.size)]))
                }
            }
            SettingsEditorPage.NOTIFICATIONS -> {
                SettingsSectionTitle("Уведомления")
                Toggle("NOTIFICATIONS_ENABLED", "Новые эфиры подписок", settings.followNotificationsEnabled, first = true) {
                    onChange(settings.copy(followNotificationsEnabled = !settings.followNotificationsEnabled))
                }
                Toggle("NOTIFICATION_SOUND", "Звук уведомлений", settings.notificationSoundEnabled) {
                    onChange(settings.copy(notificationSoundEnabled = !settings.notificationSoundEnabled))
                }
                if (onNotificationExample != null) Link("NOTIFICATION_EXAMPLE", "Показать пример", action = onNotificationExample)
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, color = TvColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
}

internal fun enabledLabel(enabled: Boolean) = if (enabled) "Включён" else "Выключен"

internal fun chatModeLabel(mode: ChatMode) = when (mode) {
    ChatMode.OFF -> "Без чата"
    ChatMode.OVERLAY -> "Чат поверх видео"
    ChatMode.THEATER -> "Кинотеатр"
}
