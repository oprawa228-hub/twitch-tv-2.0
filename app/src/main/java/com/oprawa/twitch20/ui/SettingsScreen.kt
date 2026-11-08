package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oprawa.twitch20.BuildConfig
import com.oprawa.twitch20.chat.ChatMessage
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatMode
import com.oprawa.twitch20.settings.ChatPosition
import com.oprawa.twitch20.settings.ChatStyle
import kotlinx.coroutines.delay

private val SettingsDraftSaver = listSaver<AppSettings, Any>(
    save = { listOf(it.chatMode.name, it.overlayStyle.textSizeSp, it.overlayStyle.opacity,
        it.theaterStyle.textSizeSp, it.theaterStyle.opacity, it.chatPosition.name,
        it.showClock, it.animationsEnabled) },
    restore = { AppSettings(chatMode = ChatMode.valueOf(it[0] as String),
        overlayStyle = ChatStyle(it[1] as Int, it[2] as Float),
        theaterStyle = ChatStyle(it[3] as Int, it[4] as Float),
        chatPosition = ChatPosition.valueOf(it[5] as String), showClock = it[6] as Boolean,
        animationsEnabled = it[7] as Boolean) },
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    var draft by rememberSaveable(settings, stateSaver = SettingsDraftSaver) { mutableStateOf(settings) }
    var transientPreview by remember { mutableStateOf<AppSettings?>(null) }
    val editor = rememberSettingsEditorState()
    val first = remember { FocusRequester() }
    BackHandler { if (!editor.back()) onBack() }
    Row(Modifier.fillMaxSize().background(TvColors.Background).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(.41f).fillMaxHeight()) {
            Text("Настройки", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TvColors.Text)
            Text(if (draft != settings) "Есть несохранённые изменения" else "Выберите раздел или режим чата",
                color = TvColors.Muted, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                ChatSettingsControls(draft, { draft = it }, first, state = editor, onCheckUpdates = onCheckUpdates,
                    onPreviewSettings = { transientPreview = it })
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvAction("Сохранить", { onSave(draft) }, Modifier.weight(1f))
                TvAction("Отмена", onBack, Modifier.weight(1f))
            }
        }
        Column(Modifier.weight(.59f).fillMaxHeight().padding(top = 4.dp).verticalScroll(rememberScrollState())) {
            Text("Предпросмотр", color = TvColors.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            val shownMode = when (editor.page) {
                SettingsEditorPage.OVERLAY -> ChatMode.OVERLAY
                SettingsEditorPage.THEATER -> ChatMode.THEATER
                else -> draft.chatMode
            }
            Text("Пример: ${chatModeLabel(shownMode)}", color = TvColors.Link,
                fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            SettingsPreview((transientPreview ?: draft).copy(chatMode = shownMode))
            Text("После сохранения: ${chatModeLabel(draft.chatMode)}", color = TvColors.Text,
                fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            Text(if (shownMode != draft.chatMode) "Вы редактируете неактивный профиль. Его режим не включится."
                else "Сохранить — применить все изменения. Отмена — оставить прежние настройки.",
                color = TvColors.Muted, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp))
            val hint = if (editor.page == SettingsEditorPage.OVERVIEW) {
                val action = if (editor.overviewFocus == null || editor.overviewFocus?.startsWith("MODE_") == true)
                    "OK включить / выключить · ↓ выбрать раздел" else "OK открыть раздел"
                "$action\nНазад — отменить все изменения"
            } else "Выберите параметр · OK открыть / выбрать\nНазад — к обзору, изменения останутся"
            Text(hint,
                color = TvColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp))
            Text("Twitch 2.0 · ${BuildConfig.VERSION_NAME}",
                color = TvColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
internal fun SettingChoice(label: String, value: String, modifier: Modifier = Modifier,
    accessibilityLabel: String = label, onFocused: () -> Unit = {}, selected: Boolean = false, change: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().heightIn(min = 46.dp)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            when (key.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (key.action == AndroidKeyEvent.ACTION_DOWN) change(if (key.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    true
                }
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (key.action == AndroidKeyEvent.ACTION_UP && !key.isCanceled) change(1)
                    true
                }
                else -> false
            }
        }
        .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "$accessibilityLabel: $value"; onClick { change(1); true } }
        .focusable()
        .background(if (focused) TvColors.Focus else TvColors.Surface, RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2,
            color = if (focused) TvColors.Background else TvColors.Text)
        if (selected) Text("✓", fontSize = 16.sp, color = if (focused) TvColors.Background else TvColors.Link)
        Text(value, Modifier.widthIn(max = 140.dp), fontSize = 15.sp, lineHeight = 20.sp, maxLines = 2,
            color = if (focused) TvColors.Background else TvColors.Muted)
    }
}

@Composable
internal fun SettingsPageLink(label: String, modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}, onClick: () -> Unit) {
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
        .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = label; onClick { onClick(); true } }
        .focusable()
        .background(if (focused) TvColors.Focus else TvColors.Surface, RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2,
            color = if (focused) TvColors.Background else TvColors.Text)
        Text("›", modifier = Modifier.clearAndSetSemantics { }, fontSize = 21.sp,
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
        "Дядя" to "Есть пробитие!", "Nova" to "Заряжай сердечки!", "Pixel" to "Вот это поворот",
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
                    val chatWidth = (960 * ChatLayout.overlayWidthFraction).dp
                    val chatHeight = (540 * ChatLayout.overlayHeightFraction).dp
                    ChatPanel(messages, settings, Modifier
                        .offset(x = (maxWidth - chatWidth) * horizontal, y = (maxHeight - chatHeight) * vertical)
                        .width(chatWidth).height(chatHeight))
                }
            }
            if (settings.showClock) {
                val clockAlignment = Alignment.TopEnd
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (theater) 1f - ChatLayout.theaterWidthFraction else 1f)) {
                    Text("21:08", color = TvColors.Text, fontSize = 17.sp,
                        modifier = Modifier.align(clockAlignment).padding(20.dp)
                            .background(TvColors.Background.copy(alpha = .65f), RoundedCornerShape(4.dp)).padding(4.dp))
                }
            }
            Text("ПРИМЕР", color = TvColors.Text, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
                    .background(TvColors.Accent, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}
