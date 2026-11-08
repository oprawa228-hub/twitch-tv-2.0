package com.oprawa.twitch20.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlin.math.cos
import kotlin.math.sin

private data class RailEntry(val key: String, val label: String, val accessibility: String = label)

/** Stable 84dp rail; selection never changes simply because the D-pad moves focus. */
@Composable
fun NavigationRail(
    selected: String,
    onSelect: (String) -> Unit,
    accountName: String,
    modifier: Modifier = Modifier,
    onFocusOwnership: (Boolean) -> Unit = {},
) {
    val entries = listOf(
        RailEntry("streams", "Эфиры"),
        RailEntry("search", "Поиск"),
        RailEntry("categories", "Категории"),
        RailEntry("following", "Каналы", "Отслеживаемые"),
        RailEntry("account", if (accountName.isBlank()) "Войти" else "Аккаунт",
            if (accountName.isBlank()) "Войти в Twitch" else "Аккаунт: $accountName"),
        RailEntry("settings", "Настройки"),
        RailEntry("about", "О проекте", "О приложении"),
    )
    val requesters = remember { List(7) { FocusRequester() } }
    Column(modifier.width(84.dp).fillMaxHeight().background(TvColors.Surface)
        .onFocusChanged { onFocusOwnership(it.hasFocus) }
        .onPreviewKeyEvent {
            if (it.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                it.nativeKeyEvent.keyCode in listOf(AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT)) onFocusOwnership(true)
            false
        }
        .focusGroup()
        .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("2.0", color = TvColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 9.dp))
        entries.forEachIndexed { index, entry ->
            if (index == 4) Spacer(Modifier.weight(1f))
            RailItem(entry, entry.key == selected, requesters[index],
                previous = requesters.getOrNull(index - 1) ?: FocusRequester.Cancel,
                next = requesters.getOrNull(index + 1) ?: FocusRequester.Cancel,
                onSelect = { onFocusOwnership(false); onSelect(entry.key) })
        }
    }
}

@Composable
private fun RailItem(entry: RailEntry, active: Boolean, requester: FocusRequester,
    previous: FocusRequester, next: FocusRequester, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val ink = when { focused -> TvColors.Background; active -> TvColors.Link; else -> TvColors.Text }
    Column(Modifier.fillMaxWidth().height(54.dp)
        .focusRequester(requester)
        .focusProperties { up = previous; down = next; left = FocusRequester.Cancel }
        .onFocusChanged { focused = it.isFocused }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode in listOf(AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)) {
                if (key.action == AndroidKeyEvent.ACTION_UP && !key.isCanceled) onSelect()
                true
            } else false
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            selected = active
            contentDescription = entry.accessibility
            onClick { onSelect(); true }
        }
        .focusable()
        .background(when { focused -> TvColors.Focus; active -> TvColors.Raised; else -> Color.Transparent }, TvShapes.Control)
        .border(1.dp, if (active && !focused) TvColors.Border else Color.Transparent, TvShapes.Control)
        .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        RailIcon(entry.key, ink)
        Text(entry.label, color = ink, fontSize = 11.sp, lineHeight = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            "streams" -> {
                drawRoundRect(color, point(3f, 5f), Size(18 * unit, 14 * unit), CornerRadius(2 * unit), style = stroke)
                drawPath(Path().apply { moveTo(10 * unit, 8 * unit); lineTo(16 * unit, 12 * unit); lineTo(10 * unit, 16 * unit); close() }, color, style = stroke)
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
