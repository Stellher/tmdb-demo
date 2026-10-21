package com.stellher.tmdbdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stellher.tmdbdemo.core.data.TmdbRepository
import com.stellher.tmdbdemo.core.ui.Ink
import com.stellher.tmdbdemo.core.ui.SecondaryInk
import com.stellher.tmdbdemo.core.ui.TmdbTheme
import com.stellher.tmdbdemo.feature.detail.DetailScreen
import com.stellher.tmdbdemo.feature.search.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TmdbTheme {
                var manualToken by remember { mutableStateOf("") }
                var enteredToken by remember { mutableStateOf("") }
                val token = BuildConfig.TMDB_READ_TOKEN.ifBlank { enteredToken }
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (token.isBlank()) {
                        TokenSetupPage(
                            value = manualToken,
                            onValueChange = { manualToken = it },
                            onConnect = { enteredToken = manualToken.trim() }
                        )
                    } else {
                        val repository = remember(token) { TmdbRepository(token) }
                        val nav = rememberNavController()
                        NavHost(navController = nav, startDestination = "search") {
                            composable("search") {
                                SearchScreen(repository = repository, onSelected = { id, season ->
                                    nav.navigate("details/" + id + "?season=" + (season ?: -1))
                                })
                            }
                            composable(
                                route = "details/{id}?season={season}",
                                arguments = listOf(
                                    navArgument("id") { type = NavType.IntType },
                                    navArgument("season") { type = NavType.IntType; defaultValue = -1 }
                                )
                            ) { entry ->
                                val id = entry.arguments?.getInt("id") ?: return@composable
                                DetailScreen(showId = id, initialSeason = entry.arguments?.getInt("season")?.takeIf { it > 0 }, repository = repository, onBack = {
                                    nav.popBackStack()
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenSetupPage(value: String, onValueChange: (String) -> Unit, onConnect: () -> Unit) {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFF5F7FC)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.Movie, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF4C69D9))
            Text("TMDB Demo", fontSize = 29.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("当前 APK 未预置访问令牌。可在此临时输入 TMDB API Read Access Token，或通过安全构建参数将令牌内置。", fontSize = 13.sp, color = SecondaryInk, lineHeight = 20.sp)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("TMDB API Read Access Token") },
                singleLine = false,
                minLines = 3,
                maxLines = 5
            )
            Button(onClick = onConnect, enabled = value.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("连接 TMDB")
            }
            Text("手动输入仅在当前应用进程内保留，不上传至其他服务。", color = SecondaryInk, fontSize = 11.sp)
        }
    }
}
