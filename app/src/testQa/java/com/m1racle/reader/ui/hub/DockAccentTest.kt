package com.m1racle.reader.ui.hub

import com.m1racle.reader.design.RadixColors
import com.m1racle.reader.ui.theme.DockSelectedDark
import com.m1racle.reader.ui.theme.DockSelectedLight
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockAccentTest {
    @Test fun `import fill retains full Radix saturation in both themes`() {
        for (dark in listOf(false, true)) {
            val fill = dockImportFill(enabled = true, coolapk = false, dark = dark)
            assertEquals(RadixColors.Blue9, fill)
            assertEquals(1f, fill.alpha, 0f)
            assertTrue(fill.blue - fill.red > .8f)
            val disabled = dockImportFill(enabled = false, coolapk = false, dark = dark)
            assertEquals(disabled.red, disabled.blue, 0f)
        }
    }

    @Test fun `selected foreground stays readable on each neutral dock surface`() {
        for ((foreground, background) in listOf(
            DockSelectedLight to RadixColors.Gray2,
            DockSelectedDark to RadixColors.GrayDark5,
        )) {
            val contrast = (maxOf(foreground.luminance(), background.luminance()) + .05f) /
                (minOf(foreground.luminance(), background.luminance()) + .05f)
            assertTrue("selected label contrast=$contrast", contrast >= 4.5f)
        }
    }
}
