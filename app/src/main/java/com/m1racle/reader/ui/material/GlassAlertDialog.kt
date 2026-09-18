package com.m1racle.reader.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private val GlassDialogShape = RoundedCornerShape(28.dp)

/** Material 3 dialog behavior and layout on top of the unified Dialog recipe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val scheme = MaterialTheme.colorScheme
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .glassMaterial(
                    role = GlassMaterialRole.Dialog,
                    shape = GlassDialogShape,
                    sampleBackdrop = false,
                    interaction = true,
                )
                .padding(24.dp),
        ) {
            if (title != null) {
                CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                }
            }
            if (title != null && text != null) {
                Spacer(Modifier.height(16.dp))
            }
            if (text != null) {
                CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = androidx.compose.ui.Alignment.End),
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
