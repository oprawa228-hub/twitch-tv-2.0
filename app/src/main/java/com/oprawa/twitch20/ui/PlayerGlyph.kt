package com.oprawa.twitch20.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

internal enum class PlayerGlyph { PLAY, PAUSE, BACK, FORWARD, QUALITY, CHAT, THEATER, SETTINGS }

/** Offline optical 24-unit family. Labels belong to the surrounding focusable button. */
@Composable
internal fun PlayerGlyph(kind: PlayerGlyph, color: Color, large: Boolean = false) {
    Canvas(Modifier.size(if (large) 30.dp else 24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x: Float, y: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x,y), Offset(x2,y2), 1.8f, StrokeCap.Round)
            fun triangle(x: Float, reverse: Boolean = false) {
                val tip = if (reverse) x - 7 else x + 7
                drawPath(Path().apply { moveTo(x,5f); lineTo(tip,12f); lineTo(x,19f); close() }, color)
            }
            when (kind) {
                PlayerGlyph.PLAY -> triangle(9f)
                PlayerGlyph.PAUSE -> {
                    drawRect(color, Offset(6f,5f), Size(4f,14f))
                    drawRect(color, Offset(14f,5f), Size(4f,14f))
                }
                PlayerGlyph.BACK -> { triangle(11f,true); triangle(20f,true) }
                PlayerGlyph.FORWARD -> { triangle(4f); triangle(13f) }
                PlayerGlyph.QUALITY -> {
                    drawRect(color, Offset(3f,4f), Size(18f,13f), style=Stroke(1.8f))
                    line(8f,21f,16f,21f); line(12f,17f,12f,21f)
                    line(7f,8f,17f,8f); line(7f,12f,13f,12f)
                }
                PlayerGlyph.CHAT -> {
                    drawPath(Path().apply { moveTo(3f,4f); lineTo(21f,4f); lineTo(21f,16f)
                        lineTo(10f,16f); lineTo(5f,21f); lineTo(5f,16f); lineTo(3f,16f); close() }, color, style=Stroke(1.8f))
                    line(7f,8f,17f,8f); line(7f,12f,13f,12f)
                }
                PlayerGlyph.THEATER -> {
                    drawRect(color, Offset(2f,5f), Size(20f,14f), style=Stroke(1.8f))
                    line(15f,5f,15f,19f); line(18f,9f,19f,9f); line(18f,12f,19f,12f)
                }
                PlayerGlyph.SETTINGS -> {
                    drawCircle(color,6f,Offset(12f,12f),style=Stroke(1.8f))
                    drawCircle(color,2f,Offset(12f,12f),style=Stroke(1.8f))
                    repeat(8) { val a=it*Math.PI/4; line(12f+6*cos(a).toFloat(),12f+6*sin(a).toFloat(),
                        12f+9*cos(a).toFloat(),12f+9*sin(a).toFloat()) }
                }
            }
        }
    }
}
