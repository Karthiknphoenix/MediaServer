package com.example.mediaserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mediaserver.ui.theme.MediaServerTheme
import com.example.mediaserver.ui.screens.home.HomeScreen
import com.example.mediaserver.ui.screens.library.ManageLibrariesScreen
import com.example.mediaserver.ui.screens.library.CreateLibraryScreen
import com.example.mediaserver.ui.screens.library.LibraryScreen
import com.example.mediaserver.ui.screens.settings.SettingsScreen
import com.example.mediaserver.ui.screens.player.PlayerScreen
import com.example.mediaserver.ui.screens.details.MovieDetailScreen
import com.example.mediaserver.ui.screens.identify.IdentifyScreen
import com.example.mediaserver.ui.screens.series.SeriesDetailScreen
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaServerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController, 
        startDestination = "home",
        enterTransition = { androidx.compose.animation.slideInHorizontally(initialOffsetX = { 1000 }) + androidx.compose.animation.fadeIn() },
        exitTransition = { androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -1000 }) + androidx.compose.animation.fadeOut() },
        popEnterTransition = { androidx.compose.animation.slideInHorizontally(initialOffsetX = { -1000 }) + androidx.compose.animation.fadeIn() },
        popExitTransition = { androidx.compose.animation.slideOutHorizontally(targetOffsetX = { 1000 }) + androidx.compose.animation.fadeOut() }
    ) {
        composable("home") {
            HomeScreen(
                onPlayMedia = { id -> navController.navigate("movie/$id") },
                onOpenSeries = { name -> 
                    val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
                    navController.navigate("series/$encoded/detail")
                },
                onOpenLibrary = { id, name, type -> 
                    val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
                    navController.navigate("library/$id/$encodedName/$type")
                },
                onOpenSettings = { navController.navigate("settings") },
                onManageLibraries = { navController.navigate("manage_libraries") }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("manage_libraries") {
            ManageLibrariesScreen(
                onBack = { navController.popBackStack() },
                onAddLibrary = { navController.navigate("create_library") }
            )
        }

        composable("create_library") {
            CreateLibraryScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }
        
        composable(
            route = "player/{mediaId}",
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: return@composable
            PlayerScreen(
                mediaId = mediaId,
                onBack = { navController.popBackStack() }
            )
        }



        composable(
            route = "library/{libId}/{libName}/{libType}",
            arguments = listOf(
                navArgument("libId") { type = NavType.LongType },
                navArgument("libName") { type = NavType.StringType },
                navArgument("libType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val libId = backStackEntry.arguments?.getLong("libId") ?: return@composable
            val libName = URLDecoder.decode(backStackEntry.arguments?.getString("libName") ?: "", StandardCharsets.UTF_8.toString())
            val libType = backStackEntry.arguments?.getString("libType") ?: "movies"
            LibraryScreen(
                libraryId = libId,
                libraryName = libName,
                libraryType = libType,
                onPlayMedia = { id -> navController.navigate("movie/$id") },
                onOpenSeries = { seriesName ->
                    val encoded = URLEncoder.encode(seriesName, StandardCharsets.UTF_8.toString())
                    navController.navigate("series/$encoded/detail")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "movie/{mediaId}",
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: return@composable
            MovieDetailScreen(
                mediaId = mediaId,
                onPlay = { id -> navController.navigate("player/$id") },
                onBack = { navController.popBackStack() },
                onIdentify = { id, title, mediaType ->
                    val encodedTitle = URLEncoder.encode(title ?: "", StandardCharsets.UTF_8.toString())
                    val encodedType = URLEncoder.encode(mediaType ?: "movie", StandardCharsets.UTF_8.toString())
                    navController.navigate("identify/$id/$encodedTitle/$encodedType")
                }
            )
        }

        composable(
            route = "identify/{mediaId}/{title}/{mediaType}",
            arguments = listOf(
                navArgument("mediaId") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: return@composable
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", StandardCharsets.UTF_8.toString())
            val mediaType = URLDecoder.decode(backStackEntry.arguments?.getString("mediaType") ?: "", StandardCharsets.UTF_8.toString())
            IdentifyScreen(
                mediaId = mediaId,
                initialTitle = title,
                mediaType = mediaType,
                onBack = { navController.popBackStack() },
                onIdentified = { navController.popBackStack() }
            )
        }

        composable(
            route = "series/{seriesName}/detail",
            arguments = listOf(navArgument("seriesName") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedName = backStackEntry.arguments?.getString("seriesName") ?: ""
            val seriesName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.toString())
            SeriesDetailScreen(
                onBack = { navController.popBackStack() },
                onSeasonClick = { seasonNum -> 
                    // Integrated in SeriesDetailScreen
                },
                onIdentify = { name ->
                    val encodedTitle = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
                    navController.navigate("identify/0/$encodedTitle/tv")
                },
                onPlayEpisode = { id -> navController.navigate("player/$id") }
            )
        }
    }
}

