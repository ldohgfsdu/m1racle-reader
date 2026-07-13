package com.markleaf.app.share

import android.content.Intent
import android.net.Uri

/**
 * Extract a single content/file URI from VIEW or SEND intents.
 * Does not handle [Intent.ACTION_SEND_MULTIPLE].
 */
object ExternalIntentUri {

    fun extract(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> extractSend(intent)
            else -> null
        }
    }

    private fun extractSend(intent: Intent): Uri? {
        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (stream != null) return stream
        val clip = intent.clipData
        if (clip != null && clip.itemCount == 1) {
            return clip.getItemAt(0)?.uri
        }
        return null
    }

    fun isExternalOpenAction(action: String?): Boolean =
        action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND
}
