package com.oprawa.twitch20.ui

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text

enum class SearchFilter(val label: String, val emptyMessage: String) {
    All("Все", "По этому запросу ничего не найдено"),
    Channels("Каналы", "Каналы не найдены"),
    Categories("Категории", "Категории не найдены"),
    Recordings("Записи", "Записи не найдены"),
}

@Composable
internal fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onVoice: () -> Unit,
    fieldFocus: FocusRequester,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    onFilterFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filterFocus = remember { SearchFilter.entries.associateWith { FocusRequester() } }
    val selectedFocus = filterFocus.getValue(filter)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Поиск", color = TvColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        SearchInputBar(query, onQueryChange, onSearch, onVoice, fieldFocus, selectedFocus,
            Modifier.widthIn(max = 640.dp).fillMaxWidth())
        Row(Modifier.onFocusChanged { onFilterFocus(it.hasFocus) }
            .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in SearchFilter.entries) {
                val active = filter == type
                Button(onClick = { onFilterChange(type) },
                    modifier = Modifier.focusRequester(filterFocus.getValue(type))
                        .focusProperties { up = fieldFocus }
                        .semantics { selected = active },
                    shape = ButtonDefaults.shape(shape = CircleShape),
                    contentPadding = PaddingValues(horizontal = 17.dp, vertical = 8.dp),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    colors = ButtonDefaults.colors(
                        containerColor = if (active) TvColors.Accent.copy(alpha = .23f) else TvColors.Surface,
                        contentColor = if (active) TvColors.Link else TvColors.Text,
                        focusedContainerColor = TvColors.Focus, focusedContentColor = TvColors.Background)) {
                    Text(type.label, fontSize = 15.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onVoice: () -> Unit,
    fieldFocus: FocusRequester,
    filterFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val microphone = remember { FocusRequester() }
    val magnifier = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val textField = rememberTextFieldState(initialText = query)
    val reportQuery by rememberUpdatedState(onQueryChange)
    LaunchedEffect(textField) { snapshotFlow { textField.text.toString() }.collect { reportQuery(it) } }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var fieldFocused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val hint = "Поиск по Twitch"
    fun submit() { keyboard?.hide(); onSearch(textField.text.toString()) }

    Row(modifier.clip(CircleShape).background(TvColors.Raised).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SearchIconButton("Голосовой поиск", voice = true, modifier = Modifier.focusRequester(microphone)
            .focusProperties { right = fieldFocus; down = filterFocus }) {
            keyboard?.hide()
            onVoice()
        }
        BasicTextField(state = textField, lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation.maxLength(100),
            textStyle = TextStyle(color = TvColors.Text, fontSize = 17.sp),
            cursorBrush = SolidColor(TvColors.Link),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = editing),
            onKeyboardAction = { submit() },
            modifier = Modifier.weight(1f).focusRequester(fieldFocus)
                .onFocusChanged {
                    fieldFocused = it.isFocused
                    if (!it.isFocused) editing = false
                }
                .semantics { contentDescription = hint }
                .onPreviewKeyEvent {
                    val event = it.nativeKeyEvent
                    if (!imeVisible && (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                            if (editing) keyboard?.show() else editing = true
                        }
                        return@onPreviewKeyEvent true
                    }
                    val target = when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> microphone.takeUnless { imeVisible }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> magnifier.takeUnless { imeVisible }
                        KeyEvent.KEYCODE_DPAD_DOWN -> filterFocus.takeUnless { imeVisible }
                        else -> null
                    }
                    if (target != null && event.action == KeyEvent.ACTION_DOWN) target.requestFocus() else false
                },
            decorator = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .then(if (fieldFocused) Modifier.border(2.dp, TvColors.Focus, CircleShape) else Modifier)
                    .padding(horizontal = 17.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart) {
                    if (textField.text.isEmpty()) Text(hint, color = TvColors.Muted, fontSize = 17.sp, maxLines = 1)
                    inner()
                }
            })
        SearchIconButton("Найти", voice = false, modifier = Modifier.focusRequester(magnifier)
            .focusProperties { left = fieldFocus; down = filterFocus }, onClick = ::submit)
    }
}

@Composable
private fun SearchIconButton(label: String, voice: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.size(48.dp).semantics { contentDescription = label },
        shape = ButtonDefaults.shape(shape = CircleShape), contentPadding = PaddingValues(0.dp),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Focus, focusedContentColor = TvColors.Background)) {
        val ink = LocalContentColor.current
        Canvas(Modifier.size(24.dp)) {
            val u = size.width / 24f
            fun point(x: Float, y: Float) = Offset(x * u, y * u)
            val stroke = 1.9f * u
            if (voice) {
                drawRoundRect(ink, point(9f, 2.8f), Size(6f * u, 12f * u),
                    CornerRadius(3f * u), style = Stroke(stroke))
                drawArc(ink, 0f, 180f, false, point(5.5f, 6.5f), Size(13f * u, 12f * u),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(ink, point(12f, 18.5f), point(12f, 22f), stroke, StrokeCap.Round)
                drawLine(ink, point(9f, 22f), point(15f, 22f), stroke, StrokeCap.Round)
            } else {
                drawCircle(ink, 6f * u, point(10.2f, 10.2f), style = Stroke(stroke))
                drawLine(ink, point(14.7f, 14.7f), point(20.5f, 20.5f), stroke, StrokeCap.Round)
            }
        }
    }
}
