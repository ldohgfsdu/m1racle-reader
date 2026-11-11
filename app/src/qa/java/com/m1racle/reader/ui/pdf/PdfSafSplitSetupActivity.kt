package com.m1racle.reader.ui.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.m1racle.reader.BuildConfig
import java.io.File

/**
 * QA-only entry point that obtains a persisted read/write SAF tree grant.
 *
 * The resulting URI is recorded under the QA package's private files directory
 * so instrumentation can receive it explicitly as readerSafTreeUri.
 */
class PdfSafSplitSetupActivity : ComponentActivity() {
    private val openDocumentTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }

        val permissionResult = runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        recordSelection(uri, permissionResult.exceptionOrNull())
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.APPLICATION_ID == "com.m1racle.reader.qa") {
            "PDF SAF split setup must never run in the production package"
        }
        if (savedInstanceState == null) {
            openDocumentTree.launch(null)
        }
    }

    private fun recordSelection(uri: Uri, error: Throwable?) {
        val evidence = if (error == null) {
            uri.toString()
        } else {
            "ERROR: ${error.message}\n$uri"
        }
        File(filesDir, EVIDENCE_FILE_NAME).writeText(evidence, Charsets.UTF_8)
    }

    companion object {
        const val EVIDENCE_FILE_NAME = "readerSafTreeUri.txt"
    }
}
