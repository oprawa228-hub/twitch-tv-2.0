package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.oprawa.twitch20.data.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** UI state only: one host-owned subscription monitor supplies both the rail and catalog. */
data class FollowingSidebarState(
    val channels: List<Channel> = emptyList(),
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val initialized: Boolean = false,
    val error: String? = null,
)

private data class RailEntry(val key: String, val label: String)

/** Search above the library, live followed channels below it, Settings alone at the bottom. */
@Composable
fun NavigationRail(
    selected: String,
    onSelect: (String) -> Unit,
    accountName: String,
    modifier: Modifier = Modifier,
    onFocusOwnership: (Boolean) -> Unit = {},
    onPreview: (String) -> Unit = {},
    following: FollowingSidebarState = FollowingSidebarState(signedIn = accountName.isNotBlank()),
    onChannelPreview: (Channel) -> Unit = {},
    onChannelSelect: (Channel) -> Unit = {},
    onChannelDetails: (Channel) -> Unit = {},
) {
    val entries = listOf(RailEntry("search", "Поиск"), RailEntry("following", "Каналы"),
        RailEntry("recordings", "Записи"), RailEntry("categories", "Категории"),
        RailEntry("streams", "Рекомендации"))
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var expanded by remember { mutableStateOf(false) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    // Live viewer-count changes must not reorder the user's targets under the D-pad.
    var order by remember(following.signedIn, accountName) { mutableStateOf<List<String>>(emptyList()) }
    val liveById = following.channels.filter { it.live }.associateBy { it.id }
    val liveIds = order.filter { it in liveById } + liveById.keys.filterNot { it in order }
    val live = liveIds.mapNotNull(liveById::get)
    // Capture before node removal can report a transient loss of focus to the parent.
    val removedFocusedChannel = focusedKey?.takeIf {
        expanded && it.startsWith("live:") && it.removePrefix("live:") !in liveById
    }?.removePrefix("live:")
    val requesterCache = remember { mutableMapOf<String, FocusRequester>() }
    val focusKeys = entries.map { it.key } + liveIds.map { "live:$it" } + "settings"
    val requesters = remember(focusKeys) {
        requesterCache.apply { focusKeys.forEach { getOrPut(it) { FocusRequester() } } }
    }
    fun focusLive(index: Int) {
        scope.launch {
            if (index !in live.indices) return@launch
            listState.scrollToItem(index)
            withFrameNanos { }
            requesters["live:${live[index].id}"]?.requestFocus()
        }
    }
    LaunchedEffect(liveIds) {
        val oldOrder = order
        order = liveIds
        if (removedFocusedChannel != null) {
            if (live.isEmpty()) requesters.getValue("following").requestFocus()
            else focusLive(oldOrder.indexOf(removedFocusedChannel).coerceIn(0, live.lastIndex))
        }
    }
    Column(modifier.width(if (expanded) 208.dp else 84.dp).fillMaxHeight()
        .background(TvColors.Surface)
        .onFocusChanged {
            expanded = it.hasFocus
            onFocusOwnership(it.hasFocus)
        }
        .onPreviewKeyEvent {
            if (it.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                it.nativeKeyEvent.keyCode in listOf(AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT)) onFocusOwnership(true)
            false
        }
        .focusGroup().padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEachIndexed { index, entry ->
            if (index == 1) Spacer(Modifier.height(12.dp))
            RailRow(entry.label, entry.key == selected, expanded,
                modifier = Modifier.focusRequester(requesters.getValue(entry.key)),
                onFocused = { focusedKey = entry.key },
                onPreview = { if (entry.key != "search") onPreview(entry.key) },
                onSelect = { onSelect(entry.key) },
                onDirection = { direction ->
                    when {
                        direction == AndroidKeyEvent.KEYCODE_DPAD_UP && index == 0 -> true
                        direction == AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            requesters.getValue(entries[index - 1].key).requestFocus(); true
                        }
                        direction == AndroidKeyEvent.KEYCODE_DPAD_DOWN && index < entries.lastIndex -> {
                            requesters.getValue(entries[index + 1].key).requestFocus(); true
                        }
                        direction == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (live.isNotEmpty()) focusLive(0) else requesters.getValue("settings").requestFocus()
                            true
                        }
                        else -> false
                    }
                }) { ink -> RailIcon(entry.key, ink) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp).height(1.dp).background(TvColors.Border))
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center) {
            if (expanded) Text("В эфире · ${live.size}", color = TvColors.Muted, fontSize = 11.sp,
                modifier = Modifier.padding(start = 10.dp))
            else Box(Modifier.size(5.dp).background(if (live.isEmpty()) TvColors.Muted else TvColors.Live, CircleShape))
        }
        if (live.isNotEmpty()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                itemsIndexed(live, key = { _, channel -> channel.id }) { index, channel ->
                    RailRow(channel.name, selected == "live:${channel.id}", expanded,
                        modifier = Modifier.focusRequester(requesters.getValue("live:${channel.id}")),
                        description = "${channel.name}, в эфире, ${channel.viewers} зрителей",
                        onFocused = { focusedKey = "live:${channel.id}" },
                        onPreview = { onChannelPreview(channel) }, previewDelay = 250,
                        onSelect = { onChannelSelect(channel) }, onLongSelect = { onChannelDetails(channel) },
                        onDirection = { direction ->
                            when {
                                direction == AndroidKeyEvent.KEYCODE_DPAD_UP && index == 0 -> {
                                    requesters.getValue("streams").requestFocus(); true
                                }
                                direction == AndroidKeyEvent.KEYCODE_DPAD_DOWN && index == live.lastIndex -> {
                                    requesters.getValue("settings").requestFocus(); true
                                }
                                else -> false
                            }
                        }) { _ -> RailChannelAvatar(channel) }
                }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
            if (expanded) Text(when {
                !following.signedIn -> "Войдите в Twitch в настройках"
                following.error != null -> "Каналы недоступны. Обновите список."
                following.loading || !following.initialized -> "Загрузка каналов…"
                else -> "Сейчас никто не в эфире"
            }, color = TvColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        RailRow("Настройки", selected == "settings", expanded,
            modifier = Modifier.focusRequester(requesters.getValue("settings")),
            onFocused = { focusedKey = "settings" }, onPreview = {},
            onSelect = { onSelect("settings") },
            onDirection = { direction -> when (direction) {
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    if (live.isNotEmpty()) focusLive(live.lastIndex) else requesters.getValue("streams").requestFocus()
                    true
                }
                else -> false
            } }) { ink -> RailIcon("settings", ink) }
    }
}

