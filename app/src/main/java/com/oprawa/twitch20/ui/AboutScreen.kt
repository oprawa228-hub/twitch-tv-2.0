package com.oprawa.twitch20.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.oprawa.twitch20.BuildConfig
import com.oprawa.twitch20.update.UpdateManager
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LicenseDocument(val title: String, val asset: String) {
    Notices("Сторонние компоненты", "THIRD_PARTY_NOTICES.txt"),
    License("Лицензия приложения", "LICENSE.txt"),
}

@Composable
fun AuthorScreen(onBack: () -> Unit) {
    var document by remember { mutableStateOf<LicenseDocument?>(null) }
    var lastDocument by remember { mutableStateOf(LicenseDocument.Notices) }
    val noticesFocus = remember { FocusRequester() }
    val licenseFocus = remember { FocusRequester() }
    val opened = document
    BackHandler { if (opened != null) document = null else onBack() }
    if (opened != null) {
        val assets = LocalContext.current.assets
        val body by produceState<String?>(null, opened, assets) {
            value = withContext(Dispatchers.IO) {
                try {
                    assets.open(opened.asset).bufferedReader().use { it.readText() }
                } catch (_: IOException) {
                    "Не удалось открыть текст из приложения."
                }
            }
        }
        DocumentReader(opened.title, body ?: "Открываем текст…") { document = null }
    } else {
        LaunchedEffect(Unit) {
            when (lastDocument) {
                LicenseDocument.Notices -> noticesFocus.requestFocus()
                LicenseDocument.License -> licenseFocus.requestFocus()
            }
        }
        SettingsDetailPage("Автор и лицензии") {
            Row(
                Modifier.fillMaxWidth().background(TvColors.Surface, TvShapes.Panel).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(84.dp).background(TvColors.Accent.copy(alpha = .20f), TvShapes.Panel)
                        .border(1.dp, TvColors.Accent.copy(alpha = .55f), TvShapes.Panel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("O", color = TvColors.Focus, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Twitch 2.0", color = TvColors.Link, fontSize = 16.sp)
                    Text("Oprawa", color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text("Независимое приложение для просмотра Twitch на Android TV.",
                        color = TvColors.Muted, fontSize = 18.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvAction(LicenseDocument.Notices.title, {
                    lastDocument = LicenseDocument.Notices
                    document = lastDocument
                }, Modifier.focusRequester(noticesFocus))
                TvAction(LicenseDocument.License.title, {
                    lastDocument = LicenseDocument.License
                    document = lastDocument
                }, Modifier.focusRequester(licenseFocus))
            }
            TvAction("Назад", onBack)
        }
    }
}

@Composable
fun UpdatesScreen(updater: UpdateManager, onBack: () -> Unit) {
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val activity = checkNotNull(LocalActivity.current)
    val prefs = remember(activity) { activity.getSharedPreferences("updates", Context.MODE_PRIVATE) }
    var testChannel by remember { mutableStateOf(prefs.getBoolean("testChannel", true)) }
    var showNotes by remember { mutableStateOf(false) }
    var restoreNotesFocus by remember { mutableStateOf(false) }
    val primaryFocus = remember { FocusRequester() }
    val notesButtonFocus = remember { FocusRequester() }
    val release = state.available
    val markdown = release?.notes ?: state.currentNotes
    val notes = remember(markdown) { markdown?.let(::formatReleaseNotes)?.takeIf { it.isNotBlank() } }
    val busy = state.status == "checking" || state.status == "downloading"

    BackHandler {
        when {
            showNotes -> showNotes = false
            busy -> updater.cancel()
            else -> onBack()
        }
    }
    if (showNotes && notes != null) {
        DocumentReader("Что нового · ${release?.versionName ?: BuildConfig.VERSION_NAME}", notes) { showNotes = false }
    } else {
        LaunchedEffect(Unit) {
            if (restoreNotesFocus && notes != null) notesButtonFocus.requestFocus()
            else primaryFocus.requestFocus()
            restoreNotesFocus = false
        }
        SettingsDetailPage("Обновления") {
            Column(
                Modifier.fillMaxWidth().background(TvColors.Surface, TvShapes.Panel).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Установлена версия ${BuildConfig.VERSION_NAME} · Android TV", color = TvColors.Muted, fontSize = 16.sp)
                Text(state.message, color = if (state.status == "error") TvColors.Error else TvColors.Text,
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                if (state.status == "downloading" && state.progressKnown) {
                    val progress = state.progress.coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(5.dp).progressSemantics(progress)
                        .background(TvColors.Raised, TvShapes.Control)) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight()
                            .background(TvColors.Accent, TvShapes.Control))
                    }
                    Text("${(progress * 100).toInt()}%", color = TvColors.Link, fontSize = 16.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val label = when {
                    busy -> "Отменить"
                    state.status in setOf("ready", "permission", "installing") -> "Установить обновление"
                    release != null -> "Скачать ${release.versionName}"
                    else -> "Проверить обновления"
                }
                TvAction(label, modifier = Modifier.focusRequester(primaryFocus), onClick = {
                    when {
                        busy -> updater.cancel()
                        state.status in setOf("ready", "permission", "installing") -> updater.install(activity)
                        release != null -> scope.launch { updater.download() }
                        else -> scope.launch { updater.check(testChannel) }
                    }
                })
                TvAction(if (testChannel) "Канал: тестовый" else "Канал: обычный", onClick = {
                    testChannel = !testChannel
                    prefs.edit().putBoolean("testChannel", testChannel).apply()
                    scope.launch { updater.check(testChannel) }
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (notes != null) {
                    TvAction("Что нового", {
                        restoreNotesFocus = true
                        showNotes = true
                    }, Modifier.focusRequester(notesButtonFocus))
                }
                TvAction("Назад", onBack)
            }
            if (notes == null && (release != null || state.status == "current")) {
                Text("Описание выпуска недоступно.", color = TvColors.Muted, fontSize = 16.sp)
            } else if (notes != null) {
                Text(notes, color = TvColors.Muted, fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val notesHeading = Regex("""^ {0,3}#{1,6}\s+(.+?)(?:\s+#+)?\s*$""")
private val notesBullet = Regex("""^(\s*)[-+*]\s+""")
private val notesLink = Regex("""!?\[([^\]\r\n]+)\]\((?:[^()\r\n]|\([^()\r\n]*\))*\)""")
private val notesEmphasis = Regex("""(\*\*|__|`)(.+?)\1""")

internal fun formatReleaseNotes(markdown: String): String = markdown.lineSequence().joinToString("\n") { line ->
    val heading = notesHeading.matchEntire(line)?.groupValues?.get(1)
    val text = heading ?: notesBullet.replaceFirst(line, "$1• ")
    notesEmphasis.replace(notesLink.replace(text) { it.groupValues[1] }) { it.groupValues[2] }
}.trim()

@Composable
private fun DocumentReader(title: String, body: String, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val line = with(LocalDensity.current) { 72.dp.roundToPx() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .background(TvColors.Surface, TvShapes.Panel)
                .border(2.dp, if (focused) TvColors.Focus else TvColors.Border, TvShapes.Panel)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta = when {
                        event.key == Key.DirectionUp && scroll.canScrollBackward -> -line
                        event.key == Key.DirectionDown && scroll.canScrollForward -> line
                        else -> return@onPreviewKeyEvent false
                    }
                    scope.launch { scroll.scrollTo(scroll.value + delta) }
                    true
                }
                .focusable()
                .padding(20.dp),
        ) {
            Text(body, Modifier.verticalScroll(scroll), color = TvColors.Text, fontSize = 18.sp, lineHeight = 26.sp)
        }
        TvAction("Назад", onBack)
    }
}
