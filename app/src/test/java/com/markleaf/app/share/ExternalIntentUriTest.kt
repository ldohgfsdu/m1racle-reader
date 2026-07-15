package com.markleaf.app.share

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM stubs do not fully implement Intent/Uri plumbing; keep this suite to
 * constant-level action classification that does not touch framework instance state.
 */
class ExternalIntentUriTest {

    @Test
    fun externalOpenActionsRecognized() {
        assertTrue(ExternalIntentUri.isExternalOpenAction(Intent.ACTION_VIEW))
        assertTrue(ExternalIntentUri.isExternalOpenAction(Intent.ACTION_SEND))
        assertTrue(ExternalIntentUri.isExternalOpenAction(Intent.ACTION_SEND_MULTIPLE))
        assertFalse(ExternalIntentUri.isExternalOpenAction(Intent.ACTION_MAIN))
        assertFalse(ExternalIntentUri.isExternalOpenAction(null))
    }

    @Test
    fun sendMultipleActionConstantMatchesPolicy() {
        assertTrue(Intent.ACTION_SEND_MULTIPLE == "android.intent.action.SEND_MULTIPLE")
    }
}
