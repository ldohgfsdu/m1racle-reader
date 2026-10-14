package com.m1racle.reader.ui.navigation

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1racle.reader.BuildConfig
import com.m1racle.reader.R
import com.m1racle.reader.ui.home.HomeEvent
import com.m1racle.reader.ui.home.HomeDesignTokens
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ReadingRepository
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.data.settings.ReaderSettingsRepository
import com.m1racle.reader.share.ExternalOpenCoordinator
import com.m1racle.reader.ui.home.HomeScreen
import com.m1racle.reader.ui.home.HomeViewModel
import com.m1racle.reader.ui.hub.BookmarksScreen
import com.m1racle.reader.ui.hub.BookmarksViewModel
import com.m1racle.reader.ui.hub.GlassDock
import com.m1racle.reader.ui.hub.HubDestination
import com.m1racle.reader.ui.hub.HubDockMotionProvider
import com.m1racle.reader.ui.hub.HubTabTransitions
import com.m1racle.reader.ui.hub.LibraryScreen
import com.m1racle.reader.ui.hub.LibraryViewModel
import com.m1racle.reader.ui.hub.HubSettingsScreen
import com.m1racle.reader.ui.pdf.PdfViewerScreen
import com.m1racle.reader.ui.pdf.PdfViewerViewModel
import com.m1racle.reader.ui.search.SearchScreen
import com.m1racle.reader.ui.search.SearchViewModel
import com.m1racle.reader.ui.viewer.ViewerScreen
import com.m1racle.reader.ui.viewer.ViewerViewModel
import java.util.UUID
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer/{documentId}?anchor={anchor}"
    const val PDF_VIEWER = "pdf/{documentId}?anchor={anchor}"
    const val EPUB_VIEWER = "epub/{documentId}"
    const val PDF_EDITOR = "pdf-edit/{documentId}?page={page}"

    // H2.2 Hub 顶层目的地（Dock 四入口；与 HubDestination.route 一一对应）。
    const val LIBRARY = "library"
    const val BOOKMARKS = "bookmarks"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val DOCUMENT_TOOLS = "document-tools"

    fun viewer(documentId: String, anchor: DocumentAnchor? = null): String =
        routeWithAnchor("viewer/$documentId", anchor)

    fun pdfViewer(documentId: String, anchor: DocumentAnchor? = null): String =
        routeWithAnchor("pdf/$documentId", anchor)

    fun epubViewer(documentId: String): String = "epub/$documentId"

    fun bookmark(document: DocumentEntity, target: com.m1racle.reader.data.anchor.BookmarkTarget): String {
        val base = when { document.isPdf -> "pdf"; document.isEpub -> "epub"; else -> "viewer" }
        return "$base/${document.id}?anchor=${URLEncoder.encode(target.encode(), StandardCharsets.UTF_8.name())}"
    }

    fun decodeBookmark(raw: String?): com.m1racle.reader.data.anchor.BookmarkTarget? =
        raw?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?.takeIf { it.startsWith("{") }?.let(com.m1racle.reader.data.anchor.BookmarkTarget::decode)

    fun decodeAnchor(raw: String?): DocumentAnchor? = AnchorCodec.decode(
        raw?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) },
    )

    private fun routeWithAnchor(base: String, anchor: DocumentAnchor?): String =
        anchor?.let {
            "$base?anchor=${URLEncoder.encode(AnchorCodec.encode(it), StandardCharsets.UTF_8.name())}"
        } ?: base
}

/** 按文档类型选择既有阅读路由（Home/Library/书签跳转共用）。 */
private fun viewerRouteFor(document: DocumentEntity, anchor: DocumentAnchor? = null): String = when (document.type) {
    DocumentType.PDF -> Routes.pdfViewer(document.id, anchor)
    DocumentType.EPUB -> Routes.epubViewer(document.id)
    DocumentType.MARKDOWN,
    DocumentType.MARKDOWN_PACKAGE,
    -> Routes.viewer(document.id, anchor)
}

/**
 * H2.2 顶层 Tab 导航：以 HOME 为锚点，saveState/restoreState 保持各页状态，
 * launchSingleTop 防重复栈顶。
 */
