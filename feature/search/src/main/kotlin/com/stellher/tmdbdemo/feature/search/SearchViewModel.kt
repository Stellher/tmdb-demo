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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// "ALL" maps to null (TV + MOVIE); other filters match the /search endpoint.
private data class SearchRequest(val query: String, val type: MediaType?)

data class SearchUiState(
    val query: String = "辉夜",
    val items: List<MediaSearchItem> = emptyList(),
    val detailsById: Map<Int, TvDetails> = emptyMap(),
    val requestedSeason: Int? = null,
    val suggestedTitle: String? = null,
    val filter: MediaType? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val nextPage: Int? = null,
    val error: String? = null
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class SearchViewModel(private val repository: MediaRepository) : ViewModel() {
    private val queryFlow = MutableStateFlow("辉夜")
    private val filterFlow = MutableStateFlow<MediaType?>(null)
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var moreJob: Job? = null

    init {
        viewModelScope.launch {
            combine(queryFlow.debounce(420), filterFlow) { query, type ->
                SearchRequest(query, type)
            }.distinctUntilChanged().collectLatest { request ->
                moreJob?.cancel()
                _state.update {
                    it.copy(query = request.query, filter = request.type,
                        loading = true, loadingMore = false, items = emptyList(),
                        detailsById = emptyMap(), requestedSeason = null,
                        suggestedTitle = null, nextPage = null, error = null)
                }
                if (request.query.isBlank()) {
                    _state.update { it.copy(loading = false) }
                    return@collectLatest
                }
                try {
                    val parsed = TitleNormalizer.parse(request.query)
                    val page = repository.searchMedia(parsed.name, 1, request.type)
                    _state.update { current ->
                        if (!matches(request, current)) current else current.copy(
                            items = page.items,
                            requestedSeason = parsed.requestedSeason,
                            suggestedTitle = page.suggestedTitle,
                            nextPage = if (page.hasMore) 2 else null,
                            loading = false
                        )
                    }
                    enrichTv(page.items, request)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    _state.update {
                        if (!matches(request, it)) it else it.copy(
                            loading = false,
                            error = if (error.message?.contains("401") == true)
                                "TMDB 令牌无效" else "搜索失败，请检查网络后重试"
                        )
                    }
                }
            }
        }
    }

    private fun matches(request: SearchRequest, state: SearchUiState) =
        state.query == request.query && state.filter == request.type &&
            queryFlow.value == request.query && filterFlow.value == request.type

    private suspend fun enrichTv(items: List<MediaSearchItem>, request: SearchRequest) {
        supervisorScope {
            val semaphore = Semaphore(3)
            items.filter { it.type == MediaType.TV }.take(9).map { media ->
                async {
                    semaphore.withPermit {
                        try {
                            val details = repository.getDetails(media.id)
                            if (currentCoroutineContext().isActive) {
                                _state.update { current ->
                                    if (!matches(request, current)) current else current.copy(
                                        detailsById = current.detailsById + (media.id to details)
                                    )
                                }
                            }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { /* Search remains usable without details. */ }
                    }
                }
            }.awaitAll()
        }
    }

    fun setQuery(query: String) {
        if (queryFlow.value == query) return
        moreJob?.cancel()
        _state.update { it.copy(query = query, items = emptyList(),
            loading = query.isNotBlank(), loadingMore = false,
            suggestedTitle = null, nextPage = null, error = null) }
        queryFlow.value = query
    }

    fun setFilter(type: MediaType?) {
        if (filterFlow.value == type) return
        moreJob?.cancel()
        _state.update { it.copy(filter = type, items = emptyList(),
            loading = it.query.isNotBlank(), loadingMore = false,
            suggestedTitle = null, nextPage = null, error = null) }
        filterFlow.value = type
    }

    fun loadMore() {
        val snapshot = _state.value
        val page = snapshot.nextPage ?: return
        val request = SearchRequest(snapshot.query, snapshot.filter)
        if (snapshot.loadingMore || snapshot.loading || !matches(request, snapshot)) return
        _state.update { it.copy(loadingMore = true, error = null) }
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            try {
                val next = repository.searchMedia(
                    TitleNormalizer.parse(request.query).name, page, request.type)
                _state.update { current ->
                    if (!matches(request, current)) current else {
                        val merged = (current.items + next.items)
                            .distinctBy { it.type to it.id }
                        current.copy(items = merged, loadingMore = false,
                            nextPage = if (next.hasMore) page + 1 else null)
                    }
                }
                if (matches(request, _state.value)) enrichTv(next.items, request)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                _state.update {
                    if (!matches(request, it)) it else it.copy(
                        loadingMore = false, error = "加载更多失败，请重试"
                    )
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
