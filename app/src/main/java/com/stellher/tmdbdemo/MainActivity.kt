package com.stellher.tmdbdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stellher.tmdbdemo.core.data.TmdbRepository
import com.stellher.tmdbdemo.core.ui.TmdbTheme
import com.stellher.tmdbdemo.core.model.MediaType
import com.stellher.tmdbdemo.feature.detail.DetailScreen
import com.stellher.tmdbdemo.feature.detail.MovieDetailScreen
import com.stellher.tmdbdemo.feature.search.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TmdbTheme {
                val repository = remember { TmdbRepository(BuildConfig.TMDB_READ_TOKEN) }
                val nav = rememberNavController()
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    NavHost(navController = nav, startDestination = "search") {
                        composable("search") {
                            SearchScreen(repository = repository, onSelected = { type, id, season ->
                                when (type) {
                                    MediaType.TV -> nav.navigate("details/" + id + "?season=" + (season ?: -1))
                                    MediaType.MOVIE -> nav.navigate("movie/" + id)
                                }
                            })
                        }
                        composable(
                            route = "movie/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.IntType })
                        ) { entry ->
                            val id = entry.arguments?.getInt("id") ?: return@composable
                            MovieDetailScreen(movieId = id, repository = repository,
                                onBack = { nav.popBackStack() })
                        }
                        composable(
                            route = "details/{id}?season={season}",
                            arguments = listOf(
                                navArgument("id") { type = NavType.IntType },
                                navArgument("season") { type = NavType.IntType; defaultValue = -1 }
                            )
                        ) { entry ->
                            val id = entry.arguments?.getInt("id") ?: return@composable
                            DetailScreen(
                                showId = id,
                                initialSeason = entry.arguments?.getInt("season")?.takeIf { it > 0 },
                                repository = repository,
                                onBack = { nav.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
