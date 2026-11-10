package com.oprawa.twitch20.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.oprawa.twitch20.data.VodStoryboard
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun VodStoryboardPreview(storyboard: VodStoryboard, positionMs: Long, durationMs: Long) {
    val tile = storyboard.tileIndex(positionMs)
    val sheet = storyboard.sheetIndex(tile)
    var bitmap by remember(storyboard, sheet) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(storyboard, sheet) {
        delay(120)
        bitmap = storyboard.sheet(sheet)?.asImageBitmap()
    }
    val image = bitmap ?: return
    val cellWidth = image.width / storyboard.columns
    val cellHeight = image.height / storyboard.rows
    if (cellWidth <= 0 || cellHeight <= 0 || image.width % storyboard.columns != 0 ||
        image.height % storyboard.rows != 0) return

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val previewWidth = 148.dp.coerceAtMost(maxWidth)
        val previewHeight = previewWidth * (cellHeight.toFloat() / cellWidth)
        val progress = (positionMs.toDouble() / durationMs.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
        val start = (maxWidth * progress - previewWidth / 2).coerceIn(0.dp, maxWidth - previewWidth)
        Canvas(Modifier.offset(x = start).width(previewWidth).height(previewHeight)
            .clip(RoundedCornerShape(6.dp))) {
            drawImage(image,
                srcOffset = IntOffset(storyboard.tileColumn(tile) * cellWidth, storyboard.tileRow(tile) * cellHeight),
                srcSize = IntSize(cellWidth, cellHeight),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
        }
    }
}
