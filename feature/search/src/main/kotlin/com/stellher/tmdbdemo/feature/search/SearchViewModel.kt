package com.stellher.tmdbdemo.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.model.TvShow
import com.stellher.tmdbdemo.core.model.TvDetails
import com.stellher.tmdbdemo.core.model.TitleNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class SearchUiState(
    val query: String = "辉夜",
    val shows: List<TvShow> = emptyList(),
    val detailsById: Map<Int, TvDetails> = emptyMap(),
    val requestedSeason: Int? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val animeOnly: Boolean = false
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class SearchViewModel(private val repository: MediaRepository) : ViewModel() {
    private val queryFlow = MutableStateFlow("辉夜")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            queryFlow.debounce(420).distinctUntilChanged().collectLatest { query ->
                _state.update { it.copy(query = query, loading = true, error = null) }
                if (query.isBlank()) {
                    _state.update { it.copy(shows = emptyList(), loading = false) }
                    return@collectLatest
                }
                try {
                    val intent = TitleNormalizer.parse(query)
                    val shows = repository.searchShows(intent.name)
                    _state.update { it.copy(shows = shows, detailsById = emptyMap(), requestedSeason = intent.requestedSeason, loading = false) }
                    // Enrich only the first nine cards; cap concurrent detail requests at three.
                    supervisorScope {
                        val semaphore = Semaphore(3)
                        shows.take(9).map { item ->
                            async {
                                semaphore.withPermit {
                                    try {
                                        val details = repository.getDetails(item.id)
                                        if (currentCoroutineContext().isActive) {
                                            _state.update { old ->
                                                if (old.query != query) old else old.copy(
                                                    detailsById = old.detailsById + (item.id to details)
                                                )
                                            }
                                        }
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { /* Search remains usable if enrichment fails. */ }
                                }
                            }
                        }.awaitAll()
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (e: Exception) {
                    _state.update {
                        it.copy(loading = false, shows = emptyList(), detailsById = emptyMap(),
                            error = if (e.message?.contains("401") == true) "TMDB 访问令牌无效或缺失"
                            else "搜索失败，请检查网络或稍后重试")
                    }
                }
            }
        }
    }

    fun setQuery(value: String) { _state.update { it.copy(query = value) }; queryFlow.value = value }
    fun toggleAnimeOnly() { _state.update { it.copy(animeOnly = !it.animeOnly) } }

    companion object {
        fun factory(repository: MediaRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(repository) }
        }
    }
}
