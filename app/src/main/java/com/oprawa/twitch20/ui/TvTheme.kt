package com.oprawa.twitch20.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

object TvColors {
    val Background = Color(0xFF0C0D12)
    val Surface = Color(0xFF16171F)
    val Raised = Color(0xFF242630)
    val Text = Color(0xFFF7F7F8)
    val Muted = Color(0xFFABB0C0)
    val Accent = Color(0xFF9146FF)
    val Focus = Color(0xFFC7AEFF)
    val Link = Color(0xFFC6A3FF)
    val Border = Color(0xFF363846)
    val Live = Color(0xFFFF646F)
    val Error = Color(0xFFFF8585)
}

object TvShapes {
    val Control = RoundedCornerShape(8.dp)
    val Panel = RoundedCornerShape(12.dp)
}

@Composable
fun TwitchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvColors.Focus,
            onPrimary = TvColors.Background,
            background = TvColors.Background,
            onBackground = TvColors.Text,
            surface = TvColors.Surface,
            onSurface = TvColors.Text,
            error = TvColors.Error,
        ),
        content = content,
    )
}

@Composable
fun TvAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier,
        shape = ButtonDefaults.shape(shape = TvShapes.Control),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = TvColors.Raised,
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Focus,
            focusedContentColor = TvColors.Background,
        )) {
        Text(text, fontSize = 16.sp, fontFamily = FontFamily.SansSerif)
    }
}
