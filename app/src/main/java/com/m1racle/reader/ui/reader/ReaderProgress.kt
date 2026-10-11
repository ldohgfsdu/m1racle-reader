package com.m1racle.reader.ui.reader

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxWidth

class ReaderProgress(val position: () -> Float, val seek: (Float) -> Unit)
val LocalReaderProgress = staticCompositionLocalOf<ReaderProgress?> { null }

@Composable
fun ReaderProgressDialog(progress: ReaderProgress, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(progress.position().coerceIn(0f, 1f)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("阅读进度 · ${(value * 100).toInt()}%") }, text = {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth(),
            contentAlignment = androidx.compose.ui.Alignment.Center) {
            ReaderProgressWheel(value, "旋转跳转", { value = it }, { progress.seek(value) })
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}
