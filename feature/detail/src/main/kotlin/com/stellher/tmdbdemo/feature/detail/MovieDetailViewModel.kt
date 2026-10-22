package com.stellher.tmdbdemo.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.model.MovieDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovieUiState(
    val loading: Boolean = true,
    val movie: MovieDetails? = null,
    val error: String? = null,
    val selectionVisible: Boolean = false,
    val playbackUrl: String = "",
    val playbackError: String? = null,
    val playerVisible: Boolean = false
)

class MovieDetailViewModel(repository: MediaRepository, movieId: Int) : ViewModel() {
    private val _state = MutableStateFlow(MovieUiState())
    val state: StateFlow<MovieUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = false, movie = repository.getMovieDetails(movieId)) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                _state.update { it.copy(loading = false, error = "电影详情加载失败，请返回重试") }
            }
        }
    }

    fun openSelection() { _state.update { it.copy(selectionVisible = true, playbackError = null) } }
    fun closeSelection() { _state.update { it.copy(selectionVisible = false, playbackError = null) } }
    fun changeUrl(url: String) { _state.update { it.copy(playbackUrl = url, playbackError = null) } }
    fun playMovie() {
        val url = _state.value.playbackUrl.trim()
        val parsed = android.net.Uri.parse(url)
        if (parsed.scheme?.lowercase() != "https" || parsed.host.isNullOrBlank()) {
            _state.update { it.copy(playbackError = "请输入有效的 HTTPS 视频地址") }
            return
        }
        _state.update { it.copy(playbackUrl = url, selectionVisible = false,
            playerVisible = true, playbackError = null) }
    }
    fun closePlayer() { _state.update { it.copy(playerVisible = false) } }

    companion object {
        fun factory(repository: MediaRepository, movieId: Int): ViewModelProvider.Factory =
            viewModelFactory { initializer { MovieDetailViewModel(repository, movieId) } }
    }
}
