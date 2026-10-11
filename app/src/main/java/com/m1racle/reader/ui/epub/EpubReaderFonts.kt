package com.m1racle.reader.ui.epub

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.m1racle.reader.data.settings.EpubFontFamily
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.ui.components.LocalReaderImportedFont

/** The selected family enters the same TextStyle used for measurement and drawing. */
@Composable
internal fun rememberEpubReaderFont(settings: ReaderSettings): FontFamily? {
    val imported = LocalReaderImportedFont.current
    val assets = LocalContext.current.assets
    return remember(imported, settings.epubFontFamily, assets) {
        imported ?: when (settings.epubFontFamily) {
            EpubFontFamily.Default -> null
            EpubFontFamily.Sans -> FontFamily.SansSerif
            EpubFontFamily.Serif -> FontFamily(Typeface.createFromAsset(assets, "fonts/NotoSerifCJKsc-Regular.otf"))
        }
    }
}
