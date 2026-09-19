package com.fmhub24.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fmhub24.app.ui.screens.details.DetailsScreen
import com.fmhub24.app.ui.screens.favorites.FavoritesScreen
import com.fmhub24.app.ui.screens.category.CategoryScreen
import com.fmhub24.app.ui.screens.downloads.DownloadsScreen
import com.fmhub24.app.ui.screens.downloads.OfflinePlayerScreen
import com.fmhub24.app.ui.screens.home.HomeScreen
import com.fmhub24.app.ui.screens.player.PlayerScreen
import com.fmhub24.app.ui.screens.search.SearchScreen
import com.fmhub24.app.ui.screens.settings.SettingsScreen
import com.fmhub24.app.ui.screens.splash.SplashScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetails = { url, apiName ->
                    navController.navigate(Screen.Details.createRoute(url, apiName))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToFavorites = {
                    navController.navigate(Screen.Favorites.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToCategory = { providerName, categoryName ->
                    navController.navigate(Screen.Category.createRoute(providerName, categoryName))
                },
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads.route)
                }
            )
        }

        composable(
            route = Screen.Category.route,
            arguments = listOf(
                navArgument("providerName") { type = NavType.StringType },
                navArgument("categoryName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            CategoryScreen(
                providerName = backStackEntry.arguments?.getString("providerName") ?: "",
                categoryName = backStackEntry.arguments?.getString("categoryName") ?: "",
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { detailUrl, detailApiName ->
                    navController.navigate(Screen.Details.createRoute(detailUrl, detailApiName))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { url, apiName ->
                    navController.navigate(Screen.Details.createRoute(url, apiName))
                }
            )
        }

        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlayOffline = { path, title ->
                    navController.navigate(Screen.OfflinePlayer.createRoute(path, title))
                }
            )
        }

        composable(
            route = Screen.OfflinePlayer.route,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            OfflinePlayerScreen(
                path = backStackEntry.arguments?.getString("path") ?: "",
                title = backStackEntry.arguments?.getString("title") ?: "Offline video",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Details.route,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("apiName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            // NavController already decodes path arguments once - do NOT decode again.
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val apiName = backStackEntry.arguments?.getString("apiName") ?: ""

            DetailsScreen(
                url = url,
                apiName = apiName,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { contentUrl, providerName, title, poster, episodeData, episodeName ->
                    navController.navigate(
                        Screen.Player.createRoute(contentUrl, providerName, title, poster, episodeData, episodeName)
                    )
                }
            )
        }

        composable(
            route = Screen.Player.route + "?episodeData={episodeData}&episodeName={episodeName}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("apiName") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType },
                navArgument("posterUrl") { type = NavType.StringType; nullable = true },
                navArgument("episodeData") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("episodeName") { type = NavType.StringType; nullable = true; defaultValue = null },
            )
        ) { backStackEntry ->
            // NavController already decodes arguments once - do NOT decode again.
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val apiName = backStackEntry.arguments?.getString("apiName") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""
            val poster = backStackEntry.arguments?.getString("posterUrl")
            val epData = backStackEntry.arguments?.getString("episodeData")
            val epName = backStackEntry.arguments?.getString("episodeName")

            PlayerScreen(
                url = url,
                apiName = apiName,
                name = name,
                posterUrl = poster,
                episodeData = epData,
                episodeName = epName,
                onNavigateToDetails = { detailUrl, detailApiName ->
                    navController.navigate(Screen.Details.createRoute(detailUrl, detailApiName))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { url, apiName ->
                    navController.navigate(Screen.Details.createRoute(url, apiName))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
