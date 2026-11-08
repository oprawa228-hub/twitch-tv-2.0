package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.tv.material3.Text
import com.oprawa.twitch20.playback.PlaybackSession
import com.oprawa.twitch20.playback.PlaybackState
import com.oprawa.twitch20.playback.PlaybackStatus
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatPosition
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PlayerBackground = Color(0xFF0E0E10)
private val PlayerPanel = Color(0xFF18181B)
private val PlayerFocus = Color(0xFFA970FF)
private val PlayerText = Color(0xFFF7F7F8)
private val PlayerMuted = Color(0xFFADADB8)

/** Owns UI layers only; the caller owns and releases [session]. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    session: PlaybackSession,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    chatContent: (@Composable (AppSettings) -> Unit)? = null,
    settings: AppSettings = AppSettings(),
    onSettings: () -> Unit = {},
    onSettingsChange: (AppSettings) -> Unit = {},
    onRetry: () -> Unit = { session.retry() },
) {
    val state by session.state.collectAsStateWithLifecycle()
    var hudVisible by remember(session) { mutableStateOf(true) }
    var qualityOpen by remember(session) { mutableStateOf(false) }
    var chatSettingsOpen by remember(session) { mutableStateOf(false) }
    var draftSettings by remember(session) { mutableStateOf(settings) }
    var settingsFromChat by remember { mutableStateOf(false) }
    var interaction by remember { mutableLongStateOf(0) }
    var swallowedKey by remember { mutableStateOf<Int?>(null) }
    val screenFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val autoFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val chatFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val draftFocus = remember { FocusRequester() }
    val effectiveSettings = if (chatSettingsOpen) draftSettings else settings
    val panelOpen = qualityOpen || chatSettingsOpen
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(effectiveSettings.showClock) {
        if (effectiveSettings.showClock) while (true) {
            clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }

    fun closeQuality() { qualityOpen = false; hudVisible = true; interaction++ }
    fun openChatSettings(fromChat: Boolean) {
        draftSettings = settings
        settingsFromChat = fromChat
        qualityOpen = false
        chatSettingsOpen = true
        interaction++
    }
    fun closeChatSettings(save: Boolean) {
        if (save) onSettingsChange(draftSettings)
        chatSettingsOpen = false
        hudVisible = true
        interaction++
    }

    BackHandler {
        when {
            chatSettingsOpen -> closeChatSettings(false)
            qualityOpen -> closeQuality()
            hudVisible && state.status != PlaybackStatus.Error -> { hudVisible = false; interaction++ }
            else -> onBack()
        }
    }

    LaunchedEffect(qualityOpen) {
        if (qualityOpen) autoFocus.requestFocus()
    }
    LaunchedEffect(chatSettingsOpen) {
        if (chatSettingsOpen) draftFocus.requestFocus()
    }
    LaunchedEffect(hudVisible, panelOpen, state.status == PlaybackStatus.Error) {
        when {
            panelOpen -> Unit
            state.status == PlaybackStatus.Error -> retryFocus.requestFocus()
            hudVisible -> pauseFocus.requestFocus()
            else -> screenFocus.requestFocus()
        }
    }
    LaunchedEffect(hudVisible, interaction, panelOpen, state.isPlaying) {
        if (hudVisible && !panelOpen && state.isPlaying) {
            delay(6_000)
            hudVisible = false
        }
    }

    val theater = effectiveSettings.chatMode == ChatMode.THEATER
    val showSideChat = chatContent != null && effectiveSettings.chatEnabled && theater && !qualityOpen
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val playbackAreaWidth = maxWidth - if (panelOpen) 300.dp else 0.dp
    Row(Modifier.fillMaxSize().background(PlayerBackground)
        .focusRequester(screenFocus)
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val code = native.keyCode
            if (swallowedKey == code) {
                if (native.action == AndroidKeyEvent.ACTION_UP) swallowedKey = null
                true
            } else if (!hudVisible && !panelOpen && code in revealKeys) {
                if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                    swallowedKey = code
                    hudVisible = true
                    interaction++
                }
                true
            } else {
                if (native.action == AndroidKeyEvent.ACTION_DOWN) interaction++
                false
            }
        }.focusable()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val contentWidth = maxWidth
            val contentHeight = maxHeight
            // The ContentFrame stays at the same composition location when panels change size.
            Column(Modifier.fillMaxSize().padding(if (panelOpen) 24.dp else 0.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ContentFrame(player = session.player, modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit)
                }
                if (panelOpen) {
                    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)) {
                        Text(title, color = PlayerText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, color = PlayerMuted, fontSize = 13.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (chatContent != null && effectiveSettings.chatEnabled && !theater && !qualityOpen) {
                Box(Modifier.fillMaxSize().padding(ChatLayout.inset)) {
                    Box(Modifier.align(ChatLayout.alignment(effectiveSettings.chatPosition))
                        .width(contentWidth * ChatLayout.overlayWidthFraction)
                        .height(contentHeight * ChatLayout.overlayHeightFraction)) { chatContent(effectiveSettings) }
                }
            }
            if (effectiveSettings.showClock && !qualityOpen) {
                val clockAlignment = if (effectiveSettings.chatEnabled &&
                    effectiveSettings.chatMode == ChatMode.OVERLAY &&
                    effectiveSettings.chatPosition == ChatPosition.TOP_RIGHT) Alignment.TopStart else Alignment.TopEnd
                Text(clockText, color = PlayerText, fontSize = 17.sp,
                    modifier = Modifier.align(clockAlignment).padding(20.dp)
                        .background(PlayerBackground.copy(alpha = .65f), RoundedCornerShape(4.dp)).padding(4.dp))
            }

            if (state.status == PlaybackStatus.Buffering) {
                Text(if (state.firstFrameMs == null) "Подключение к видео…" else "Буферизация…",
                    color = PlayerText, fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                        .background(PlayerPanel.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp))
            }

            if (state.status == PlaybackStatus.Error) {
                Column(Modifier.align(Alignment.Center).width(440.dp)
                    .background(PlayerPanel, RoundedCornerShape(8.dp)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Видео недоступно", color = PlayerText, fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(state.error ?: "Попробуйте открыть канал заново.", color = PlayerMuted,
                        fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerAction("Повторить", Modifier.focusRequester(retryFocus)) { onRetry() }
                        PlayerAction("Назад") { onBack() }
                    }
                }
            } else if (hudVisible && !panelOpen) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(PlayerBackground.copy(alpha = 0.96f)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (state.isLive) "LIVE" else "ЗАПИСЬ", color = PlayerFocus,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(title, color = PlayerText, fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        if (state.activeQuality.isNotEmpty()) Text(state.activeQuality,
                            color = PlayerMuted, fontSize = 12.sp)
                    }
                    if (subtitle.isNotBlank()) Text(subtitle, color = PlayerMuted, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!state.isLive) VodProgress(state)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        PlayerAction(if (state.isPlaying) "Пауза" else if (state.isLive) "К эфиру" else "Смотреть",
                            Modifier.focusRequester(pauseFocus)) {
                            if (state.isPlaying) session.pause() else session.resume()
                        }
                        if (!state.isLive) {
                            PlayerAction("−10 с") { session.seekBy(-10_000) }
                            PlayerAction("+10 с") { session.seekBy(10_000) }
                        }
                        PlayerAction("Качество", Modifier.focusRequester(qualityFocus)) { qualityOpen = true }
                        if (chatContent != null) {
                            PlayerAction(if (settings.chatEnabled) "Чат: вкл" else "Чат: выкл",
                                Modifier.focusRequester(chatFocus), onLongClick = { openChatSettings(true) }) {
                                onSettingsChange(settings.copy(chatEnabled = !settings.chatEnabled))
                            }
                            PlayerAction(if (theater) "Кинотеатр: вкл" else "Кинотеатр") {
                                onSettingsChange(settings.copy(chatEnabled = true,
                                    chatMode = if (theater) ChatMode.OVERLAY else ChatMode.THEATER))
                            }
                        }
                        PlayerAction("Настройки", Modifier.focusRequester(settingsFocus)) { openChatSettings(false) }
                    }
                }
            }
        }

        if (showSideChat) {
            Box(Modifier.width(playbackAreaWidth * ChatLayout.theaterWidthFraction)
                .fillMaxHeight().background(PlayerPanel)) { chatContent(effectiveSettings) }
        }
        if (qualityOpen) {
            Column(Modifier.width(300.dp).fillMaxHeight().background(PlayerPanel).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Качество видео", color = PlayerText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("Авто подбирает качество по скорости сети", color = PlayerMuted, fontSize = 12.sp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        PlayerAction(if (state.selectedQualityId == null) "✓ Авто" else "Авто",
                            Modifier.fillMaxWidth().focusRequester(autoFocus)) {
                            session.selectQuality(null); closeQuality()
                        }
                    }
                    items(state.qualityOptions, key = { it.id }) { quality ->
                        PlayerAction(if (quality.id == state.selectedQualityId) "✓ ${quality.label}" else quality.label,
                            Modifier.fillMaxWidth()) {
                            session.selectQuality(quality.id); closeQuality()
                        }
                    }
                    if (state.qualityOptions.isEmpty()) item {
                        Text("Варианты появятся после загрузки видео", color = PlayerMuted,
                            fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                PlayerAction("Назад", Modifier.fillMaxWidth()) { closeQuality() }
            }
        } else if (chatSettingsOpen) {
            Column(Modifier.width(300.dp).fillMaxHeight().background(PlayerPanel).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Настройки просмотра", color = PlayerText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("Изменения видны сразу. Сохраните результат или отмените кнопкой Назад.",
                    color = PlayerMuted, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        PlayerAction(if (draftSettings.chatEnabled) "Чат: включён" else "Чат: выключен",
                            Modifier.fillMaxWidth().focusRequester(draftFocus)) {
                            draftSettings = draftSettings.copy(chatEnabled = !draftSettings.chatEnabled)
                        }
                    }
                    item {
                        PlayerAction(if (draftSettings.chatMode == ChatMode.OVERLAY) "Режим: поверх видео" else "Режим: отдельная область",
                            Modifier.fillMaxWidth()) {
                            draftSettings = draftSettings.copy(chatMode = if (draftSettings.chatMode == ChatMode.OVERLAY)
                                ChatMode.THEATER else ChatMode.OVERLAY)
                        }
                    }
                    item {
                        PlayerAction("Текст: ${draftSettings.chatTextSizeSp}", Modifier.fillMaxWidth()) {
                            draftSettings = draftSettings.copy(chatTextSizeSp =
                                if (draftSettings.chatTextSizeSp >= 24) 12 else draftSettings.chatTextSizeSp + 2)
                        }
                    }
                    item {
                        PlayerAction("Фон: ${(draftSettings.chatOpacity * 100).toInt()}%", Modifier.fillMaxWidth()) {
                            draftSettings = draftSettings.copy(chatOpacity =
                                if (draftSettings.chatOpacity >= 1f) 0f else (draftSettings.chatOpacity + .1f).coerceAtMost(1f))
                        }
                    }
                    item {
                        PlayerAction("Положение: ${chatPositionLabel(draftSettings.chatPosition)}", Modifier.fillMaxWidth()) {
                            val positions = ChatPosition.entries
                            draftSettings = draftSettings.copy(chatPosition = positions[(draftSettings.chatPosition.ordinal + 1) % positions.size])
                        }
                    }
                    item {
                        PlayerAction(if (draftSettings.showClock) "Часы: включены" else "Часы: выключены", Modifier.fillMaxWidth()) {
                            draftSettings = draftSettings.copy(showClock = !draftSettings.showClock)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerAction("Сохранить", Modifier.weight(1f)) { closeChatSettings(true) }
                    PlayerAction("Отмена", Modifier.weight(1f)) { closeChatSettings(false) }
                }
            }
        }
    }
    }

    // Restore the invoking quality action after the panel disappears.
    var wasQualityOpen by remember { mutableStateOf(false) }
    LaunchedEffect(qualityOpen) {
        if (wasQualityOpen && !qualityOpen && state.status != PlaybackStatus.Error) qualityFocus.requestFocus()
        wasQualityOpen = qualityOpen
    }
    var wasSettingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(chatSettingsOpen) {
        if (wasSettingsOpen && !chatSettingsOpen && state.status != PlaybackStatus.Error) {
            if (settingsFromChat) chatFocus.requestFocus() else settingsFocus.requestFocus()
        }
        wasSettingsOpen = chatSettingsOpen
    }
}

private fun chatPositionLabel(position: ChatPosition) = when (position) {
    ChatPosition.TOP_LEFT -> "слева сверху"
    ChatPosition.TOP_RIGHT -> "справа сверху"
    ChatPosition.BOTTOM_LEFT -> "слева снизу"
    ChatPosition.BOTTOM_RIGHT -> "справа снизу"
}

@Composable
private fun VodProgress(state: PlaybackState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF3A3A43))) {
            if (state.durationMs > 0) Box(Modifier.fillMaxWidth(
                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f))
                .height(3.dp).background(PlayerFocus))
        }
        Text("${timeText(state.positionMs)} / ${if (state.durationMs > 0) timeText(state.durationMs) else "—"}",
            color = PlayerMuted, fontSize = 12.sp)
    }
}

@Composable
private fun PlayerAction(label: String, modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longTriggered by remember { mutableStateOf(false) }
    Box(modifier.onFocusChanged { focused = it.isFocused; if (!it.isFocused) longTriggered = false }
        .background(if (focused) PlayerFocus else Color(0xFF26262C), RoundedCornerShape(5.dp))
        .border(1.dp, if (focused) Color(0xFFD9BCFF) else Color.Transparent, RoundedCornerShape(5.dp))
        .onKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode in confirmKeys) {
                val held = key.eventTime - key.downTime >= 500
                if (key.action == AndroidKeyEvent.ACTION_DOWN && held && !longTriggered) {
                    longTriggered = true
                    onLongClick?.invoke()
                }
                if (key.action == AndroidKeyEvent.ACTION_UP) {
                    if (!longTriggered) { if (held) onLongClick?.invoke() else onClick() }
                    longTriggered = false
                }
                true // A held OK never produces a second short action on release.
            } else false
        }
        .semantics { role = Role.Button; onClick { onClick(); true } }
        .focusable().padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart) {
        Text(label, color = if (focused) PlayerBackground else PlayerText, fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val confirmKeys = setOf(AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)
private val revealKeys = confirmKeys + setOf(AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)

private fun timeText(timeMs: Long): String {
    val seconds = (timeMs / 1_000).coerceAtLeast(0)
    return if (seconds >= 3_600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600,
        (seconds / 60) % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
