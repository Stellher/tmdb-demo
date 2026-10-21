package com.stellher.tmdbdemo.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

val Ink = Color(0xFF182238)
val SecondaryInk = Color(0xFF75809B)
val PrimaryBlue = Color(0xFF4C69D9)
val SurfaceCloud = Color(0xFFF5F7FC)
val CardWhite = Color(0xFFFFFFFF)

@Composable
fun TmdbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryBlue,
            onPrimary = Color.White,
            background = SurfaceCloud,
            onBackground = Ink,
            surface = CardWhite,
            onSurface = Ink,
            surfaceVariant = Color(0xFFEBEEF5)
        ),
        content = content
    )
}

enum class ImageKind { Poster, Still, Backdrop }

fun imageUrl(path: String?, kind: ImageKind): String? {
    if (path.isNullOrBlank()) return null
    val width = when (kind) {
        ImageKind.Poster -> "w500"
        ImageKind.Still -> "w780"
        ImageKind.Backdrop -> "w1280"
    }
    return "https://image.tmdb.org/t/p/" + width + path
}

@Composable
fun TmdbImage(
    path: String?,
    kind: ImageKind,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    description: String? = null
) {
    Box(modifier = modifier.background(Color(0xFFE5E9F2))) {
        AsyncImage(
            model = imageUrl(path, kind),
            contentDescription = description,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
fun GlassPanel(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    radius: Int = 24,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .hazeEffect(state = hazeState) {
                blurRadius = 18.dp
                noiseFactor = 0.04f
            }
            .background(Color.White.copy(alpha = 0.74f))
            .border(1.dp, Color.White.copy(alpha = 0.85f), shape)
    ) {
        content()
    }
}
