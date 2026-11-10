package com.oprawa.twitch20.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.oprawa.twitch20.auth.AuthException
import com.oprawa.twitch20.auth.DeviceAuthorization
import com.oprawa.twitch20.auth.TwitchAuth
import com.oprawa.twitch20.data.Channel
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(
    auth: TwitchAuth,
    animations: Boolean,
    onBack: () -> Unit,
    profile: Channel? = null,
) {
    val state by auth.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val primaryFocus = remember { FocusRequester() }
    var device by remember { mutableStateOf<DeviceAuthorization?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var greeting by remember { mutableStateOf<String?>(null) }

    fun cancelLogin() {
        job?.cancel()
        job = null
        busy = false
        device = null
        cancelled = true
    }

    LaunchedEffect(Unit) { primaryFocus.requestFocus() }
    LaunchedEffect(greeting) {
        if (greeting != null) {
            delay(1800)
            greeting = null
        }
    }
    BackHandler { if (busy && !state.loggedIn) cancelLogin() else onBack() }

    SettingsDetailPage("Аккаунт Twitch") {
        if (state.loggedIn) {
            val name = state.displayName
            Row(
                Modifier.fillMaxWidth().background(TvColors.Surface, TvShapes.Panel).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(TvColors.Raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name.take(1).uppercase(), color = TvColors.Focus, fontSize = 30.sp)
                    profile?.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Аккаунт подключён", color = TvColors.Link, fontSize = 16.sp)
                    Text(name, color = TvColors.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (animations) {
                AnimatedVisibility(greeting == name, enter = fadeIn(), exit = fadeOut()) {
                    Text("Привет, $name!", color = TvColors.Link, fontSize = 20.sp)
                }
            } else if (greeting == name) {
                Text("Привет, $name!", color = TvColors.Link, fontSize = 20.sp)
            }
        } else {
            val pending = device
            if (pending != null) {
                ActivationCard(pending)
            } else {
                val message = error ?: state.error
                Column(
                    Modifier.fillMaxWidth().background(TvColors.Surface, TvShapes.Panel).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        when {
                            busy -> "Получаем код Twitch…"
                            message?.startsWith("Код входа истёк") == true -> "Код истёк"
                            message != null -> "Вход не завершён"
                            cancelled -> "Вход отменён"
                            else -> "Войдите в свой аккаунт"
                        },
                        color = TvColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        message ?: "Подтвердите вход на телефоне или компьютере.",
                        color = if (message == null) TvColors.Muted else TvColors.Error,
                        fontSize = 17.sp,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val action = when {
                state.loggedIn -> "Выйти"
                busy -> "Отменить"
                else -> "Получить код"
            }
            TvAction(action, modifier = Modifier.focusRequester(primaryFocus), onClick = {
                when {
                    state.loggedIn -> {
                        greeting = null
                        auth.logout()
                    }
                    busy -> cancelLogin()
                    else -> {
                        busy = true
                        cancelled = false
                        error = null
                        job = scope.launch {
                            try {
                                val authorization = auth.beginLogin()
                                if (!isTwitchActivationUri(authorization.verificationUri)) {
                                    throw AuthException("Twitch вернул неподходящую страницу входа.")
                                }
                                device = authorization
                                auth.awaitLogin(authorization)
                                val account = auth.state.value
                                if (account.loggedIn) greeting = account.displayName
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: AuthException) {
                                error = exception.message
                            } catch (_: Exception) {
                                error = "Вход не завершён. Получите новый код и повторите."
                            } finally {
                                if (job === coroutineContext[Job]) {
                                    device = null
                                    busy = false
                                    job = null
                                }
                            }
                        }
                    }
                }
            })
            TvAction("Назад", onBack)
        }
    }
}

@Composable
private fun ActivationCard(device: DeviceAuthorization) {
    val qr by produceState<Bitmap?>(null, device) {
        value = null
        value = withContext(Dispatchers.Default) { createActivationQr(device.verificationUri) }
    }
    Row(
        Modifier.fillMaxWidth().background(TvColors.Surface, TvShapes.Panel).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(144.dp), contentAlignment = Alignment.Center) {
            qr?.let {
                Image(it.asImageBitmap(), "QR-код страницы активации Twitch", Modifier.fillMaxSize())
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ожидаем подтверждения", color = TvColors.Link, fontSize = 16.sp)
            Text("Откройте twitch.tv/activate и введите код", color = TvColors.Text, fontSize = 18.sp)
            Text(device.userCode, color = TvColors.Text, fontSize = 32.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

internal fun isTwitchActivationUri(value: String): Boolean {
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    return uri.scheme == "https" && uri.host in setOf("www.twitch.tv", "twitch.tv") &&
        uri.port in setOf(-1, 443) && uri.rawUserInfo == null && uri.rawFragment == null &&
        uri.rawPath in setOf("/activate", "/activate/")
}

private fun createActivationQr(uri: String): Bitmap? {
    val size = 360
    val matrix = try {
        QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, size, size)
    } catch (_: IllegalArgumentException) {
        return null
    } catch (_: com.google.zxing.WriterException) {
        return null
    }
    val pixels = IntArray(size * size) { index ->
        if (matrix[index % size, index / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
internal fun SettingsDetailPage(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(TvColors.Background).verticalScroll(rememberScrollState())
            .padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("НАСТРОЙКИ", color = TvColors.Muted, fontSize = 13.sp, letterSpacing = 1.5.sp)
        Text(title, color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}
