package com.oprawa.twitch20.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Twitch's own global emote; decoration has no remote-control focus or spoken label. */
@Composable
internal fun DinoDecoration(animationsEnabled: Boolean) {
    val moving = rememberPreviewMotionEnabled(animationsEnabled)
    val format = if (moving) "animated" else "static"
    val image = "https://static-cdn.jtvnw.net/emoticons/v2/emotesv2_dcd06b30a5c24f6eb871e8f5edbd44f7/$format/dark/2.0"
    Box(Modifier.fillMaxWidth().height(68.dp).clearAndSetSemantics { }) {
        if (moving) {
            val transition = rememberInfiniteTransition(label = "dino hearts")
            val phase by transition.animateFloat(0f, 1f,
                infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "rise")
            Canvas(Modifier.fillMaxSize()) {
                repeat(10) { i ->
                    val t = (phase + i * .17f) % 1f
                    val x = size.width * (((i % 5) + .5f) / 5f) + (t - .5f) * (if (i % 2 == 0) 18.dp else -18.dp).toPx()
                    val y = size.height - 24.dp.toPx() - t * 38.dp.toPx()
                    val r = (3.5f + (i % 3)).dp.toPx()
                    val heart = Path().apply {
                        moveTo(x, y + r)
                        cubicTo(x - 2 * r, y - .2f * r, x - r, y - 1.9f * r, x, y - .7f * r)
                        cubicTo(x + r, y - 1.9f * r, x + 2 * r, y - .2f * r, x, y + r)
                        close()
                    }
                    drawPath(heart, TvColors.Focus.copy(alpha = (1f - t) * .82f))
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround) {
            repeat(5) { AsyncImage(image, null, Modifier.size(36.dp)) }
        }
    }
}
