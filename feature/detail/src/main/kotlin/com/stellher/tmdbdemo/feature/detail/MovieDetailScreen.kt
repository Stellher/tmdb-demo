package com.stellher.tmdbdemo.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.ui.GlassPanel
import com.stellher.tmdbdemo.core.ui.ImageKind
import com.stellher.tmdbdemo.core.ui.Ink
import com.stellher.tmdbdemo.core.ui.SecondaryInk
import com.stellher.tmdbdemo.core.ui.TmdbImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun MovieDetailScreen(
    movieId: Int,
    repository: MediaRepository,
    onBack: () -> Unit
) {
    val vm: MovieDetailViewModel = viewModel(
        key = "movie-" + movieId,
        factory = MovieDetailViewModel.factory(repository, movieId)
    )
    val state by vm.state.collectAsState()
    val haze = remember { HazeState() }

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FC))) {
        if (state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (state.movie == null) {
            Column(Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: "未找到电影", color = Ink)
                TextButton(onClick = onBack) { Text("返回搜索") }
            }
        } else {
            val movie = state.movie!!
            LazyColumn(Modifier.fillMaxSize().hazeSource(haze)) {
                item {
                    Box(Modifier.fillMaxWidth().height(318.dp)) {
                        val backdrop = movie.backdropPath ?: movie.posterPath
                        TmdbImage(backdrop,
                            if (movie.backdropPath != null) ImageKind.Backdrop else ImageKind.Poster,
                            Modifier.fillMaxSize(), description = movie.title)
                        Box(Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent, Color(0xFFF5F7FC)),
                                startY = 0f)))
                        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.Bottom) {
                            TmdbImage(movie.posterPath, ImageKind.Poster,
                                Modifier.width(88.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
                                description = movie.title)
                            Spacer(Modifier.width(14.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(movie.title, color = Ink,
                                    fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                                Text("电影 · " + movie.year +
                                    (movie.runtimeMinutes?.takeIf { it > 0 }?.let { " · " + it + "分钟" } ?: ""),
                                    color = SecondaryInk, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 17.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val rating = movie.rating?.takeIf { it > 0.0 }
                            if (rating != null) {
                                Text("TMDB " + String.format(java.util.Locale.US, "%.1f", rating) + "/10",
                                    color = Color(0xFF4C69D9), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            movie.releaseDate?.takeIf { it.isNotBlank() }?.let {
                                Text("上映 " + it, color = SecondaryInk, fontSize = 12.sp)
                            }
                        }
                        if (movie.genres.isNotEmpty()) {
                            Text(movie.genres.joinToString(" · "), color = SecondaryInk, fontSize = 12.sp)
                        }
                        Text("电影简介", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(movie.overview.ifBlank { "TMDB 暂无中文简介" },
                            color = SecondaryInk, fontSize = 14.sp, lineHeight = 23.sp)
                        if (movie.originalTitle.isNotBlank() && movie.originalTitle != movie.title) {
                            Text("原名：" + movie.originalTitle, color = SecondaryInk, fontSize = 12.sp)
                        }
                        Text("电影没有 TV 季度/选集；以上资料来自 TMDB。",
                            color = SecondaryInk, fontSize = 11.sp)
                    }
                }
            }
        }

        GlassPanel(
            hazeState = haze, radius = 100,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回搜索")
            }
        }
    }
}
