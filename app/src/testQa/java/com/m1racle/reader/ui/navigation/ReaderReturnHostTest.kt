package com.m1racle.reader.ui.navigation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.m1racle.reader.ui.epub.EpubQaHostActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.M1racleReaderApplication
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.m1racle.reader.ui.material.LocalGlassCapability
import com.m1racle.reader.ui.material.GlassCapability
import android.app.Application
import android.app.AppOpsManager
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import androidx.compose.ui.platform.LocalView
import com.m1racle.reader.data.local.DocumentEntity
import kotlinx.coroutines.runBlocking
import java.io.File
import androidx.room.Room
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.data.settings.ReaderSettingsRepository
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.StorageVolumeBuilder
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ReaderReturnHostTest {
    @get:Rule val rule = createAndroidComposeRule<EpubQaHostActivity>()
    private lateinit var nav: NavHostController
    private lateinit var db: AppDatabase
    private lateinit var view: View
    private val store = ViewModelStore()
    @After fun close() {
        rule.runOnIdle { store.clear() }
        rule.waitForIdle()
        db.close()
    }
    private fun start(withEpub: Boolean = false) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Model a mounted shared volume with all-files access denied, as on a
        // fresh install. Robolectric does not create this framework volume.
        val sharedDirectory = ShadowEnvironment.addExternalDir(File(app.cacheDir, "shared-storage").absolutePath)
        shadowOf(app.getSystemService(StorageManager::class.java)).addStorageVolume(
            StorageVolumeBuilder("primary", sharedDirectory, "Shared storage",
                Process.myUserHandle(), Environment.MEDIA_MOUNTED)
                .setIsPrimary(true).setIsEmulated(true).build(),
        )
        shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(
            "android:manage_external_storage", Process.myUid(), app.packageName,
            AppOpsManager.MODE_IGNORED,
        )
        assertFalse("fresh install must not have all-files access", Environment.isExternalStorageManager())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val search = SearchRepository(db.searchIndexDao())
        val repository = DocumentRepository(app, db, db.documentDao(), db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(), search)
        if (withEpub) {
            val fixture = File("src/test/resources/fixtures/epub/sample-epub2.epub")
                .takeIf { it.isFile } ?: File("app/src/test/resources/fixtures/epub/sample-epub2.epub")
            assertTrue(fixture.isFile)
            runBlocking {
                db.documentDao().insertDocument(DocumentEntity("return-epub", "Return EPUB", fixture.toURI().toString(),
                    "application/epub+zip", fixture.length(), 1, 1, 0, 0, 0f, null, 0, "EPUB"))
            }
        }
        rule.setContent {
            view = LocalView.current
            nav = rememberNavController()
            M1racleReaderTheme {
                // Robolectric's native Skia lacks child-shader eval; device QA covers optics.
                CompositionLocalProvider(LocalGlassCapability provides GlassCapability.BLUR_ONLY,
                    LocalViewModelStoreOwner provides object : ViewModelStoreOwner { override val viewModelStore = store }) {
                M1racleReaderNavGraph(nav, repository, ReaderSettingsRepository(app), searchRepository = search)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun loadedEpubReturnsToAStationaryHomeWhileNativeStatusBarReappears() {
        start(withEpub = true)
        val height = (32 * rule.density.density).toInt()
        fun statusInset(visibleHeight: Int) {
            view.dispatchApplyWindowInsets(WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, visibleHeight, 0, 0))
                .setInsetsIgnoringVisibility(WindowInsets.Type.statusBars(), Insets.of(0, height, 0, 0))
                .setVisible(WindowInsets.Type.statusBars(), visibleHeight > 0).build())
        }
        rule.runOnIdle { statusInset(height); nav.navigate(Routes.epubViewer("return-epub")) }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("epub-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag("epub-reading-area").assertIsDisplayed()
        rule.runOnIdle { statusInset(0) }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { assertTrue(nav.popBackStack()) }
        var homeFrames = 0
        repeat(20) { frame ->
            rule.runOnIdle { statusInset(if (frame < 4) 0 else if (frame < 8) height / 2 else height) }
            rule.mainClock.advanceTimeByFrame()
            val header = rule.onAllNodesWithTag("home-header").fetchSemanticsNodes().singleOrNull()
            if (header != null) {
                homeFrames++
                assertEquals("home jumped during EPUB exit at frame $frame", height.toFloat(), header.boundsInRoot.top, 0f)
            }
            // AnimatedContent may still compose the outgoing destination in its
            // first bookkeeping frame. The dock's visibility is the handoff:
            // it must never coexist with the loaded outgoing reader.
            if (rule.onAllNodesWithTag("dock-import").fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithTag("epub-pager").assertDoesNotExist()
            }
        }
        assertTrue("must observe the actual home return", homeFrames > 0)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag("dock-import").assertIsDisplayed()
    }

    @Test fun closingAnyReaderNeverLeavesOutgoingReaderBehindNewDock() {
        start()
        for (route in listOf(Routes.viewer("absent"), Routes.pdfViewer("absent"), Routes.epubViewer("absent"))) {
            rule.runOnIdle { nav.navigate(route) }
            rule.waitForIdle()
            rule.onNodeWithTag("dock-import").assertDoesNotExist()
            rule.mainClock.autoAdvance = false
            rule.runOnIdle { assertTrue(nav.popBackStack()) }
            repeat(20) {
                rule.mainClock.advanceTimeByFrame()
                if (rule.onAllNodesWithTag("dock-import").fetchSemanticsNodes().isNotEmpty()) rule.runOnIdle {
                    assertTrue("outgoing reader must be disposed when hub dock appears",
                        nav.visibleEntries.value.none { it.destination.route in listOf(Routes.VIEWER, Routes.PDF_VIEWER, Routes.EPUB_VIEWER) })
                }
            }
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            rule.onNodeWithTag("dock-import").assertIsDisplayed()
        }
    }

    @Test fun settingsOpensDocumentToolsAndRejectsCancelledWorkbenchRoute() {
        start()
        rule.runOnIdle { nav.navigate(Routes.SETTINGS) }
        rule.waitForIdle()
        rule.onNodeWithText("液态玻璃工作台").assertDoesNotExist()
        rule.runOnIdle {
            assertThrows(IllegalArgumentException::class.java) { nav.navigate("settings/glass-workbench") }
        }
        rule.onNodeWithText("文件与PDF工具").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(Routes.DOCUMENT_TOOLS, nav.currentDestination?.route)
        rule.onNodeWithTag("dock-import").assertDoesNotExist()
        rule.runOnIdle { nav.popBackStack() }
        rule.waitForIdle()
        assertEquals(Routes.SETTINGS, nav.currentDestination?.route)
        rule.onNodeWithTag("dock-import").assertIsDisplayed()
    }
}
