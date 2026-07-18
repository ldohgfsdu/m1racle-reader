package com.markleaf.app.ui.viewer

import android.content.Intent
import android.util.Log
import com.markleaf.app.BuildConfig

/**
 * QA-only runtime switches for isolating first-entry Markdown scroll work.
 *
 * Production / debug builds always keep every flag false regardless of Intent extras.
 * Launch examples (QA package only):
 * ```
 * adb shell am start -n com.markleaf.app.qa/.MainActivity \
 *   --ez markleaf.qa.disable_top_bar true
 * adb shell am start -n com.markleaf.app.qa/.MainActivity \
 *   --ez markleaf.qa.disable_prefetch true
 * adb shell am start -n com.markleaf.app.qa/.MainActivity \
 *   --ez markleaf.qa.clear_block_semantics true
 * ```
 */
internal object ReaderPerformanceExperiments {
    const val EXTRA_DISABLE_TOP_BAR = "markleaf.qa.disable_top_bar"
    const val EXTRA_DISABLE_PREFETCH = "markleaf.qa.disable_prefetch"
    const val EXTRA_CLEAR_BLOCK_SEMANTICS = "markleaf.qa.clear_block_semantics"

    var disableTopBar: Boolean = false
        private set
    var disablePrefetch: Boolean = false
        private set
    var clearBlockSemantics: Boolean = false
        private set

    fun configure(intent: Intent?) {
        val enabled = BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED
        disableTopBar = enabled && intent?.getBooleanExtra(EXTRA_DISABLE_TOP_BAR, false) == true
        disablePrefetch = enabled && intent?.getBooleanExtra(EXTRA_DISABLE_PREFETCH, false) == true
        clearBlockSemantics =
            enabled && intent?.getBooleanExtra(EXTRA_CLEAR_BLOCK_SEMANTICS, false) == true
        if (enabled) {
            Log.i(
                "ReaderPerfExperiment",
                "topBar=$disableTopBar prefetch=$disablePrefetch semantics=$clearBlockSemantics",
            )
        }
    }
}
