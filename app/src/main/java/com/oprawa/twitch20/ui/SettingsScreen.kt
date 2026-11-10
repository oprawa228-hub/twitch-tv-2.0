package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oprawa.twitch20.chat.ChatMessage
import com.oprawa.twitch20.BuildConfig
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatPosition
import com.oprawa.twitch20.settings.ClockPlacement
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    onAccount: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null,
    accountProfile: Channel? = null,
    onNotificationExampleSound: () -> Unit = {},
    accountDisplayName: String? = accountProfile?.name,
    updateState: com.oprawa.twitch20.update.UpdateState = com.oprawa.twitch20.update.UpdateState(),
    onAboutFocused: () -> Unit = {},
) {
    val editor = rememberSettingsEditorState()
    val aboutFocused = editor.previewTarget() == SettingsPreviewTarget.ABOUT
    LaunchedEffect(aboutFocused) { if (aboutFocused) onAboutFocused() }
    val first = remember { FocusRequester() }
    BackHandler { if (!editor.back()) onBack() }
    Row(Modifier.fillMaxSize().background(TvColors.Background).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(.41f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Настройки", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TvColors.Text)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                ChatSettingsControls(settings, onSave, first, state = editor, onCheckUpdates = onCheckUpdates,
                    onAccount = onAccount, onAbout = onAbout, accountName = accountDisplayName,
                    onNotificationSound = onNotificationExampleSound)
            }
            if (editor.page == SettingsEditorPage.OVERVIEW) DinoDecoration(settings.animationsEnabled)
        }
        Column(Modifier.weight(.59f).fillMaxHeight().padding(top = 4.dp).verticalScroll(rememberScrollState())) {
            val target = editor.previewTarget()
            val title = when (target) {
                SettingsPreviewTarget.CATALOG -> "Предпросмотр эфиров"
                SettingsPreviewTarget.ANIMATIONS -> "Анимации"
                SettingsPreviewTarget.CLOCK -> "Часы"
                SettingsPreviewTarget.NOTIFICATIONS -> "Новый эфир"
                SettingsPreviewTarget.ACCOUNT -> "Аккаунт Twitch"
                SettingsPreviewTarget.ABOUT -> "Twitch 2.0"
                SettingsPreviewTarget.VIEWING -> chatModeLabel(settings.chatMode)
                SettingsPreviewTarget.OVERLAY -> "Overlay"
                SettingsPreviewTarget.THEATER -> "Кинотеатр"
            }
            Text(title, color = TvColors.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp))
            val preview = settings
            when (target) {
                SettingsPreviewTarget.CATALOG -> CatalogSettingsPreview(preview)
                SettingsPreviewTarget.CLOCK -> {
                    Row(Modifier.fillMaxWidth().background(TvColors.Surface, TvShapes.Control).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("На главной", Modifier.weight(1f), color = TvColors.Text, fontSize = 16.sp)
                        TvClock(preview)
                    }
                    Text("В плеере", color = TvColors.Muted, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    SettingsPreview(if (preview.clockPlacement == ClockPlacement.CHAT && !preview.chatEnabled)
                        preview.copy(chatMode = ChatMode.OVERLAY) else preview)
                    if (preview.clockPlacement == ClockPlacement.CHAT && !preview.chatEnabled) {
                        Text("Пример размещения · чат выключен", color = TvColors.Muted, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
                SettingsPreviewTarget.ANIMATIONS -> {
                    SettingsPreview(preview.copy(chatMode = ChatMode.OFF))
                    Text(if (preview.animationsEnabled) "Движение интерфейса и примеров включено" else "Интерфейс и примеры без анимации. Видео продолжает воспроизводиться.",
                        color = TvColors.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp))
                }
                SettingsPreviewTarget.VIEWING -> SettingsPreview(preview)
                SettingsPreviewTarget.OVERLAY, SettingsPreviewTarget.THEATER -> {
                    val mode = if (target == SettingsPreviewTarget.OVERLAY) ChatMode.OVERLAY else ChatMode.THEATER
                    val profile = preview.copy(chatMode = mode)
                    SettingsPreview(profile)
                    if (mode != settings.chatMode) Text("Профиль выключен · показан пример", color = TvColors.Muted, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
                SettingsPreviewTarget.NOTIFICATIONS -> NotificationSettingsPreview(preview)
                SettingsPreviewTarget.ACCOUNT -> AccountSettingsPreview(accountDisplayName, accountProfile?.avatarUrl)
                SettingsPreviewTarget.ABOUT -> ReleaseOverview(updateState)
            }
        }
    }
}

@Composable
internal fun SettingChoice(label: String, value: String, modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}, change: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().heightIn(min = 46.dp)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            when (key.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (key.action == AndroidKeyEvent.ACTION_DOWN && key.repeatCount == 0)
                        change(if (key.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    true
                }
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    true
                }
                else -> false
            }
        }
        .semantics(mergeDescendants = true) {
            contentDescription = "$label: $value"
        }
        .focusable()
        .background(if (focused) TvColors.Focus else TvColors.Surface, RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2,
            color = if (focused) TvColors.Background else TvColors.Text)
        Text(if (focused) "‹  $value  ›" else value, Modifier.widthIn(max = 158.dp), fontSize = 15.sp, lineHeight = 20.sp, maxLines = 2,
            color = if (focused) TvColors.Background else TvColors.Muted)
    }
}

