package com.stellher.tmdbdemo.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.model.MediaSearchItem
import com.stellher.tmdbdemo.core.model.MediaType
import com.stellher.tmdbdemo.core.model.TvSeason
import com.stellher.tmdbdemo.core.ui.GlassPanel
import com.stellher.tmdbdemo.core.ui.ImageKind
import com.stellher.tmdbdemo.core.ui.Ink
import com.stellher.tmdbdemo.core.ui.SecondaryInk
import com.stellher.tmdbdemo.core.ui.TmdbImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun SearchScreen(
    repository: MediaRepository,
    onSelected: (MediaType, Int, Int?) -> Unit
) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(repository))
    val state by vm.state.collectAsState()
    val haze = remember { HazeState() }
    val results = remember(state.items, state.detailsById, state.requestedSeason) {
        // Backend already fetched this category; no genre-based client-side filtering.
        state.items.flatMap { media ->
            if (media.type == MediaType.MOVIE) {
                listOf(SearchCardItem(media))
            } else {
                val details = state.detailsById[media.id]
                val seasons = details?.seasons.orEmpty()
                    .filter { it.number > 0 }
                    .filter { state.requestedSeason == null || it.number == state.requestedSeason }
                    .take(12)
                if (seasons.isEmpty()) {
                    listOf(SearchCardItem(media,
                        showEpisodeCount = details?.show?.numberOfEpisodes,
                        lastAired = details?.lastEpisode?.number))
                } else {
                    seasons.map { season -> SearchCardItem(media, season) }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FC))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 148.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("探索影视", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("全部 / TV / 电影 · 按 TMDB 搜索类型", fontSize = 12.sp, color = SecondaryInk)
                }
            }
            if (state.suggestedTitle != null && !state.loading) {
                item {
                    Text(
                        "未找到完全匹配，可能是：${state.suggestedTitle}（近似片名）",
                        color = Color(0xFF4C69D9), fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    )
                }
            }
            if (state.loading && state.items.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    Text(state.error ?: "当前分类暂无结果，试试其他片名或分类",
                        color = SecondaryInk, modifier = Modifier.padding(20.dp))
                }
            } else {
                items(
                    items = results.chunked(3),
                    key = { row ->
                        row.joinToString("-") {
                            it.media.type.name + "_" + it.media.id + "_" + (it.season?.number ?: -1)
                        }
                    }
                ) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(3) { col ->
                            if (col < row.size) {
                                Box(Modifier.weight(1f)) {
                                    val card = row[col]
                                    MediaCard(card) {
                                        onSelected(card.media.type, card.media.id,
                                            if (card.media.type == MediaType.TV)
                                                card.season?.number ?: state.requestedSeason
                                            else null)
                                    }
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (state.nextPage != null && !state.loading) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(onClick = vm::loadMore, enabled = !state.loadingMore) {
                            if (state.loadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(18.dp).height(18.dp),
                                    strokeWidth = 2.dp)
                            } else Text("加载更多")
                        }
                    }
                }
            }
            if (state.error != null && results.isNotEmpty()) {
                item { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
            item {
                Text("本产品使用 TMDB API，但未经 TMDB 认可或认证。",
                    color = SecondaryInk, fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        GlassPanel(
            hazeState = haze,
            modifier = Modifier.align(Alignment.TopCenter)
                .fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            radius = 25
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    placeholder = { Text("搜索电影、番剧、电视剧…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "搜索") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        null to "全部",
                        MediaType.TV to "TV",
                        MediaType.MOVIE to "电影"
                    ).forEach { (filter, title) ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { vm.setFilter(filter) },
                            label = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    }
}

private data class SearchCardItem(
    val media: MediaSearchItem,
    val season: TvSeason? = null,
    val showEpisodeCount: Int? = null,
    val lastAired: Int? = null
)

@Composable
private fun MediaCard(card: SearchCardItem, onClick: () -> Unit) {
    val media = card.media
    val season = card.season
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(13.dp))) {
            TmdbImage(season?.posterPath ?: media.posterPath, ImageKind.Poster,
                Modifier.fillMaxSize(), description = media.title)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(52.dp)
                .background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f)))))
            val badge = when {
                media.type == MediaType.MOVIE -> "电影"
                season != null -> "全" + season.episodeCount + "集"
                card.lastAired != null -> "更新至第" + card.lastAired + "集"
                (card.showEpisodeCount ?: 0) > 0 -> "共" + card.showEpisodeCount + "集"
                else -> "TV"
            }
            Text(badge, Modifier.align(Alignment.BottomEnd).padding(8.dp),
                color = Color.White, fontSize = 11.sp, maxLines = 1)
        }
        Text(media.title + (season?.let { " · 第" + it.number + "季" } ?: ""),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val kind = if (media.type == MediaType.MOVIE) "电影" else "TV"
        Text(
            (season?.airDate?.take(4) ?: media.year) + " / " + kind +
                (media.countries.firstOrNull()?.let { " / " + it } ?: ""),
            fontSize = 10.sp, color = SecondaryInk, maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
