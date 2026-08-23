package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markleaf.app.R
import kotlinx.coroutines.delay

/**
 * In-bar search row: same 56dp height as the reading top bar so the chrome
 * does not jump. Compact filled field instead of a Material TextField form.
 */
@Composable
fun SearchTopBar(
    query: String,
    matchLabel: String,
    hasMatches: Boolean,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(180)
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.search_close),
                tint = scheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                .padding(start = 12.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (query.isNotEmpty()) 40.dp else 0.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        modifier = Modifier.size(18.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = matchLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(min = 36.dp)
                .padding(start = 8.dp),
        )
        IconButton(
            onClick = onPrevious,
            enabled = hasMatches,
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.search_previous),
                tint = if (hasMatches) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            )
        }
        IconButton(
            onClick = onNext,
            enabled = hasMatches,
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.search_next),
                tint = if (hasMatches) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}
