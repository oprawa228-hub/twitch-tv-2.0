package com.oprawa.twitch20.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Native 16:9 author-art scene. No playback/chat state and no focusable nodes. */
@Composable
internal fun PreviewScene(animationsEnabled: Boolean, modifier: Modifier = Modifier) {
    val environment = rememberMotionEnvironment(animationsEnabled)
    val view = LocalView.current
    var intersectsWindow by remember { mutableStateOf(false) }
    val frame = remember { mutableStateOf(PreviewFrame()) }
    val cameraViewport = remember {
        GenericShape { size, _ ->
            val sx = size.width / 640f
            val sy = size.height / 360f
            fun cameraPoint(x: Float, y: Float) = Offset((x * 1.32f - 8f) * sx, (y * 1.32f - 103f) * sy)
            val first = cameraPoint(18f, 246f)
            moveTo(first.x, first.y)
            for ((x, y) in listOf(30f to 234f, 183f to 234f, 183f to 318f, 171f to 330f, 18f to 330f)) {
                val point = cameraPoint(x, y)
                lineTo(point.x, point.y)
            }
            close()
        }
    }
    val running = environment.enabled && intersectsWindow

    LaunchedEffect(running, environment.scale) {
        frame.value = PreviewFrame()
        if (!running) return@LaunchedEffect
        var previous = withFrameNanos { it }
        var elapsed = 0.0
        while (isActive) {
            val now = withFrameNanos { it }
            // Scale 0 never enters this loop. Clamp stalls: a returning window cannot jump a whole scene.
            elapsed += ((now - previous).coerceIn(0L, 100_000_000L) / 1_000_000.0) / environment.scale
            previous = now
            frame.value = PreviewMotion.frame(elapsed.toLong())
        }
    }

    BoxWithConstraints(
        modifier.clipToBounds().onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            intersectsWindow = coordinates.isAttached && bounds.width > 0f && bounds.height > 0f &&
                bounds.right > 0f && bounds.bottom > 0f &&
                bounds.left < view.width && bounds.top < view.height
        }, contentAlignment = Alignment.Center,
    ) {
        val stageWidth = minOf(maxWidth, maxHeight * (16f / 9f))
        Box(Modifier.size(stageWidth, stageWidth * (9f / 16f)).clipToBounds()) {
            SceneLayer("background")
            SceneLayer("tank", Modifier.graphicsLayer {
                translationX = frame.value.tankX * size.width / 640f
            })
            WheelSpokes(frame)
            SceneLayer("turret", Modifier.graphicsLayer {
                val pose = frame.value
                translationX = (pose.tankX - pose.recoil) * size.width / 640f
                translationY = pose.recoil * .22f * size.height / 360f
            })
            HeartEffects(frame)
            SceneLayer("camera-background")
            Box(Modifier.fillMaxSize().clip(cameraViewport)) {
                SceneLayer("player", Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(125.32f / 640f, 303.56f / 360f)
                    rotationZ = frame.value.playerTilt
                })
                SceneLayer("mug", Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(155.68f / 640f, 293f / 360f)
                    rotationZ = frame.value.mugTilt + frame.value.playerTilt
                })
                SceneLayer("headset", Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(125.32f / 640f, 248.12f / 360f)
                    translationY = -frame.value.headsetLift * size.height / 360f
                    rotationZ = frame.value.playerTilt * .6f
                })
            }
            SceneLayer("camera-frame")
        }
    }
}

@Composable
private fun SceneLayer(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(context, name) {
        ImageRequest.Builder(context).data("file:///android_asset/preview/layers/$name.svg")
            .decoderFactory(SvgDecoder.Factory()).build()
    }
    AsyncImage(request, contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Fit)
}

