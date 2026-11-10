package com.oprawa.twitch20.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oprawa.twitch20.settings.AppSettings
import com.oprawa.twitch20.settings.ClockStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TvClock(settings: AppSettings, modifier: Modifier = Modifier, textOverride: String? = null) {
    if (!settings.showClock) return
    var current by remember { mutableStateOf(formatClock()) }
    LaunchedEffect(textOverride) {
        if (textOverride == null) {
            while (true) {
                current = formatClock()
                delay(60_000L - System.currentTimeMillis().mod(60_000L))
            }
        }
    }
    val text = textOverride ?: current
    val shape = RoundedCornerShape(6.dp)
    when (settings.clockStyle) {
        ClockStyle.CLASSIC -> Text(text,
            modifier = modifier.background(TvColors.Background.copy(alpha = .92f), shape)
                .border(1.dp, TvColors.Accent.copy(alpha = .55f), shape).padding(horizontal = 9.dp, vertical = 4.dp),
            color = TvColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        ClockStyle.COMPACT -> Text(text,
            modifier = modifier.background(TvColors.Background.copy(alpha = .88f), shape)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            color = TvColors.Link, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        ClockStyle.SEGMENTS -> Row(
            modifier.clearAndSetSemantics { contentDescription = text },
            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            text.split(':').forEachIndexed { index, part ->
                if (index > 0) Text(":", color = TvColors.Link, fontSize = 15.sp)
                Text(part, Modifier.background(TvColors.Surface, shape).border(1.dp, TvColors.Accent, shape)
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                    color = TvColors.Text, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
            }
        }
    }
}

private fun formatClock(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