@Composable
internal fun SettingsPageLink(label: String, modifier: Modifier = Modifier,
    summary: String? = null, expanded: Boolean? = null, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().heightIn(min = 46.dp)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode in setOf(AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)) {
                if (key.action == AndroidKeyEvent.ACTION_UP && !key.isCanceled) onClick()
                true
            } else false
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = if (summary == null) label else "$label: $summary"
            onClick { onClick(); true }
        }
        .focusable()
        .background(if (focused) TvColors.Focus else TvColors.Surface, RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2,
                color = if (focused) TvColors.Background else TvColors.Text)
            if (summary != null) Text(summary, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2,
                color = if (focused) TvColors.Background else TvColors.Muted)
        }
        Text(when (expanded) { true -> "⌃"; false -> "⌄"; null -> "›" },
            modifier = Modifier.clearAndSetSemantics { }, fontSize = 21.sp,
            color = if (focused) TvColors.Background else TvColors.Muted)
    }
}

@Composable
private fun SettingsPreview(settings: AppSettings) {
    var tick by remember { mutableIntStateOf(0) }
    val motionEnabled = rememberPreviewMotionEnabled(settings.animationsEnabled)
    // This coroutine is cancelled when the example leaves composition.
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) while (true) {
            delay(1800)
            tick++
        }
    }
    val samples = listOf(
        "Дядя" to "Есть пробитие!", "Зритель" to "Добрый вечер!", "Pixel" to "Вот это поворот",
        "Luna" to "Старая гвардия!!!", "Tank" to "WW завоз", "Nova" to "Хороший эфир",
    )
    val messages = remember(tick) {
        (0..7).map { index ->
            val sample = samples[(index + tick) % samples.size]
            ChatMessage("preview-${tick + index}", sample.first, sample.second,
                listOf("#bf94ff", "#62f5c6", "#ffb16b", "#93c7ff")[index % 4])
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(8.dp)).border(1.dp, TvColors.Raised, RoundedCornerShape(8.dp))) {
        val factor = maxWidth.value / 960f
        Box(Modifier.requiredSize(960.dp, 540.dp).graphicsLayer { scaleX = factor; scaleY = factor }
            .background(Color.Black)) {
            val theater = settings.chatEnabled && settings.chatMode == ChatMode.THEATER
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (theater) 1f - ChatLayout.theaterWidthFraction else 1f)) {
                PreviewScene(animationsEnabled = settings.animationsEnabled,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.Center),
                )
            }
            if (settings.chatEnabled) {
                if (theater) ChatPanel(messages, settings, Modifier.align(Alignment.CenterEnd)
                    .fillMaxWidth(ChatLayout.theaterWidthFraction).fillMaxHeight())
                else BoxWithConstraints(Modifier.fillMaxSize().padding(start=ChatLayout.inset, end=ChatLayout.inset,
                    top=52.dp, bottom=ChatLayout.inset)) {
                    val right = settings.chatPosition == ChatPosition.TOP_RIGHT || settings.chatPosition == ChatPosition.BOTTOM_RIGHT
                    val bottom = settings.chatPosition == ChatPosition.BOTTOM_LEFT || settings.chatPosition == ChatPosition.BOTTOM_RIGHT
                    val horizontal by animateFloatAsState(if (right) 1f else 0f,
                        animationSpec = if (motionEnabled) tween(200, easing = FastOutSlowInEasing) else snap(), label = "preview-chat-horizontal")
                    val vertical by animateFloatAsState(if (bottom) 1f else 0f,
                        animationSpec = if (motionEnabled) tween(200, easing = FastOutSlowInEasing) else snap(), label = "preview-chat-vertical")
                    val chatWidth by animateDpAsState((960 * ChatLayout.overlayWidth(settings)).dp,
                        animationSpec = if (motionEnabled) tween(200, easing = FastOutSlowInEasing) else snap(),
                        label = "preview-chat-width")
                    val chatHeight by animateDpAsState(minOf((540 * ChatLayout.overlayHeight(settings)).dp, maxHeight),
                        animationSpec = if (motionEnabled) tween(200, easing = FastOutSlowInEasing) else snap(),
                        label = "preview-chat-height")
                    ChatPanel(messages, settings, Modifier
                        .offset(x = (maxWidth - chatWidth) * horizontal, y = (maxHeight - chatHeight) * vertical)
                        .width(chatWidth).height(chatHeight))
                }
            }
            if (settings.clockPlacement == ClockPlacement.VIDEO || !settings.chatEnabled) {
                val clockAlignment = Alignment.TopEnd
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (theater) 1f - ChatLayout.theaterWidthFraction else 1f)) {
                    TvClock(settings, Modifier.align(clockAlignment).padding(20.dp))
                }
            }
            Text("ПРИМЕР", color = TvColors.Text, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
                    .background(TvColors.Accent, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

/** An explanatory catalog crop: same card aspect, typography and focus colors, no remote media. */
@Composable
private fun CatalogSettingsPreview(settings: AppSettings) {
    val motionEnabled = rememberPreviewMotionEnabled(settings.animationsEnabled)
    val largeText = LocalDensity.current.fontScale > 1.25f
    val columns = if (largeText) 2 else 3
    var previewReady by remember { mutableStateOf(false) }
    LaunchedEffect(settings.cardPreviewEnabled) {
        previewReady = false
        if (settings.cardPreviewEnabled) {
            delay(700)
            previewReady = true
        }
    }
    SettingsExampleStage {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Прямые эфиры", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TvColors.Text,
                    modifier = Modifier.weight(1f))
                TvClock(settings)
            }
            repeat(if (largeText) 1 else 2) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(columns) { column ->
                        val first = row == 0 && column == 0
                        val index = row * columns + column
                        Column(Modifier.weight(1f).clip(TvShapes.Control)
                            .background(TvColors.Surface)
                            .border(2.dp, if (first) TvColors.Focus else Color.Transparent, TvShapes.Control)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))) {
                                if (first) PreviewScene(animationsEnabled = previewReady && motionEnabled,
                                    modifier = Modifier.fillMaxSize())
                                else Box(Modifier.fillMaxSize().background(listOf(
                                    Color(0xFF343051), Color(0xFF253E49), Color(0xFF453848))[index % 3]))
                                Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(9.dp)
                                    .background(TvColors.Live, CircleShape))
                                Text(listOf("242", "188", "156", "96", "82", "74")[index], color = TvColors.Text,
                                    fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                        .background(TvColors.Background.copy(alpha = .75f), RoundedCornerShape(3.dp)).padding(3.dp))
                            }
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(listOf("Дядя на танке", "Nova", "Pixel", "Luna", "Orbit", "Tank")[index],
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TvColors.Text, maxLines = 1)
                                Text(if (first) "Заряжай сердечки!" else "Встречаемся в эфире", fontSize = 12.sp,
                                    color = TvColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (first) "Мир Танков" else "Общение", fontSize = 11.sp, color = TvColors.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
    Text(when {
        !settings.cardPreviewEnabled -> "Карточки остаются неподвижными"
        !motionEnabled -> "Предпросмотр включён · движение примера отключено"
        settings.previewSoundEnabled -> "На выделенной карточке включён звук эфира"
        else -> "На выделенной карточке звук эфира выключен"
    }, color = TvColors.Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun NotificationSettingsPreview(settings: AppSettings) {
    SettingsExampleStage {
        Box(Modifier.fillMaxSize().clearAndSetSemantics {
            contentDescription = "Пример уведомления о новом эфире"
        }) {
            PreviewScene(animationsEnabled = settings.animationsEnabled, modifier = Modifier.fillMaxSize())
            LiveNotificationBanner(Channel(id = "settings-example", login = "settings_example",
                name = "Дядя на танке", title = "", category = "Мир Танков", imageUrl = "", liveMessage = "Начал трансляцию"),
                Modifier.align(Alignment.TopCenter).padding(24.dp))
        }
    }
    Text(if (settings.followNotificationsEnabled) "Во время просмотра, когда канал из подписок начинает эфир. Нужен вход в Twitch."
        else "Уведомления о новых эфирах скрыты", color = TvColors.Muted, fontSize = 14.sp, lineHeight = 20.sp,
        modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun AccountSettingsPreview(displayName: String?, avatarUrl: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (displayName != null && !avatarUrl.isNullOrBlank()) {
            AsyncImage(avatarUrl, contentDescription = null,
                modifier = Modifier.size(56.dp).clip(CircleShape))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(displayName ?: "Гость", color = TvColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (displayName == null) "Войдите, чтобы открыть подписки" else "Аккаунт Twitch подключён",
                color = TvColors.Muted, fontSize = 16.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun SettingsExampleStage(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(8.dp)).border(1.dp, TvColors.Raised, RoundedCornerShape(8.dp))) {
        val factor = maxWidth.value / 960f
        Box(Modifier.requiredSize(960.dp, 540.dp).graphicsLayer { scaleX = factor; scaleY = factor }
            .background(TvColors.Background)) {
            content()
            Text("ПРИМЕР", color = TvColors.Text, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    .background(TvColors.Accent, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}


@Composable
private fun ReleaseOverview(state: com.oprawa.twitch20.update.UpdateState) {
    Column(Modifier.fillMaxWidth().background(TvColors.Surface, TvShapes.Panel).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Установлено · ${BuildConfig.VERSION_NAME}", color = TvColors.Link, fontSize = 14.sp)
        Text(state.available?.let { "Доступна версия ${it.versionName}" } ?: when (state.status) {
            "idle", "checking" -> "Проверяем обновления…"
            else -> state.message
        }, color = TvColors.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        val lastPublished = state.latestPublished?.takeIf { state.available == null && it.versionName != BuildConfig.VERSION_NAME }
        if (lastPublished != null) {
            Text("Последний опубликованный выпуск · ${lastPublished.versionName}",
                color = TvColors.Link, fontSize = 14.sp)
        }
        val notes = state.available?.notes ?: state.currentNotes ?: lastPublished?.notes
        if (!notes.isNullOrBlank()) {
            Text("Что нового", color = TvColors.Link, fontSize = 15.sp)
            Text(formatReleaseNotes(notes), color = TvColors.Muted, fontSize = 15.sp,
                lineHeight = 21.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
        }
    }
}
