package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
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
    var previewMode by remember { mutableStateOf<ChatMode?>(null) }
    val first = remember { FocusRequester() }
    BackHandler { onBack() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Row(Modifier.fillMaxSize().background(TvColors.Background).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(.43f).fillMaxHeight()) {
            Text("Настройки", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TvColors.Text)
            Text(if (draft != settings) "Есть несохранённые изменения" else "Выберите параметр",
                color = TvColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ChatSettingsControls(draft, { draft = it }, first) { previewMode = it }
                SettingChoice("Часы", if (draft.showClock) "Показаны" else "Скрыты") {
                    draft = draft.copy(showClock = !draft.showClock)
                }
                SettingChoice("Анимации", if (draft.animationsEnabled) "Включены" else "Выключены") {
                    draft = draft.copy(animationsEnabled = !draft.animationsEnabled)
                }
                TvAction("Проверить обновления", onCheckUpdates, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvAction("Сохранить", { onSave(draft) })
                TvAction("Отмена", onBack)
            }
        }
        Column(Modifier.weight(.57f).padding(top = 24.dp)) {
            Text("Предпросмотр", color = TvColors.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Пример — настройки применятся после сохранения", color = TvColors.Muted,
                fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            val shownMode = previewMode ?: draft.chatMode
            Text(chatModeLabel(shownMode) + if (shownMode != draft.chatMode) " · только пример" else "",
                color = TvColors.Link, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            SettingsPreview(draft.copy(chatMode = shownMode))
            Text("При сохранении: ${chatModeLabel(draft.chatMode)}", color = TvColors.Muted,
                fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Text("← → Изменить    OK Следующее значение\nНазад — отменить изменения",
                color = TvColors.Muted, fontSize = 12.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 14.dp))
            Spacer(Modifier.weight(1f))
            Text("Twitch 2.0 · ${BuildConfig.VERSION_NAME}",
                color = TvColors.Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun SettingChoice(label: String, value: String, modifier: Modifier = Modifier,
    accessibilityLabel: String = label, onFocused: () -> Unit = {}, change: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().height(43.dp)
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
        .semantics { role = Role.Button; contentDescription = "$accessibilityLabel: $value"; onClick { change(1); true } }
        .focusable()
        .background(if (focused) TvColors.Focus else TvColors.Background, RoundedCornerShape(6.dp))
        .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp,
            color = if (focused) TvColors.Background else TvColors.Text)
        Text(value, fontSize = 13.sp, color = if (focused) TvColors.Background else TvColors.Muted)
    }
}

@Composable
private fun SettingsPreview(settings: AppSettings) {
    var tick by remember { mutableIntStateOf(0) }
    // This coroutine is cancelled when the example leaves composition.
    LaunchedEffect(settings.animationsEnabled) {
        if (settings.animationsEnabled) while (true) {
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
    val context = LocalContext.current
    val scene = remember(context) {
        ImageRequest.Builder(context).data("file:///android_asset/preview/tanks.svg")
            .decoderFactory(SvgDecoder.Factory()).build()
    }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(8.dp)).border(1.dp, TvColors.Raised, RoundedCornerShape(8.dp))) {
        val factor = maxWidth.value / 960f
        Box(Modifier.requiredSize(960.dp, 540.dp).graphicsLayer { scaleX = factor; scaleY = factor }
            .background(Color.Black)) {
            val theater = settings.chatEnabled && settings.chatMode == ChatMode.THEATER
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (theater) 1f - ChatLayout.theaterWidthFraction else 1f)) {
                AsyncImage(scene, contentDescription = "Авторский пример: танк и стример",
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.Center),
                    contentScale = ContentScale.Fit)
            }
            if (settings.chatEnabled) {
                if (theater) ChatPanel(messages, settings, Modifier.align(Alignment.CenterEnd)
                    .fillMaxWidth(ChatLayout.theaterWidthFraction).fillMaxHeight())
                else Box(Modifier.fillMaxSize().padding(ChatLayout.inset)) {
                    ChatPanel(messages, settings, Modifier.align(ChatLayout.alignment(settings.chatPosition))
                        .width((960 * ChatLayout.overlayWidthFraction).dp)
                        .height((540 * ChatLayout.overlayHeightFraction).dp))
                }
            }
            if (settings.showClock) {
                val clockAlignment = if (settings.chatEnabled && settings.chatMode == ChatMode.OVERLAY &&
                    settings.chatPosition == ChatPosition.TOP_RIGHT) Alignment.TopStart else Alignment.TopEnd
                Text("21:08", color = TvColors.Text, fontSize = 17.sp,
                    modifier = Modifier.align(clockAlignment).padding(20.dp)
                        .background(TvColors.Background.copy(alpha = .65f), RoundedCornerShape(4.dp)).padding(4.dp))
            }
            Text("ПРИМЕР", color = TvColors.Text, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
                    .background(TvColors.Accent, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}
