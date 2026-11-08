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
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
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
                SettingChoice("Чат", if (draft.chatEnabled) "Показан" else "Скрыт",
                    Modifier.focusRequester(first)) { draft = draft.copy(chatEnabled = !draft.chatEnabled) }
                SettingChoice("Режим", if (draft.chatMode == ChatMode.OVERLAY) "Поверх видео" else "Кинотеатр") {
                    draft = draft.copy(chatMode = if (draft.chatMode == ChatMode.OVERLAY) ChatMode.THEATER else ChatMode.OVERLAY)
                }
                SettingChoice("Размер текста", "${draft.chatTextSizeSp}") { step ->
                    draft = draft.copy(chatTextSizeSp = (draft.chatTextSizeSp + step * 2).coerceIn(12, 24))
                }
                SettingChoice("Фон чата", "${(draft.chatOpacity * 100).toInt()}%") { step ->
                    draft = draft.copy(chatOpacity = (draft.chatOpacity + step * .1f).coerceIn(0f, 1f))
                }
                if (draft.chatMode == ChatMode.OVERLAY) SettingChoice("Положение", positionLabel(draft.chatPosition)) { step ->
                    val positions = ChatPosition.entries
                    draft = draft.copy(chatPosition = positions[(draft.chatPosition.ordinal + step + positions.size) % positions.size])
                }
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
        Column(Modifier.weight(.57f).padding(top = 44.dp)) {
            Text("Предпросмотр", color = TvColors.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Пример — настройки применятся после сохранения", color = TvColors.Muted,
                fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            SettingsPreview(draft)
            Text("← → Изменить    OK Следующее значение\nНазад — отменить изменения",
                color = TvColors.Muted, fontSize = 12.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 14.dp))
            Spacer(Modifier.weight(1f))
            Text("Twitch 2.0 · ${BuildConfig.VERSION_NAME}\nСамостоятельный клиент для Android TV",
                color = TvColors.Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SettingChoice(label: String, value: String, modifier: Modifier = Modifier, change: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().height(43.dp)
        .onFocusChanged { focused = it.isFocused }
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
        .semantics { role = Role.Button; contentDescription = "$label: $value"; onClick { change(1); true } }
        .focusable()
        .background(if (focused) TvColors.Focus else TvColors.Background, RoundedCornerShape(6.dp))
        .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp,
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
            if (settings.showClock) Text("21:08", color = TvColors.Text, fontSize = 17.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
                    .background(TvColors.Background.copy(alpha = .65f), RoundedCornerShape(4.dp)).padding(4.dp))
            Text("ПРИМЕР", color = TvColors.Text, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
                    .background(TvColors.Accent, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

private fun positionLabel(position: ChatPosition) = when (position) {
    ChatPosition.TOP_LEFT -> "Слева сверху"
    ChatPosition.TOP_RIGHT -> "Справа сверху"
    ChatPosition.BOTTOM_LEFT -> "Слева снизу"
    ChatPosition.BOTTOM_RIGHT -> "Справа снизу"
}
