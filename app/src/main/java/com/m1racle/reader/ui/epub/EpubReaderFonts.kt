package com.m1racle.reader.ui.epub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.m1racle.reader.data.settings.EpubFontFamily
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.ui.components.LocalReaderImportedFont

/** The selected family enters the same TextStyle used for measurement and drawing. */
@Composable
internal fun rememberEpubReaderFont(settings: ReaderSettings): FontFamily? {
    val imported = LocalReaderImportedFont.current
    return remember(imported, settings.epubFontFamily) {
        imported ?: when (settings.epubFontFamily) {
            EpubFontFamily.Default -> null
            EpubFontFamily.Sans -> FontFamily.SansSerif
            // The platform serif family resolves to Noto Serif CJK on Android 12+,
            // which is what the previously bundled 24 MB OTF provided. Shipping
            // that file cost 19.2 MiB of APK for a single family choice.
            EpubFontFamily.Serif -> FontFamily.Serif
        }
    }
}
