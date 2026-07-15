package com.markleaf.app.share

import android.content.Intent
import android.net.Uri

/**
 * Extract a single content/file URI from VIEW or SEND intents.
 * [Intent.ACTION_SEND_MULTIPLE] is explicitly rejected by callers (never silently take first).
 */
object ExternalIntentUri {

    fun extract(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND_MULTIPLE || isSendMultiple(intent)) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> extractSend(intent)
            else -> null
        }
    }

    fun isSendMultiple(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) return true
        val clip = intent.clipData
        if (intent.action == Intent.ACTION_SEND && clip != null && clip.itemCount > 1) return true
        return false
    }

    private fun extractSend(intent: Intent): Uri? {
        val clip = intent.clipData
        if (clip != null && clip.itemCount > 1) return null
        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (stream != null) return stream
        if (clip != null && clip.itemCount == 1) {
            return clip.getItemAt(0)?.uri
        }
        return null
    }

    fun isExternalOpenAction(action: String?): Boolean =
        action == Intent.ACTION_VIEW ||
            action == Intent.ACTION_SEND ||
            action == Intent.ACTION_SEND_MULTIPLE
}