@Composable
private fun WheelSpokes(frame: State<PreviewFrame>) {
    Canvas(Modifier.fillMaxSize()) {
        val pose = frame.value
        withTransform({ scale(size.width / 640f, size.height / 360f, Offset.Zero) }) {
            val angle = pose.tankX / 10f
            for (x in listOf(278f, 309f, 340f, 371f, 402f, 433f)) {
                val center = Offset(x + pose.tankX, 264f)
                for (spoke in 0..2) {
                    val a = angle + spoke * (2f * PI.toFloat() / 3f)
                    drawLine(Color(0xFF454050), center, center + Offset(cos(a) * 6f, sin(a) * 6f), 1.7f)
                }
                drawCircle(Color(0xFF454050), 2f, center)
            }
        }
    }
}

@Composable
private fun HeartEffects(frame: State<PreviewFrame>) {
    val heart = remember {
        Path().apply {
            moveTo(0f, 12f)
            cubicTo(-4f, 8f, -15f, 1f, -15f, -6f)
            cubicTo(-15f, -16f, -4f, -18f, 0f, -9f)
            cubicTo(4f, -18f, 15f, -16f, 15f, -6f)
            cubicTo(15f, 1f, 4f, 8f, 0f, 12f)
            close()
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val pose = frame.value
        withTransform({ scale(size.width / 640f, size.height / 360f, Offset.Zero) }) {
            if (pose.smokeAlpha > 0f) {
                val origin = Offset(478f + pose.tankX, 189f)
                scale(pose.smokeScale, pivot = origin) {
                    drawCircle(Color(0xFFE9C7FB), 9f, origin, alpha = pose.smokeAlpha)
                    drawCircle(Color(0xFFE9C7FB), 6f, origin + Offset(9f, -7f), alpha = pose.smokeAlpha * .7f)
                }
            }
            pose.hearts.forEach { item ->
                translate(item.x, item.y) {
                    rotate(item.rotation, Offset.Zero) {
                        scale(item.scale, pivot = Offset.Zero) {
                            val color = when (item.variant) { 1 -> Color(0xFFFFC2DE); 2 -> Color(0xFFFF6F9F); else -> Color(0xFFFF83B4) }
                            drawPath(heart, color, alpha = item.alpha)
                        }
                    }
                }
            }
        }
    }
}

private data class MotionEnvironment(val enabled: Boolean, val scale: Float)

/** Shared with the example chat timer so static mode stops every part of the preview. */
@Composable
internal fun rememberPreviewMotionEnabled(animationsEnabled: Boolean): Boolean =
    rememberMotionEnvironment(animationsEnabled).enabled

@Composable
private fun rememberMotionEnvironment(animationsEnabled: Boolean): MotionEnvironment {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var shown by remember(view) { mutableStateOf(view.isAttachedToWindow && view.isShown && view.hasWindowFocus()) }
    fun readScale() = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    var durationScale by remember(context) { mutableStateOf(readScale()) }

    DisposableEffect(owner, view, context) {
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        fun updateShown() { shown = view.isAttachedToWindow && view.isShown && view.hasWindowFocus() }
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateShown() }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { updateShown() }
        val tree = view.viewTreeObserver
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = updateShown()
            override fun onViewDetachedFromWindow(v: View) { shown = false }
        }
        val scaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { durationScale = readScale() }
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        tree.addOnGlobalLayoutListener(layoutListener)
        tree.addOnWindowFocusChangeListener(focusListener)
        view.addOnAttachStateChangeListener(attachListener)
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, scaleObserver,
        )
        updateShown()
        durationScale = readScale()
        onDispose {
            owner.lifecycle.removeObserver(lifecycleObserver)
            if (tree.isAlive) {
                tree.removeOnGlobalLayoutListener(layoutListener)
                tree.removeOnWindowFocusChangeListener(focusListener)
            }
            view.removeOnAttachStateChangeListener(attachListener)
            context.contentResolver.unregisterContentObserver(scaleObserver)
        }
    }
    return MotionEnvironment(PreviewMotion.canRun(animationsEnabled, resumed, shown, durationScale), durationScale)
}
