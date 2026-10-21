package com.stellher.tmdbdemo.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.model.TvShow
import com.stellher.tmdbdemo.core.ui.GlassPanel
import com.stellher.tmdbdemo.core.ui.ImageKind
import com.stellher.tmdbdemo.core.ui.Ink
import com.stellher.tmdbdemo.core.ui.SecondaryInk
import com.stellher.tmdbdemo.core.ui.TmdbImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun SearchScreen(repository: MediaRepository, onSelected: (Int) -> Unit) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(repository))
    val state by vm.state.collectAsState()
    val haze = rememberHazeState()
    val results = remember(state.shows, state.animeOnly) {
        if (state.animeOnly) state.shows.filter { it.isAnimation } else state.shows
    }
    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FC))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 148.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("探索影视", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("找到你想看的每一季与每一集", fontSize = 12.sp, color = SecondaryInk)
                }
            }
            if (state.loading && state.shows.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (state.error != null) {
                item { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) }
            } else if (results.isEmpty()) {
                item { Text("暂无匹配作品，试试其他片名", color = SecondaryInk, modifier = Modifier.padding(20.dp)) }
            } else {
                items(results.chunked(3), key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(3) { column ->
                            if (column < row.size) {
                                Box(Modifier.weight(1f)) {
                                    ShowCard(row[column]) { onSelected(row[column].id) }
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        GlassPanel(
            hazeState = haze,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            radius = 25
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    placeholder = { Text("搜索番剧、电视剧…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "搜索") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.animeOnly,
                        onClick = { if (state.animeOnly) vm.toggleAnimeOnly() },
                        label = { Text("全部剧集", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = state.animeOnly,
                        onClick = { if (!state.animeOnly) vm.toggleAnimeOnly() },
                        label = { Text("动画", fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowCard(show: TvShow, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(13.dp))) {
            TmdbImage(show.posterPath, ImageKind.Poster, Modifier.fillMaxSize(), description = show.title)
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(52.dp).background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f)))
                )
            )
            val update = show.lastAiredEpisode
            val status = when {
                update != null -> "更新至第" + update.number + "集"
                show.numberOfEpisodes != null && show.numberOfEpisodes > 0 -> "共" + show.numberOfEpisodes + "集"
                else -> "查看剧集"
            }
            Text(
                status,
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                color = Color.White, fontSize = 10.sp,
                maxLines = 1
            )
        }
        Text(show.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            show.year + " / " + (if (show.isAnimation) "动画" else "剧集") +
                (show.originCountries.firstOrNull()?.let { " / " + it } ?: ""),
            fontSize = 10.sp, color = SecondaryInk, maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
