package com.stellher.tmdbdemo.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.stellher.tmdbdemo.core.model.MovieDetails
import com.stellher.tmdbdemo.core.ui.ImageKind
import com.stellher.tmdbdemo.core.ui.Ink
import com.stellher.tmdbdemo.core.ui.SecondaryInk
import com.stellher.tmdbdemo.core.ui.TmdbImage

private fun movieImageKind(movie: MovieDetails) =
    if (movie.backdropPath != null) ImageKind.Backdrop else ImageKind.Poster

/** Movie has a single synthetic playback entry, not a TMDB season or episode. */
@Composable
internal fun MovieEpisodeSection(movie: MovieDetails, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("选集", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("正片 · 1/1", color = SecondaryInk, fontSize = 12.sp)
        }
        Column(Modifier.width(228.dp).clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) {
                TmdbImage(movie.backdropPath ?: movie.posterPath,
                    movieImageKind(movie), Modifier.fillMaxSize(), description = "正片")
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)))
                Icon(Icons.Rounded.PlayArrow, contentDescription = "选择正片",
                    tint = Color.White, modifier = Modifier.align(Alignment.Center).size(40.dp))
                Text(movie.runtimeMinutes?.let { it.toString() + "分钟" } ?: "正片",
                    color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
            Text("第1集 · 正片", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(movie.releaseDate ?: "上映日期未知", color = SecondaryInk, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovieSourceSheet(
    movie: MovieDetails,
    url: String,
    error: String?,
    onUrlChange: (String) -> Unit,
    onPlay: () -> Unit,
    onClose: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFFF7F8FC)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("选集 · 正片 1/1", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TmdbImage(movie.backdropPath ?: movie.posterPath, movieImageKind(movie),
                    Modifier.width(122.dp).aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(11.dp)))
                Column(Modifier.weight(1f)) {
                    Text("线路 1 · 正片", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(movie.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = SecondaryInk, fontSize = 12.sp)
                }
            }
            Text("TMDB 只提供电影元数据。正片需要你提供合法的播放地址；填入后可以直接在 Demo 内播放。",
                color = SecondaryInk, fontSize = 12.sp, lineHeight = 20.sp)
            OutlinedTextField(value = url, onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("正片播放地址") },
                placeholder = { Text("https://.../movie.m3u8") },
                supportingText = { Text("支持 HTTPS MP4、HLS (.m3u8)、DASH (.mpd)") })
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("播放正片")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun MoviePlayerDialog(url: String, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var error by remember(url) { mutableStateOf<String?>(null) }
    val player = remember(context, url) {
        ExoPlayer.Builder(context).build().apply {
            val suffix = url.substringBefore("?").lowercase()
            val mime = when {
                suffix.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
                suffix.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
                else -> null
            }
            val item = MediaItem.Builder().setUri(url)
            if (mime != null) item.setMimeType(mime)
            setMediaItem(item.build())
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(failure: PlaybackException) {
                error = "播放失败：" + failure.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF11131A))
            .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "关闭播放器", tint = Color.White)
                }
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AndroidView(factory = { PlayerView(it).apply {
                this.player = player
                useController = true
            } }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Text(error ?: "Media3 ExoPlayer · 当前播放用户配置的片源",
                color = if (error != null) Color(0xFFFFB4AB) else Color.White,
                modifier = Modifier.padding(horizontal = 20.dp), fontSize = 12.sp)
        }
    }
}