@Composable
private fun RailRow(label: String, active: Boolean, expanded: Boolean, modifier: Modifier,
    description: String = label, previewDelay: Long = 120, onFocused: () -> Unit,
    onPreview: () -> Unit, onSelect: () -> Unit, onLongSelect: (() -> Unit)? = null,
    onDirection: (Int) -> Boolean, icon: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longActivated by remember { mutableStateOf(false) }
    val preview by rememberUpdatedState(onPreview)
    LaunchedEffect(focused) { if (focused) { delay(previewDelay); preview() } }
    val ink = when { focused -> TvColors.Background; active -> TvColors.Link; else -> TvColors.Text }
    Row(modifier.fillMaxWidth().height(42.dp)
        .focusProperties { left = FocusRequester.Cancel }
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused() else longActivated = false
        }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            when (key.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (key.action == AndroidKeyEvent.ACTION_DOWN && key.repeatCount == 0) longActivated = false
                    val held = key.isLongPress || key.eventTime - key.downTime >= ViewConfiguration.getLongPressTimeout()
                    // Android's injected/platform long press may flag the repeated DOWN while
                    // the subsequent UP does not retain that flag or the same duration.
                    if (key.action == AndroidKeyEvent.ACTION_DOWN && held && onLongSelect != null) longActivated = true
                    if (key.action == AndroidKeyEvent.ACTION_UP && !key.isCanceled) {
                        if (onLongSelect != null && (longActivated || held)) onLongSelect()
                        else onSelect()
                    }
                    if (key.action == AndroidKeyEvent.ACTION_UP) longActivated = false
                    true
                }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (key.action == AndroidKeyEvent.ACTION_UP && !key.isCanceled) onSelect()
                    true
                }
                else -> key.action == AndroidKeyEvent.ACTION_DOWN && onDirection(key.keyCode)
            }
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button; selected = active; contentDescription = description
            onClick { onSelect(); true }
            if (onLongSelect != null) onLongClick("Открыть канал") { onLongSelect(); true }
        }
        .focusable()
        .background(when { focused -> TvColors.Focus; active -> TvColors.Raised; else -> Color.Transparent }, TvShapes.Control)
        .border(1.dp, if (active && !focused) TvColors.Border else Color.Transparent, TvShapes.Control)
        .padding(horizontal = if (expanded) 10.dp else 0.dp),
        horizontalArrangement = if (expanded) Arrangement.spacedBy(12.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { icon(ink) }
        if (expanded) Text(label, modifier = Modifier.weight(1f), color = ink, fontSize = 14.sp,
            lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (active || focused) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun RailChannelAvatar(channel: Channel) {
    Box(Modifier.size(30.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(TvColors.Raised), contentAlignment = Alignment.Center) {
            Text(channel.name.take(1).uppercase(), color = TvColors.Text, fontSize = 14.sp)
            if (channel.avatarUrl.isNotBlank()) AsyncImage(channel.avatarUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.align(Alignment.BottomEnd).size(7.dp).background(TvColors.Live, CircleShape)
            .border(1.dp, TvColors.Surface, CircleShape))
    }
}

/** Simple, consistent 24-unit outlined symbols; no extra icon font or remote assets. */
@Composable
private fun RailIcon(kind: String, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val unit = size.width / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        val stroke = Stroke(1.8f * unit, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, point(x1, y1), point(x2, y2), strokeWidth = 1.8f * unit, cap = StrokeCap.Round)
        when (kind) {
            "recordings" -> {
                drawRoundRect(color, point(3f, 5f), Size(18 * unit, 14 * unit), CornerRadius(2 * unit), style = stroke)
                drawPath(Path().apply { moveTo(10 * unit, 8 * unit); lineTo(16 * unit, 12 * unit); lineTo(10 * unit, 16 * unit); close() }, color, style = stroke)
            }
            "streams" -> {
                drawCircle(color, 9 * unit, center, style = stroke)
                drawPath(Path().apply {
                    moveTo(16 * unit, 8 * unit); lineTo(13.5f * unit, 13.5f * unit)
                    lineTo(8 * unit, 16 * unit); lineTo(10.5f * unit, 10.5f * unit); close()
                }, color, style = stroke)
            }
            "search" -> {
                drawCircle(color, 7 * unit, point(10f, 10f), style = stroke)
                line(15f, 15f, 21f, 21f)
            }
            "categories" -> {
                listOf(3f to 3f, 14f to 3f, 3f to 14f, 14f to 14f).forEach { (x, y) ->
                    drawRoundRect(color, point(x, y), Size(7 * unit, 7 * unit), CornerRadius(unit), style = stroke)
                }
            }
            "following" -> {
                val heart = Path().apply {
                    moveTo(12 * unit, 21 * unit)
                    cubicTo(9 * unit, 18 * unit, 2 * unit, 13 * unit, 2 * unit, 8 * unit)
                    cubicTo(2 * unit, 2 * unit, 9 * unit, 1 * unit, 12 * unit, 7 * unit)
                    cubicTo(15 * unit, 1 * unit, 22 * unit, 2 * unit, 22 * unit, 8 * unit)
                    cubicTo(22 * unit, 13 * unit, 15 * unit, 18 * unit, 12 * unit, 21 * unit)
                }
                drawPath(heart, color, style = stroke)
            }
            "account" -> {
                drawCircle(color, 4 * unit, point(12f, 7f), style = stroke)
                val shoulders = Path().apply {
                    moveTo(4 * unit, 21 * unit)
                    cubicTo(4 * unit, 11 * unit, 20 * unit, 11 * unit, 20 * unit, 21 * unit)
                }
                drawPath(shoulders, color, style = stroke)
            }
            "settings" -> {
                drawCircle(color, 6.5f * unit, center, style = stroke)
                drawCircle(color, 2.5f * unit, center, style = stroke)
                repeat(8) { index ->
                    val angle = index * Math.PI / 4
                    line(12 + cos(angle).toFloat() * 6.5f, 12 + sin(angle).toFloat() * 6.5f,
                        12 + cos(angle).toFloat() * 9f, 12 + sin(angle).toFloat() * 9f)
                }
            }
            "about" -> {
                drawCircle(color, 9 * unit, center, style = stroke)
                drawCircle(color, unit, point(12f, 7f))
                line(12f, 11f, 12f, 17f)
            }
        }
    }
}
