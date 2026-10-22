package com.stellher.tmdbdemo.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.model.MediaSearchItem
import com.stellher.tmdbdemo.core.model.MediaType
import com.stellher.tmdbdemo.core.model.TitleNormalizer
import com.stellher.tmdbdemo.core.model.TvDetails
import kotlinx.coroutines.CancellationException
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

enum class MediaFilter { ALL, TV, MOVIE, ANIMATION }

data class SearchUiState(
    val query: String = "辉夜",
    val items: List<MediaSearchItem> = emptyList(),
    val detailsById: Map<Int, TvDetails> = emptyMap(),
    val requestedSeason: Int? = null,
    val suggestedTitle: String? = null,
    val filter: MediaFilter = MediaFilter.ALL,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val nextPage: Int? = null,
    val error: String? = null
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class SearchViewModel(private val repository: MediaRepository) : ViewModel() {
    private val queryFlow = MutableStateFlow("辉夜")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            queryFlow.debounce(420).distinctUntilChanged().collectLatest { query ->
                _state.update {
                    it.copy(query = query, loading = true, loadingMore = false, items = emptyList(),
                        detailsById = emptyMap(), requestedSeason = null, suggestedTitle = null,
                        nextPage = null, error = null)
                }
                if (query.isBlank()) {
                    _state.update { it.copy(loading = false) }
                    return@collectLatest
                }
                try {
                    val intent = TitleNormalizer.parse(query)
                    val page = repository.searchMedia(intent.name, 1)
                    _state.update {
                        if (it.query != query) it else it.copy(
                            items = page.items, requestedSeason = intent.requestedSeason,
                            suggestedTitle = page.suggestedTitle,
                            nextPage = if (page.hasMore) 2 else null,
                            loading = false)
                    }
                    enrichTv(page.items, query)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    _state.update {
                        if (it.query != query) it else it.copy(
                            loading = false, error = if (error.message?.contains("401") == true)
                                "TMDB 令牌无效" else "搜索失败，请检查网络后重试")
                    }
                }
            }
        }
    }

    private suspend fun enrichTv(items: List<MediaSearchItem>, query: String) {
        supervisorScope {
            val semaphore = Semaphore(3)
            items.filter { it.type == MediaType.TV }.take(9).map { media ->
                async {
                    semaphore.withPermit {
                        try {
                            val details = repository.getDetails(media.id)
                            if (currentCoroutineContext().isActive) {
                                _state.update { current ->
                                    if (current.query != query) current else current.copy(
                                        detailsById = current.detailsById + (media.id to details))
                                }
                            }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) { /* Keep search results without enrichment. */ }
                    }
                }
            }.awaitAll()
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun setFilter(filter: MediaFilter) { _state.update { it.copy(filter = filter) } }

    fun loadMore() {
        val snapshot = _state.value
        val page = snapshot.nextPage ?: return
        if (snapshot.loadingMore || snapshot.loading || snapshot.query != queryFlow.value) return
        _state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            try {
                val next = repository.searchMedia(TitleNormalizer.parse(snapshot.query).name, page)
                _state.update { current ->
                    if (current.query != snapshot.query) current else {
                        val merged = (current.items + next.items).distinctBy { it.type to it.id }
                        current.copy(items = merged, loadingMore = false,
                            nextPage = if (next.hasMore) page + 1 else null)
                    }
                }
                if (_state.value.query == snapshot.query) enrichTv(next.items, snapshot.query)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                _state.update {
                    if (it.query != snapshot.query) it
                    else it.copy(loadingMore = false, error = "加载更多失败，请重试")
                }
            }
        }
    }

    companion object {
        fun factory(repository: MediaRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(repository) }
        }
    }
}