private fun NavHostController.navigateToHub(destination: HubDestination) {
    navigate(destination.route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 打开文档：单一活跃阅读器语义（先弹回 Home 锚点再进阅读器）。 */
private fun openDocument(
    navController: NavHostController,
    document: DocumentEntity,
    anchor: DocumentAnchor? = null,
) {
    navController.popBackStack(Routes.HOME, inclusive = false)
    navController.navigate(viewerRouteFor(document, anchor)) { launchSingleTop = true }
}

@Composable
fun M1racleReaderNavGraph(
    navController: NavHostController,
    repository: DocumentRepository,
    readerSettingsRepository: ReaderSettingsRepository,
    modifier: Modifier = Modifier,
    externalOpenCoordinator: ExternalOpenCoordinator? = null,
    readingRepository: ReadingRepository? = null,
    searchRepository: SearchRepository? = null,
) {
    val application = LocalContext.current.applicationContext as Application
    val coordinator = externalOpenCoordinator
        ?: (application as? com.m1racle.reader.M1racleReaderApplication)?.externalOpenCoordinator
    // R23：阅读资产仓库（书签/标注唯一写入通道）。
    val readingRepo = readingRepository
        ?: (application as? com.m1racle.reader.M1racleReaderApplication)?.readingRepository
    val searchRepo = searchRepository
        ?: (application as? com.m1racle.reader.M1racleReaderApplication)?.searchRepository

    // One import owner for the home empty state and the shared trailing action.
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository))
    val importing by homeViewModel.isImporting.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val documentOperations: com.m1racle.reader.ui.home.DocumentOperations = viewModel(
        factory = com.m1racle.reader.ui.home.DocumentOperations.Factory(repository),
    )
    LaunchedEffect(documentOperations) { documentOperations.events.collect { snackbar.showSnackbar(it) } }
    val resources = androidx.compose.ui.platform.LocalResources.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) homeViewModel.onImportDocument(uri)
    }
    val launchImport = {
        importLauncher.launch(arrayOf("text/*", "application/pdf", "application/epub+zip",
            "application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }
    LaunchedEffect(homeViewModel, resources) {
        homeViewModel.events.collect { event ->
            val message = when (event) {
                is HomeEvent.OpenDocument -> {
                    openDocument(navController, event.document)
                    return@collect
                }
                HomeEvent.UnsupportedFormat -> resources.getString(R.string.error_unsupported_format)
                HomeEvent.SourceTooLarge -> resources.getString(R.string.home_import_too_large,
                    com.m1racle.reader.share.ExternalDocumentImporter.MAX_BYTES / (1024L * 1024L))
                HomeEvent.DeleteFailed -> resources.getString(R.string.home_delete_failed)
                HomeEvent.ReadFailed -> resources.getString(R.string.error_read_failed)
            }
            snackbar.showSnackbar(message)
        }
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    val selectedHub = HubDestination.fromRoute(currentEntry?.destination?.route)
        ?: HubDestination.fromRoute(navController.previousBackStackEntry?.destination?.route)
        ?: HubDestination.HOME
    val hubEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition? = {
        HubTabTransitions.enter(initialState.destination.route, targetState.destination.route)
    }
    val hubExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition? = {
        HubTabTransitions.exit(initialState.destination.route, targetState.destination.route)
    }
    // Capture the actual opaque page base, including the empty area below content.
    // Parent window/background pixels are not part of this RenderNode. Without
    // this fill, transparent hub pages become gray after optical processing.
    val hubBackground = MaterialTheme.colorScheme.background
    val drawHubBackdrop: ContentDrawScope.() -> Unit = remember(hubBackground) {
        {
            drawRect(hubBackground)
            drawContent()
        }
    }
    val hubBackdrop = rememberLayerBackdrop(onDraw = drawHubBackdrop)
    val hubMenuExport = rememberLayerBackdrop()
    val hubBackdropHost = remember(hubBackdrop, hubMenuExport) { GlassBackdropHost(hubBackdrop, hubMenuExport) }
    val visibleEntries by navController.visibleEntries.collectAsState()
    // Back-stack selection changes before the outgoing destination is disposed.
    // Never sample that outgoing reader through a newly mounted hub dock.
    val showDock = HubDestination.isTopLevelRoute(currentEntry?.destination?.route) &&
        visibleEntries.all { HubDestination.isTopLevelRoute(it.destination.route) }
    CompositionLocalProvider(com.m1racle.reader.ui.home.LocalDocumentOperations provides documentOperations) {
    HubDockMotionProvider(selected = selectedHub) {
        Box(modifier = modifier.fillMaxSize()) {
            GlassBackdropSource(hubBackdrop, Modifier.fillMaxSize(), enabled = showDock) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    // A reader must not remain visible behind the newly mounted hub dock.
                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None },
                    popEnterTransition = {
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))
                    },
                    popExitTransition = {
                        androidx.compose.animation.scaleOut(targetScale = 0.92f,
                            animationSpec = androidx.compose.animation.core.tween(220)) +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220))
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.HOME, enterTransition = hubEnter, exitTransition = hubExit) {
                        HomeScreen(
                            modifier = Modifier.testTag("hub-page-home"),
                            viewModel = homeViewModel,
                            onImport = launchImport,
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenDocument = { document ->
                                openDocument(navController, document)
                            },
                            onNavigateHub = { destination ->
                                navController.navigateToHub(destination)
                            },
                        )
                    }

                    composable(Routes.LIBRARY, enterTransition = hubEnter, exitTransition = hubExit) {
                        val libraryViewModel: LibraryViewModel = viewModel(
                            factory = LibraryViewModel.Factory(repository),
                        )
                        LibraryScreen(
                            modifier = Modifier.testTag("hub-page-library"),
                            viewModel = libraryViewModel,
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenDocument = { document ->
                                openDocument(navController, document)
                            },
                            onNavigateHub = { destination ->
                                navController.navigateToHub(destination)
                            },
                        )
                    }

                    composable(Routes.SEARCH,
                        enterTransition = {
                            androidx.compose.animation.scaleIn(initialScale = 0.2f,
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.92f, 0.04f),
                                animationSpec = androidx.compose.animation.core.tween(280)) +
                                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180))
                        },
                        popExitTransition = {
                            androidx.compose.animation.scaleOut(targetScale = 0.2f,
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.92f, 0.04f),
                                animationSpec = androidx.compose.animation.core.tween(240)) +
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
                        },
                    ) {
                        val repositoryForSearch = requireNotNull(searchRepo) {
                            "SearchRepository unavailable for SearchScreen"
                        }
                        val searchViewModel: SearchViewModel = viewModel(
                            factory = SearchViewModel.Factory(repositoryForSearch),
                        )
                        val scope = rememberCoroutineScope()
                        SearchScreen(
                            viewModel = searchViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenResult = { result ->
                                scope.launch {
                                    repository.getDocumentById(result.documentId)?.let { document ->
                                        openDocument(navController, document, result.anchor)
                                    }
                                }
                            },
                        )
                    }

                    composable(Routes.BOOKMARKS, enterTransition = hubEnter, exitTransition = hubExit) {
                        val bookmarksViewModel: BookmarksViewModel = viewModel(
                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : androidx.lifecycle.ViewModel> create(
                                    modelClass: Class<T>,
                                ): T {
                                    if (modelClass.isAssignableFrom(BookmarksViewModel::class.java)) {
                                        val repo = readingRepository
                                            ?: (application as? com.m1racle.reader.M1racleReaderApplication)?.readingRepository
                                            ?: error("ReadingRepository unavailable for BookmarksScreen")
                                        return BookmarksViewModel(repo) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel")
                                }
                            },
                        )
                        val scope = rememberCoroutineScope()
                        BookmarksScreen(
                            modifier = Modifier.testTag("hub-page-bookmarks"),
                            viewModel = bookmarksViewModel,
                            onOpenDocument = { bookmark ->
                                scope.launch {
                                    repository.getDocumentById(bookmark.documentId)?.let { document ->
                                        val target = com.m1racle.reader.data.anchor.BookmarkTarget.decode(bookmark.anchor)
                                            ?: com.m1racle.reader.data.anchor.BookmarkTarget(
                                                if (document.isPdf) DocumentAnchor.PdfPage(bookmark.location)
                                                else DocumentAnchor.MarkdownBlock((bookmark.location - 1).coerceAtLeast(0)),
                                                scrollOffset = bookmark.offset?.toInt()?.coerceAtLeast(0) ?: 0,
                                            )
                                        navController.popBackStack(Routes.HOME, inclusive = false)
                                        navController.navigate(Routes.bookmark(document, target)) { launchSingleTop = true }
                                    }
                                }
                            },
                            onNavigateHub = { destination ->
                                navController.navigateToHub(destination)
                            },
                        )
                    }

                    composable(Routes.SETTINGS, enterTransition = hubEnter, exitTransition = hubExit) {
                        HubSettingsScreen(
                            modifier = Modifier.testTag("hub-page-settings"),
                            onOpenDocumentTools = { navController.navigate(Routes.DOCUMENT_TOOLS) },
                            onNavigateHub = { destination ->
                                navController.navigateToHub(destination)
                            },
                        )
                    }

                    composable(Routes.DOCUMENT_TOOLS) {
                        com.m1racle.reader.ui.documents.DocumentToolsScreen(repository,
                            onBack = { navController.popBackStack() },
                            onOpen = { openDocument(navController, it) })
                    }

                    composable(
                        route = Routes.VIEWER,
                        arguments = listOf(
                            navArgument("documentId") {
                                type = NavType.StringType
                            },
                            navArgument("anchor") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val documentId = entry.arguments?.getString("documentId").orEmpty()
                        val initialAnchor = Routes.decodeAnchor(entry.arguments?.getString("anchor"))
                        val sessionId = remember(documentId) { "md-$documentId-${UUID.randomUUID()}" }
                        // R23 书签中介：Markdown 使用 SCROLL_POSITION 坐标系。
                        val bookmarkController = remember(documentId) {
                            readingRepo?.let {
                                com.m1racle.reader.ui.reader.ReaderBookmarkController(
                                    readingRepository = it,
                                    documentId = documentId,
                                    locationKind = com.m1racle.reader.data.local.BookmarkLocationKind.SCROLL_POSITION,
                                )
                            }
                        }
                        // P0-1 标注中介：复用与 PDF 同一 Controller → Repository → Anchor 链。
                        val highlightController = remember(documentId) {
                            readingRepo?.let {
                                com.m1racle.reader.ui.reader.ReaderHighlightController(
                                    readingRepository = it,
                                    documentId = documentId,
                                )
                            }
                        }
                        val noteController = remember(documentId) {
                            readingRepo?.let {
                                com.m1racle.reader.ui.reader.ReaderNoteController(
                                    readingRepository = it,
                                    documentId = documentId,
                                )
                            }
                        }
                        val viewerViewModel: ViewerViewModel = viewModel(
                            factory = ViewerViewModel.Factory(
                                documentId = documentId,
                                repository = repository,
                                readerSettingsRepository = readerSettingsRepository,
                            ),
                        )
                        // Single active session: register open/close for external replace bookkeeping.
                        DisposableEffect(documentId, sessionId, viewerViewModel) {
                            val c = coordinator
                            c?.onViewerOpened(documentId, sessionId)
                            c?.progressFlushHook = { _ ->
                                // Markdown leave paths flush position; hook is an order barrier only.
                            }
                            onDispose {
                                if (c != null && c.activeViewerSessionId.value == sessionId) {
                                    c.progressFlushHook = null
                                }
                                c?.onViewerClosed(documentId, sessionId)
                            }
                        }
                        CompositionLocalProvider(com.m1racle.reader.ui.documents.LocalReaderDocumentId provides documentId) {
                        ViewerScreen(
                            viewModel = viewerViewModel,
                            onBack = {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            },
                            bookmarkController = bookmarkController,
                            highlightController = highlightController,
                            noteController = noteController,
                            initialAnchor = initialAnchor,
                            initialBookmark = Routes.decodeBookmark(entry.arguments?.getString("anchor")),
                        )
                        }
                    }

                    composable(
                        route = Routes.EPUB_VIEWER,
                        arguments = listOf(
                            navArgument("documentId") {
                                type = NavType.StringType
                            },
                        ),
                    ) { entry ->
                        val documentId = entry.arguments?.getString("documentId").orEmpty()
                        val sessionId = remember(documentId) { "epub-$documentId-${UUID.randomUUID()}" }
                        DisposableEffect(documentId, sessionId, coordinator) {
                            val c = coordinator
                            c?.onViewerOpened(documentId, sessionId)
                            c?.progressFlushHook = { _ ->
                                // EPUB leave paths flush position; hook is an order barrier only.
                            }
                            onDispose {
                                if (c != null && c.activeViewerSessionId.value == sessionId) {
                                    c.progressFlushHook = null
                                }
                                c?.onViewerClosed(documentId, sessionId)
                            }
                        }
                        com.m1racle.reader.ui.epub.EpubReaderScreen(
                            documentId = documentId,
                            repository = repository,
                            readerSettingsRepository = readerSettingsRepository,
                            onBack = {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            },
                        )
                    }

                    composable(
                        route = Routes.PDF_EDITOR,
                        arguments = listOf(navArgument("documentId") { type = NavType.StringType }, navArgument("page") { type = NavType.IntType; defaultValue = 0 }),
                    ) { entry ->
                        val editor: com.m1racle.reader.ui.pdf.edit.PdfBodyEditorViewModel = viewModel(
                            factory = com.m1racle.reader.ui.pdf.edit.PdfBodyEditorViewModel.Factory(application, repository,
                                entry.arguments?.getString("documentId").orEmpty(), entry.arguments?.getInt("page") ?: 0),
                        )
                        com.m1racle.reader.ui.pdf.edit.PdfBodyEditorScreen(editor,
                            onBack = {
                                navController.navigate(Routes.pdfViewer(entry.arguments?.getString("documentId").orEmpty(),
                                    DocumentAnchor.PdfPage(editor.state.value.page?.index ?: 0))) {
                                    popUpTo(Routes.PDF_EDITOR) { inclusive=true }
                                }
                            },
                            onSaved = { id ->
                                navController.popBackStack(Routes.HOME, inclusive = false)
                                navController.navigate(Routes.pdfViewer(id, DocumentAnchor.PdfPage(editor.state.value.page?.index ?: 0)))
                            },
                        )
                    }

                    composable(
                        route = Routes.PDF_VIEWER,
                        arguments = listOf(
                            navArgument("documentId") {
                                type = NavType.StringType
                            },
                            navArgument("anchor") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val documentId = entry.arguments?.getString("documentId").orEmpty()
                        val initialAnchor = Routes.decodeAnchor(entry.arguments?.getString("anchor"))
                        val sessionId = remember(documentId) { "pdf-$documentId-${UUID.randomUUID()}" }
                        val m1racleReaderApp = application as? com.m1racle.reader.M1racleReaderApplication
                        // R26.1 高亮控制器（Selection → Flow → Repository → Overlay）。
                        val highlightController = remember(documentId) {
                            readingRepo?.let {
                                com.m1racle.reader.ui.reader.ReaderHighlightController(
                                    readingRepository = it,
                                    documentId = documentId,
                                )
                            }
                        }
                        val noteController = remember(documentId) {
                            readingRepo?.let {
                                com.m1racle.reader.ui.reader.ReaderNoteController(
                                    readingRepository = it,
                                    documentId = documentId,
                                )
                            }
                        }
                        // R23 书签中介：PDF 使用 PAGE_INDEX 坐标系。
                        val bookmarkController = remember(documentId) {
                            readingRepo?.let {
                                com.m1racle.reader.ui.reader.ReaderBookmarkController(
                                    readingRepository = it,
                                    documentId = documentId,
                                    locationKind = com.m1racle.reader.data.local.BookmarkLocationKind.PAGE_INDEX,
                                )
                            }
                        }
                        val pdfViewModel: PdfViewerViewModel = viewModel(
                            factory = PdfViewerViewModel.Factory(
                                application = application,
                                documentId = documentId,
                                repository = repository,
                                applicationScope = m1racleReaderApp?.applicationScope
                                    ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
                                initialBookmark = Routes.decodeBookmark(entry.arguments?.getString("anchor")),
                            ),
                        )
                        // Single active PDF session: open/close + final progress flush before external switch.
                        DisposableEffect(documentId, sessionId, pdfViewModel) {
                            val c = coordinator
                            c?.onViewerOpened(documentId, sessionId)
                            c?.progressFlushHook = { id ->
                                if (id == documentId) {
                                    pdfViewModel.flushProgress()
                                }
                            }
                            onDispose {
                                if (c != null && c.activeViewerSessionId.value == sessionId) {
                                    c.progressFlushHook = null
                                }
                                c?.onViewerClosed(documentId, sessionId)
                            }
                        }
                        val editorLaunchScope = rememberCoroutineScope()
                        var openingEditor by remember { mutableStateOf(false) }
                        PdfViewerScreen(
                            viewModel = pdfViewModel,
                            onEditBody = { page ->
                                if (!openingEditor) {
                                    openingEditor=true
                                    editorLaunchScope.launch {
                                        try {
                                            pdfViewModel.releaseForEditing()
                                            navController.navigate("pdf-edit/$documentId?page=$page") {
                                                popUpTo(Routes.PDF_VIEWER) { inclusive=true }
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                        catch (_: Exception) {
                                            navController.popBackStack(Routes.HOME,inclusive=false)
                                            android.widget.Toast.makeText(application,"暂时无法进入编辑，请重新打开文档",android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            onBack = {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            },
                            bookmarkController = bookmarkController,
                            highlightController = highlightController,
                            noteController = noteController,
                            initialAnchor = initialAnchor,
                        )
                    }
                }
            } // Captured navigation content; dock is a sibling.
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = if (showDock) HomeDesignTokens.DockContentClearance else 8.dp))
            if (showDock) {
                CompositionLocalProvider(LocalGlassBackdropHost provides hubBackdropHost) {
                    GlassDock(
                        onSelect = { destination -> navController.navigateToHub(destination) },
                        onImportDocument = launchImport,
                        importing = importing,
                    )
                }
            }
        }
    }
    }
}
