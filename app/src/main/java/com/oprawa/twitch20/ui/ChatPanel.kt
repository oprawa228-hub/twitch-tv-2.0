package com.oprawa.twitch20.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oprawa.twitch20.chat.ChatMessage
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatPosition

/** Shared normalized geometry for the actual player and the 960 × 540 settings stage. */
object ChatLayout {
    const val overlayWidthFraction = .24f
    const val overlayHeightFraction = .52f
    const val theaterWidthFraction = .24f
    val inset = 20.dp

    fun alignment(position: ChatPosition): Alignment = when (position) {
        ChatPosition.TOP_LEFT -> Alignment.TopStart
        ChatPosition.TOP_RIGHT -> Alignment.TopEnd
        ChatPosition.BOTTOM_LEFT -> Alignment.BottomStart
        ChatPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
}

@Composable
fun ChatPanel(messages: List<ChatMessage>, settings: AppSettings, modifier: Modifier = Modifier) {
    val shown = remember(messages) { messages.takeLast(100) }
    val state = rememberLazyListState()
    LaunchedEffect(shown.lastOrNull()?.id) {
        if (shown.isNotEmpty()) state.scrollToItem(shown.lastIndex)
    }
    Box(modifier.clip(RoundedCornerShape(5.dp))
        .background(TvColors.Background.copy(alpha = settings.chatOpacity.coerceIn(0f, 1f)))) {
        if (shown.isEmpty()) Text("Чат подключается…", color = TvColors.Muted,
            fontSize = settings.chatTextSizeSp.sp, modifier = Modifier.padding(10.dp))
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom)) {
            items(shown, key = { it.id }) { message ->
                val nickColor = remember(message.color) {
                    runCatching { Color(AndroidColor.parseColor(message.color)) }
                        .getOrDefault(TvColors.Link)
                }
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = nickColor, fontWeight = FontWeight.Bold)) {
                        append(message.nickname); append(": ")
                    }
                    append(message.text)
                }, modifier = if (settings.animationsEnabled) Modifier.animateItem() else Modifier,
                    color = TvColors.Text, fontSize = settings.chatTextSizeSp.sp,
                    lineHeight = (settings.chatTextSizeSp * 1.35f).sp)
            }
        }
    }
}
