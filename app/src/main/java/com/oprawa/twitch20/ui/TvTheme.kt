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
    val Background = Color(0xFF0E0E10)
    val Surface = Color(0xFF18181B)
    val Raised = Color(0xFF26262C)
    val Text = Color(0xFFF7F7F8)
    val Muted = Color(0xFFADADB8)
    val Accent = Color(0xFF9146FF)
    val Focus = Color(0xFFA970FF)
    val Link = Color(0xFFBF94FF)
    val Error = Color(0xFFFF8585)
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
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
        colors = ButtonDefaults.colors(
            containerColor = TvColors.Raised,
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Focus,
            focusedContentColor = TvColors.Background,
        )) {
        Text(text, fontSize = 16.sp, fontFamily = FontFamily.SansSerif)
    }
}
