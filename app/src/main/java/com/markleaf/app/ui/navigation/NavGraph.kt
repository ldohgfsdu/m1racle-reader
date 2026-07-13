package com.markleaf.app.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
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
import com.markleaf.app.ui.home.HomeScreen
import com.markleaf.app.ui.home.HomeViewModel
import com.markleaf.app.ui.pdf.PdfViewerScreen
import com.markleaf.app.ui.pdf.PdfViewerViewModel
import com.markleaf.app.ui.viewer.ViewerScreen
import com.markleaf.app.ui.viewer.ViewerViewModel

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
) {
    val application = LocalContext.current.applicationContext as Application

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
                    navController.navigate(route)
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
            val viewerViewModel: ViewerViewModel = viewModel(
                factory = ViewerViewModel.Factory(
                    documentId = documentId,
                    repository = repository,
                    readerSettingsRepository = readerSettingsRepository,
                ),
            )
            ViewerScreen(
                viewModel = viewerViewModel,
                onBack = { navController.popBackStack() },
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
            PdfViewerScreen(
                viewModel = pdfViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
