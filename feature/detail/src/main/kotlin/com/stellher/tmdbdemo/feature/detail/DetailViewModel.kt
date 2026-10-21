package com.stellher.tmdbdemo.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.model.TvDetails
import com.stellher.tmdbdemo.core.model.TvEpisode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val details: TvDetails? = null,
    val selectedSeason: Int = 1,
    val episodes: List<TvEpisode> = emptyList(),
    val episodesLoading: Boolean = false,
    val showEpisodeSheet: Boolean = false,
    val selectedEpisode: TvEpisode? = null
)

class DetailViewModel(private val repository: MediaRepository, private val showId: Int, private val preferredSeason: Int?) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()
    private var seasonJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val details = repository.getDetails(showId)
                val initial = details.seasons.firstOrNull { it.number == preferredSeason }?.number
                    ?: details.seasons.firstOrNull { it.number == 1 }?.number
                    ?: details.seasons.firstOrNull { it.number > 0 }?.number
                    ?: details.seasons.firstOrNull()?.number ?: 1
                _state.update { it.copy(loading = false, details = details, selectedSeason = initial) }
                selectSeason(initial)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                _state.update { it.copy(loading = false, error = "加载详情失败，请返回重试") }
            }
        }
    }

    fun selectSeason(number: Int) {
        if (_state.value.selectedSeason == number && _state.value.episodes.isNotEmpty()) return
        seasonJob?.cancel()
        _state.update { it.copy(selectedSeason = number, episodesLoading = true, episodes = emptyList()) }
        seasonJob = viewModelScope.launch {
            try {
                val episodes = repository.getSeason(showId, number)
                _state.update { it.copy(episodesLoading = false, episodes = episodes) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                _state.update { it.copy(episodesLoading = false, episodes = emptyList(), error = "本季度集数获取失败") }
            }
        }
    }

    fun setSheetVisible(visible: Boolean) { _state.update { it.copy(showEpisodeSheet = visible) } }
    fun openEpisode(episode: TvEpisode) { _state.update { it.copy(selectedEpisode = episode) } }
    fun dismissEpisode() { _state.update { it.copy(selectedEpisode = null) } }

    companion object {
        fun factory(repo: MediaRepository, id: Int, preferredSeason: Int?): ViewModelProvider.Factory = viewModelFactory {
            initializer { DetailViewModel(repo, id, preferredSeason) }
        }
    }
}
