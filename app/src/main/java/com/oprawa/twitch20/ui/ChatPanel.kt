package com.oprawa.twitch20.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oprawa.twitch20.chat.ChatMessage
import com.oprawa.twitch20.chat.ChatPresentation
import com.oprawa.twitch20.chat.ChatColor
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ChatPosition
import com.oprawa.twitch20.settings.ClockPlacement

/** Shared normalized geometry for the actual player and the 960 × 540 settings stage. */
object ChatLayout {
    const val theaterWidthFraction = .20f
    fun overlayWidth(settings: AppSettings): Float = settings.overlaySize.widthFraction
    fun overlayHeight(settings: AppSettings): Float = settings.overlaySize.heightFraction
    val inset = 20.dp

    fun alignment(position: ChatPosition): Alignment = when (position) {
        ChatPosition.TOP_LEFT -> Alignment.TopStart
        ChatPosition.TOP_RIGHT -> Alignment.TopEnd
        ChatPosition.BOTTOM_LEFT -> Alignment.BottomStart
        ChatPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
}

@Composable
fun ChatPanel(messages: List<ChatMessage>, settings: AppSettings, modifier: Modifier = Modifier, status: String = "") {
    val shown = remember(messages) { messages.takeLast(100) }
    val state = rememberLazyListState()
    LaunchedEffect(shown.lastOrNull()?.id) {
        if (shown.isNotEmpty()) state.scrollToItem(shown.lastIndex)
    }
    Box(modifier.clip(RoundedCornerShape(5.dp))
        .background(TvColors.Background.copy(alpha = settings.chatOpacity.coerceIn(0f, 1f)))) {
        Column(Modifier.fillMaxSize()) {
            if (settings.showClock && settings.clockPlacement == ClockPlacement.CHAT)
                TvClock(settings, Modifier.align(Alignment.End).padding(horizontal = 10.dp, vertical = 6.dp))
            ChatPresentation.statusLabel(status, shown.isNotEmpty())?.let { label ->
                Text(label, color = TvColors.Muted,
                    fontSize = settings.chatTextSizeSp.sp, modifier = Modifier.padding(10.dp))
            }
            LazyColumn(state = state, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom)) {
                items(shown, key = { it.id }) { message ->
                    val nickColor = remember(message.color) {
                        Color(ChatColor.readable(message.color))
                    }
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = nickColor, fontWeight = FontWeight.Bold)) {
                            append(message.nickname); append(": ")
                        }
                        append(message.text)
                    }, modifier = if (settings.animationsEnabled) Modifier.animateItem() else Modifier,
                        style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1f), 3f)),
                        color = TvColors.Text, fontSize = settings.chatTextSizeSp.sp,
                        lineHeight = (settings.chatTextSizeSp * 1.35f).sp)
                }
            }
        }
    }
}
