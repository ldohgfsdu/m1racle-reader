package com.m1racle.reader.share

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ExternalIntentManifestTest {

    @Test
    fun viewAndSend_acceptGenericAndLegacyPdfMimeTypes() {
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifest)

        assertTrue(hasFilter(document.documentElement, "android.intent.action.VIEW", "application/x-pdf"))
        assertTrue(hasFilter(document.documentElement, "android.intent.action.VIEW", "application/octet-stream"))
        assertTrue(hasFilter(document.documentElement, "android.intent.action.SEND", "application/x-pdf"))
        assertTrue(hasFilter(document.documentElement, "android.intent.action.SEND", "application/octet-stream"))
    }

    private fun hasFilter(root: Element, actionName: String, mimeType: String): Boolean {
        val filters = root.getElementsByTagName("intent-filter")
        return (0 until filters.length).any { index ->
            val filter = filters.item(index) as Element
            val actions = filter.getElementsByTagName("action")
            val data = filter.getElementsByTagName("data")
            val hasAction = (0 until actions.length).any { actionIndex ->
                (actions.item(actionIndex) as Element).getAttributeNS(ANDROID_NS, "name") == actionName
            }
            val hasMime = (0 until data.length).any { dataIndex ->
                (data.item(dataIndex) as Element).getAttributeNS(ANDROID_NS, "mimeType") == mimeType
            }
            hasAction && hasMime
        }
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
