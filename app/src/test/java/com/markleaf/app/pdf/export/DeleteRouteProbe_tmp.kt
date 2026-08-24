package com.markleaf.app.pdf.export

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DeleteRouteProbe {
    @Test
    fun probe() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        Robolectric.setupContentProvider(RouteProbeProvider::class.java, "probe.auth")
        val uri = Uri.parse("content://probe.auth/x/y")
        val viaResolver = try { app.contentResolver.delete(uri, null, null) } catch (t: Throwable) { "ERR:${t.javaClass.simpleName}" }
        val viaContract = try { android.provider.DocumentsContract.deleteDocument(app.contentResolver, uri) } catch (t: Throwable) { "ERR:${t.javaClass.simpleName}" }
        println("PROBE resolver.delete=$viaResolver documentsContract.delete=$viaContract calls=${RouteProbeProvider.calls}")
    }
    class RouteProbeProvider : ContentProvider() {
        companion object { var calls = 0 }
        override fun onCreate() = true
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, v: ContentValues?): Uri? = null
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int { calls++; return 1 }
    }
}
