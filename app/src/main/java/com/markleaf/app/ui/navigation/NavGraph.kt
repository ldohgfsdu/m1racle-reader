package com.markleaf.app.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.markleaf.app.data.local.DocumentType
import com.markleaf.app.data.repository.DocumentRepository
import com.markleaf.app.data.settings.ReaderSettingsRepository
import com.markleaf.app.share.ExternalOpenCoordinator
import com.markleaf.app.ui.home.HomeScreen
import com.markleaf.app.ui.home.HomeViewModel
import com.markleaf.app.ui.pdf.PdfViewerScreen
import com.markleaf.app.ui.pdf.PdfViewerViewModel
import com.markleaf.app.ui.viewer.ViewerScreen
import com.markleaf.app.ui.viewer.ViewerViewModel
import java.util.UUID

object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer/{documentId}"
    const val PDF_VIEWER = "pdf/{documentId}"

    fun viewer(documentId: String): String = "viewer/$documentId"
    fun pdfViewer(documentId: String): String = "pdf/$documentId"
}

@Composable
fun MarkLeafNavGraph(
    navController: NavHostController,
    repository: DocumentRepository,
    readerSettingsRepository: ReaderSettingsRepository,
    modifier: Modifier = Modifier,
    externalOpenCoordinator: ExternalOpenCoordinator? = null,
) {
    val application = LocalContext.current.applicationContext as Application
    val coordinator = externalOpenCoordinator
        ?: (application as? com.markleaf.app.MarkLeafApplication)?.externalOpenCoordinator

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(repository),
            )
            HomeScreen(
                viewModel = homeViewModel,
                onOpenDocument = { document ->
                    val route = when (document.type) {
                        DocumentType.PDF -> Routes.pdfViewer(document.id)
                        DocumentType.MARKDOWN,
                        DocumentType.MARKDOWN_PACKAGE,
                        -> Routes.viewer(document.id)
                    }
                    // Single active viewer: clear any prior viewer route first.
                    navController.popBackStack(Routes.HOME, inclusive = false)
                    navController.navigate(route) { launchSingleTop = true }
                },
            )
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                },
            ),
        ) { entry ->
            val documentId = entry.arguments?.getString("documentId").orEmpty()
            val sessionId = remember(documentId) { "md-$documentId-${UUID.randomUUID()}" }
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
            )
        }

        composable(
            route = Routes.PDF_VIEWER,
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                },
            ),
        ) { entry ->
            val documentId = entry.arguments?.getString("documentId").orEmpty()
            val sessionId = remember(documentId) { "pdf-$documentId-${UUID.randomUUID()}" }
            val markLeafApp = application as? com.markleaf.app.MarkLeafApplication
            val pdfViewModel: PdfViewerViewModel = viewModel(
                factory = PdfViewerViewModel.Factory(
                    application = application,
                    documentId = documentId,
                    repository = repository,
                    applicationScope = markLeafApp?.applicationScope
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
            )
        }
    }
}
