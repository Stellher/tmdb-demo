package com.stellher.tmdbdemo.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.model.TvEpisode
import com.stellher.tmdbdemo.core.model.TvSeason
import com.stellher.tmdbdemo.core.ui.GlassPanel
import com.stellher.tmdbdemo.core.ui.ImageKind
import com.stellher.tmdbdemo.core.ui.Ink
import com.stellher.tmdbdemo.core.ui.SecondaryInk
import com.stellher.tmdbdemo.core.ui.TmdbImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(showId: Int, repository: MediaRepository, onBack: () -> Unit) {
    val vm: DetailViewModel = viewModel(key = "details-" + showId, factory = DetailViewModel.factory(repository, showId))
    val state by vm.state.collectAsState()
    val haze = remember { HazeState() }
    val details = state.details

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FC))) {
        if (state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (details == null) {
            Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: "无法显示详情", color = Ink)
                TextButton(onClick = onBack) { Text("返回搜索") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().hazeSource(haze),
                contentPadding = PaddingValues(bottom = 56.dp)
            ) {
                item {
                    Box(Modifier.fillMaxWidth().height(320.dp)) {
                        TmdbImage(
                            details.show.backdropPath ?: details.show.posterPath,
                            if (details.show.backdropPath != null) ImageKind.Backdrop else ImageKind.Poster,
                            modifier = Modifier.fillMaxSize(),
                            description = details.show.title
                        )
                        Box(Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.25f), Color(0xFFF5F7FC)),
                                startY = 110f
                            )
                        ))
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 17.dp)) {
                            Text(details.show.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                details.show.year + " · " + (if (details.show.isAnimation) "动画" else "电视剧") +
                                    " · " + (details.show.numberOfSeasons?.toString() ?: "—") + " 季",
                                color = SecondaryInk, fontSize = 13.sp
                            )
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("作品简介", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(details.show.overview.ifBlank { "TMDB 暂无中文简介" },
                            fontSize = 13.sp, lineHeight = 21.sp, color = SecondaryInk)
                        Spacer(Modifier.height(22.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("选集", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Spacer(Modifier.weight(1f))
                            val season = details.seasons.firstOrNull { it.number == state.selectedSeason }
                            Text(season?.let { "共" + it.episodeCount + "集" } ?: "",
                                fontSize = 12.sp, color = SecondaryInk)
                        }
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(details.seasons, key = { it.number }) { season ->
                            val selected = season.number == state.selectedSeason
                            Box(
                                Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) Color(0xFF4C69D9) else Color.White)
                                    .clickable { vm.selectSeason(season.number) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    if (season.number == 0) "特别篇" else "第" + season.number + "季",
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    color = if (selected) Color.White else Ink
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
                if (state.episodesLoading) {
                    item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (state.episodes.isEmpty()) {
                    item { Text(state.error ?: "当前季度暂无集数信息", modifier = Modifier.padding(20.dp), color = SecondaryInk) }
                } else {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(13.dp)
                        ) {
                            items(state.episodes, key = { it.id }) { episode ->
                                EpisodeHorizontalCard(episode) { vm.openEpisode(episode) }
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 17.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { vm.setSheetVisible(true) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Collections, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("查看全部" + state.episodes.size + "集 · 展开剧情梗概")
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("播出动态", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(9.dp))
                        val latest = details.lastEpisode
                        Text(
                            if (latest != null) "最新已播 · 第" + latest.seasonNumber + "季 第" + latest.number + "集 · " + (latest.airDate ?: "日期未知")
                            else "暂无最新播出记录",
                            color = SecondaryInk, fontSize = 13.sp
                        )
                        details.nextEpisode?.let {
                            Spacer(Modifier.height(4.dp))
                            Text("下集预计 · 第" + it.seasonNumber + "季 第" + it.number + "集 · " + (it.airDate ?: "待定"),
                                color = SecondaryInk, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("播出信息来自 TMDB，可能与实际片源上线进度不同。", color = SecondaryInk, fontSize = 11.sp)
                    }
                }
            }
        }
        GlassPanel(
            hazeState = haze,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp),
            radius = 100
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
        }
    }

    if (state.showEpisodeSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.setSheetVisible(false) },
            containerColor = Color(0xFFF8F9FE)
        ) {
            Text(
                "第" + state.selectedSeason + "季 · 完整选集",
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.episodes, key = { it.id }) { episode ->
                    EpisodeExpandedRow(episode) { vm.openEpisode(episode) }
                }
            }
        }
    }
    state.selectedEpisode?.let { episode ->
        AlertDialog(
            onDismissRequest = vm::dismissEpisode,
            title = { Text("第" + episode.number + "集 · " + episode.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TmdbImage(
                        episode.stillPath, ImageKind.Still,
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                    )
                    Text((episode.airDate ?: "播出日期未提供") + " · " + episodeDuration(episode), fontSize = 12.sp, color = SecondaryInk)
                    Text(episode.overview.ifBlank { "TMDB 暂未收录这一集的剧情梗概。" }, color = Ink, lineHeight = 21.sp)
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissEpisode) { Text("关闭") } }
        )
    }
}

@Composable
private fun EpisodeHorizontalCard(episode: TvEpisode, onClick: () -> Unit) {
    Column(Modifier.width(213.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(15.dp))) {
            TmdbImage(episode.stillPath, ImageKind.Still, Modifier.fillMaxSize(), description = episode.title)
            Text(
                episodeDuration(episode),
                Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.67f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                fontSize = 10.sp, color = Color.White
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("第" + episode.number + "集 · " + episode.title,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(episode.airDate ?: "播出日期未知", color = SecondaryInk, fontSize = 11.sp)
    }
}

@Composable
private fun EpisodeExpandedRow(episode: TvEpisode, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(Color.White).clickable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        TmdbImage(
            episode.stillPath,
            ImageKind.Still,
            Modifier.width(124.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(11.dp)),
            description = episode.title
        )
        Column(Modifier.weight(1f)) {
            Text("第" + episode.number + "集 · " + episode.title, fontSize = 13.sp,
                color = Ink, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text((episode.airDate ?: "未定档") + " · " + episodeDuration(episode),
                fontSize = 10.sp, color = SecondaryInk)
            Spacer(Modifier.height(5.dp))
            Text(episode.overview.ifBlank { "暂无单集剧情梗概" }, fontSize = 11.sp,
                lineHeight = 16.sp, color = SecondaryInk, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun episodeDuration(episode: TvEpisode): String =
    episode.minutes?.takeIf { it > 0 }?.let { it.toString() + "分钟" } ?: "时长未知"
