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
    onNotificationSound: () -> Unit = {},
) {
    val launchers = remember(state.page) { mutableMapOf<String, FocusRequester>() }
    BackHandler(enabled = state.page != SettingsEditorPage.OVERVIEW) {
        if (onCloseProfile != null) onCloseProfile() else state.back()
    }
    LaunchedEffect(state.page) {
        onPreviewMode(when (state.page) {
            SettingsEditorPage.OVERLAY -> ChatMode.OVERLAY
            SettingsEditorPage.THEATER -> ChatMode.THEATER
            else -> null
        })
        withFrameNanos { }
        val key = if (state.page == SettingsEditorPage.OVERVIEW) state.overviewFocus else state.focusedSetting
        (launchers[key] ?: firstFocus).requestFocus()
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
        SettingChoice(label, value, Modifier.focusRequester(focus),
            onFocused = { state.focusSetting(key) }, change = change)
    }

    @Composable
    fun Toggle(key: String, label: String, enabled: Boolean, first: Boolean = false, change: () -> Unit) {
        Parameter(key, label, enabledLabel(enabled), first) { if ((it > 0) != enabled) change() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state.page) {
            SettingsEditorPage.OVERVIEW -> {
                if (onAccount != null) Link("ACCOUNT", "Аккаунт", first = true,
                    summary = accountName ?: "Гость", action = onAccount)
                val previewStatus = previewModeLabel(settings)
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
                Link("OVERLAY", "Overlay", first = true, summary = overlaySizeLabel(settings.overlaySize)) { state.open(SettingsEditorPage.OVERLAY) }
                Link("THEATER", "Кинотеатр", summary = textSizeLabel(settings.theaterStyle.textSizeSp)) { state.open(SettingsEditorPage.THEATER) }
            }
            SettingsEditorPage.OVERLAY, SettingsEditorPage.THEATER -> {
                val mode = if (state.page == SettingsEditorPage.OVERLAY) ChatMode.OVERLAY else ChatMode.THEATER
                SettingsSectionTitle(chatModeLabel(mode))
                val choices = if (mode == ChatMode.OVERLAY) listOf(SettingsChoice.OVERLAY_SIZE, SettingsChoice.TEXT_SIZE,
                    SettingsChoice.OPACITY, SettingsChoice.POSITION) else listOf(SettingsChoice.TEXT_SIZE)
                for (choice in choices) {
                    val selection = SettingsSelection(settings, mode, choice)
                    Parameter(choice.name, selection.title, selection.options[selection.selectedIndex].label, first = choice == choices.first()) {
                        onChange(selection.step(it))
                    }
                }
            }
            SettingsEditorPage.INTERFACE -> {
                SettingsSectionTitle("Интерфейс")
                Parameter("CARD_PREVIEW", "Предпросмотр", previewModeLabel(settings), first = true) { delta ->
                    val mode = (previewModeIndex(settings) + delta).coerceIn(0, 2)
                    onChange(settings.copy(cardPreviewEnabled = mode != 0, previewSoundEnabled = mode == 2))
                }
                Toggle("ANIMATIONS", "Анимации интерфейса и примеров", settings.animationsEnabled) {
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
                    val enabled = !settings.notificationSoundEnabled
                    onChange(settings.copy(notificationSoundEnabled = enabled))
                    if (enabled) onNotificationSound()
                }
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
    ChatMode.OVERLAY -> "Overlay"
    ChatMode.THEATER -> "Кинотеатр"
}


internal fun previewModeIndex(settings: AppSettings) = when {
    !settings.cardPreviewEnabled -> 0
    settings.previewSoundEnabled -> 2
    else -> 1
}
internal fun previewModeLabel(settings: AppSettings) = when (previewModeIndex(settings)) {
    0 -> "Без предпросмотра"
    1 -> "Без звука"
    else -> "Со звуком"
}
