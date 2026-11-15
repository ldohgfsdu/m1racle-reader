package com.m1racle.reader.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.m1racle.reader.R

/**
 * Destination for documents the shipping build no longer reads.
 *
 * Two callers rely on it:
 * 1. A back stack saved by a build that still had the Markdown or EPUB reader.
 *    Navigation restores a destination by route name, so the route has to stay
 *    registered even though no reader is attached to it. Without this the
 *    restore throws "destination cannot be found" and the app dies on launch.
 * 2. A bookmark written before the scope change. The library row and the
 *    bookmark row are user data and stay; only the reader is gone.
 *
 * Deliberately has no reader state, no session, no repository: it must keep
 * compiling after ui/epub is deleted.
 */
@Composable
internal fun UnsupportedDocumentScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .testTag("unsupported-format-page"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.unsupported_format_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.unsupported_format_body),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .padding(top = 24.dp)
                .testTag("unsupported-format-back"),
        ) {
            Text(stringResource(R.string.unsupported_format_back))
        }
    }
}
