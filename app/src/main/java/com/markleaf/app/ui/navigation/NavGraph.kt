package com.markleaf.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.markleaf.app.data.repository.DocumentRepository
import com.markleaf.app.data.settings.ReaderSettingsRepository
import com.markleaf.app.ui.home.HomeScreen
import com.markleaf.app.ui.home.HomeViewModel
import com.markleaf.app.ui.viewer.ViewerScreen
import com.markleaf.app.ui.viewer.ViewerViewModel

object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer/{documentId}"

    fun viewer(documentId: String): String = "viewer/$documentId"
}

@Composable
fun MarkLeafNavGraph(
    navController: NavHostController,
    repository: DocumentRepository,
    readerSettingsRepository: ReaderSettingsRepository,
    modifier: Modifier = Modifier,
) {
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
                onOpenDocument = { documentId ->
                    navController.navigate(Routes.viewer(documentId))
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
    }
}
