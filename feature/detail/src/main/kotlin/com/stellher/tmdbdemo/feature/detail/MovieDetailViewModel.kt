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
    val error: String? = null
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

    companion object {
        fun factory(repository: MediaRepository, movieId: Int): ViewModelProvider.Factory =
            viewModelFactory { initializer { MovieDetailViewModel(repository, movieId) } }
    }
}
