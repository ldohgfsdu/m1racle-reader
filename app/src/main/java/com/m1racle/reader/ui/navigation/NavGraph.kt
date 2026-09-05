package com.m1racle.reader.ui.navigation

import android.app.Application
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.m1racle.reader.ui.hub.HubDestination
import com.m1racle.reader.ui.hub.HubDockMotionProvider
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

    // H2.2 Hub 顶层目的地（Dock 四入口；与 HubDestination.route 一一对应）。
    const val LIBRARY = "library"
    const val BOOKMARKS = "bookmarks"
    const val SETTINGS = "settings"
    const val SEARCH = "search"

    fun viewer(documentId: String, anchor: DocumentAnchor? = null): String =
        routeWithAnchor("viewer/$documentId", anchor)

    fun pdfViewer(documentId: String, anchor: DocumentAnchor? = null): String =
        routeWithAnchor("pdf/$documentId", anchor)

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

    val currentEntry by navController.currentBackStackEntryAsState()
    val selectedHub = HubDestination.fromRoute(currentEntry?.destination?.route)
        ?: HubDestination.fromRoute(navController.previousBackStackEntry?.destination?.route)
        ?: HubDestination.HOME
    val hubEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        if (HubDestination.isTopLevelRoute(initialState.destination.route)) EnterTransition.None else null
    }
    val hubExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        if (HubDestination.isTopLevelRoute(targetState.destination.route)) ExitTransition.None else null
    }
    HubDockMotionProvider(selected = selectedHub) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME, enterTransition = hubEnter, exitTransition = hubExit) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(repository),
            )
            HomeScreen(
                viewModel = homeViewModel,
                onOpenDocument = { document ->
                    openDocument(navController, document)
                },
                onNavigateHub = { destination ->
                    navController.navigateToHub(destination)
                },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
            )
        }

        composable(Routes.LIBRARY, enterTransition = hubEnter, exitTransition = hubExit) {
            val libraryViewModel: LibraryViewModel = viewModel(
                factory = LibraryViewModel.Factory(repository),
            )
            LibraryScreen(
                viewModel = libraryViewModel,
                onOpenDocument = { document ->
                    openDocument(navController, document)
                },
                onNavigateHub = { destination ->
                    navController.navigateToHub(destination)
                },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
            )
        }

        composable(Routes.SEARCH) {
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
                viewModel = bookmarksViewModel,
                onOpenDocument = { documentId ->
                    scope.launch {
                        repository.getDocumentById(documentId)?.let { document ->
                            openDocument(navController, document)
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
                onNavigateHub = { destination ->
                    navController.navigateToHub(destination)
                },
            )
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
            ViewerScreen(
                viewModel = viewerViewModel,
                onBack = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                bookmarkController = bookmarkController,
                highlightController = highlightController,
                noteController = noteController,
                initialAnchor = initialAnchor,
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
            PdfViewerScreen(
                viewModel = pdfViewModel,
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
    } // HubDockMotionProvider
}
