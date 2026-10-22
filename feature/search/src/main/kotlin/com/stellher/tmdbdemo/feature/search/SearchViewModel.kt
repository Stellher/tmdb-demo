package com.stellher.tmdbdemo.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.model.MediaSearchItem
import com.stellher.tmdbdemo.core.model.MediaSearchPage
import com.stellher.tmdbdemo.core.model.MediaType
import com.stellher.tmdbdemo.core.model.TitleNormalizer
import com.stellher.tmdbdemo.core.model.TvDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

private data class SearchRequest(val query: String, val type: MediaType?)

private class SourceBucket {
    val items = mutableListOf<MediaSearchItem>()
    var fetched = false
    var nextPage: Int? = 1

    fun addPage(page: MediaSearchPage, pageNumber: Int, hasMore: Boolean) {
        (items + page.items).distinctBy { it.type to it.id }.let {
            items.clear()
            items.addAll(it)
        }
        fetched = true
        nextPage = if (hasMore) pageNumber + 1 else null
    }
}

private class CachedQuery {
    val tv = SourceBucket()
    val movies = SourceBucket()
    val tvDetails = mutableMapOf<Int, TvDetails>()
    var suggestedTitle: String? = null
}

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

    // Scoped to this ViewModel: switching tabs reuses the exact loaded objects and
    // season metadata. Cache is bounded to avoid retaining unlimited search history.
    private val cache = LinkedHashMap<String, CachedQuery>()
    private var moreJob: Job? = null

    private fun cached(query: String): CachedQuery = cache.getOrPut(query) {
        if (cache.size >= 8) cache.remove(cache.keys.first())
        CachedQuery()
    }

    private fun matches(request: SearchRequest): Boolean =
        queryFlow.value == request.query && filterFlow.value == request.type

    private fun needsFetch(data: CachedQuery, type: MediaType?): Boolean = when (type) {
        MediaType.TV -> !data.tv.fetched
        MediaType.MOVIE -> !data.movies.fetched
        null -> !data.tv.fetched || !data.movies.fetched
    }

    private fun visibleItems(data: CachedQuery, type: MediaType?): List<MediaSearchItem> = when (type) {
        MediaType.TV -> data.tv.items.toList()
        MediaType.MOVIE -> data.movies.items.toList()
        null -> {
            val tv = data.tv.items
            val movies = data.movies.items
            buildList {
                for (i in 0 until maxOf(tv.size, movies.size)) {
                    tv.getOrNull(i)?.let(::add)
                    movies.getOrNull(i)?.let(::add)
                }
            }
        }
    }

    private fun hasMore(data: CachedQuery, type: MediaType?): Boolean = when (type) {
        MediaType.TV -> data.tv.fetched && data.tv.nextPage != null
        MediaType.MOVIE -> data.movies.fetched && data.movies.nextPage != null
        null -> (data.tv.fetched && data.tv.nextPage != null) ||
            (data.movies.fetched && data.movies.nextPage != null)
    }

    private fun publish(request: SearchRequest, loading: Boolean = false) {
        if (!matches(request)) return
        val data = cache[request.query]
        val items = data?.let { visibleItems(it, request.type) }.orEmpty()
        _state.update { it.copy(
            query = request.query,
            filter = request.type,
            loading = loading,
            items = items,
            loadingMore = false,
            nextPage = if (data != null && hasMore(data, request.type)) 1 else null,
            requestedSeason = TitleNormalizer.parse(request.query).requestedSeason,
            suggestedTitle = data?.suggestedTitle,
            detailsById = data?.tvDetails?.toMap().orEmpty(),
            error = null
        ) }
    }

    init {
        viewModelScope.launch {
            combine(queryFlow.debounce(420), filterFlow) { query, type ->
                SearchRequest(query, type)
            }.distinctUntilChanged().collectLatest { request ->
                moreJob?.cancel()
                if (!matches(request)) return@collectLatest
                if (request.query.isBlank()) {
                    publish(request)
                    return@collectLatest
                }
                val entry = cached(request.query)
                publish(request, loading = needsFetch(entry, request.type))
                try {
                    val title = TitleNormalizer.parse(request.query).name
                    when (request.type) {
                        MediaType.TV -> if (!entry.tv.fetched) {
                            val page = repository.searchMedia(title, 1, MediaType.TV)
                            if (matches(request)) {
                                entry.tv.addPage(page, 1, page.tvHasMore)
                                entry.suggestedTitle = page.suggestedTitle
                            }
                        }
                        MediaType.MOVIE -> if (!entry.movies.fetched) {
                            val page = repository.searchMedia(title, 1, MediaType.MOVIE)
                            if (matches(request)) {
                                entry.movies.addPage(page, 1, page.movieHasMore)
                                entry.suggestedTitle = page.suggestedTitle
                            }
                        }
                        null -> {
                            if (!entry.tv.fetched && !entry.movies.fetched) {
                                // One combined request already queries both endpoints,
                                // and populates both category caches for instant switching.
                                val page = repository.searchMedia(title, 1, null)
                                if (matches(request)) {
                                    entry.tv.addPage(page.copy(
                                        items = page.items.filter { it.type == MediaType.TV }
                                    ), 1, page.tvHasMore)
                                    entry.movies.addPage(page.copy(
                                        items = page.items.filter { it.type == MediaType.MOVIE }
                                    ), 1, page.movieHasMore)
                                    entry.suggestedTitle = page.suggestedTitle
                                }
                            } else if (!entry.tv.fetched || !entry.movies.fetched) {
                                // Tab was initially searched alone; load only the missing
                                // endpoint when switching to All.
                                val pending = coroutineScope {
                                    buildList {
                                        if (!entry.tv.fetched) add(async {
                                            MediaType.TV to repository.searchMedia(title, 1, MediaType.TV)
                                        })
                                        if (!entry.movies.fetched) add(async {
                                            MediaType.MOVIE to repository.searchMedia(title, 1, MediaType.MOVIE)
                                        })
                                    }.awaitAll()
                                }
                                if (matches(request)) {
                                    pending.forEach { (type, page) ->
                                        when (type) {
                                            MediaType.TV -> entry.tv.addPage(page, 1, page.tvHasMore)
                                            MediaType.MOVIE -> entry.movies.addPage(page, 1, page.movieHasMore)
                                        }
                                        if (entry.suggestedTitle == null)
                                            entry.suggestedTitle = page.suggestedTitle
                                    }
                                }
                            }
                        }
                    }
                    if (matches(request)) {
                        publish(request)
                        enrichTv(visibleItems(entry, request.type), request)
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    if (matches(request)) {
                        publish(request)
                        _state.update { it.copy(
                            error = if (error.message?.contains("401") == true)
                                "TMDB 令牌无效" else "搜索失败，请检查网络后重试"
                        ) }
                    }
                }
            }
        }
    }

    private suspend fun enrichTv(items: List<MediaSearchItem>, request: SearchRequest) {
        val entry = cache[request.query] ?: return
        supervisorScope {
            val semaphore = Semaphore(3)
            items.filter { it.type == MediaType.TV }
                .distinctBy { it.id }
                .take(9)
                .filterNot { entry.tvDetails.containsKey(it.id) }
                .map { media ->
                    async {
                        semaphore.withPermit {
                            try {
                                val details = repository.getDetails(media.id)
                                if (currentCoroutineContext().isActive && matches(request)) {
                                    entry.tvDetails[media.id] = details
                                    _state.update { it.copy(
                                        detailsById = entry.tvDetails.toMap()
                                    ) }
                                }
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { /* Metadata must never block search. */ }
                        }
                    }
                }.awaitAll()
        }
    }

    fun setQuery(query: String) {
        if (queryFlow.value == query) return
        moreJob?.cancel()
        queryFlow.value = query
        val request = SearchRequest(query, filterFlow.value)
        // Render previous cached results immediately if this query was searched;
        // no stale posters from a different keyword are displayed.
        if (cache.containsKey(query)) publish(request, needsFetch(cached(query), request.type))
        else _state.update { it.copy(
            query = query, items = emptyList(), detailsById = emptyMap(),
            nextPage = null, suggestedTitle = null, error = null,
            loading = query.isNotBlank(), loadingMore = false
        ) }
    }

    fun setFilter(type: MediaType?) {
        if (filterFlow.value == type) return
        moreJob?.cancel()
        filterFlow.value = type
        val request = SearchRequest(queryFlow.value, type)
        // Instant in-memory tab switch: no Retrofit call, no loading flash if
        // the selected endpoint was already fetched from All.
        val data = cache[request.query]
        publish(request, loading = request.query.isNotBlank() &&
            (data == null || needsFetch(data, type)))
    }

    fun loadMore() {
        val current = _state.value
        val request = SearchRequest(current.query, current.filter)
        if (current.loading || current.loadingMore || !matches(request)) return
        val data = cache[request.query] ?: return
        val wanted = when (request.type) {
            MediaType.TV -> listOf(MediaType.TV)
            MediaType.MOVIE -> listOf(MediaType.MOVIE)
            null -> listOf(MediaType.TV, MediaType.MOVIE)
        }.mapNotNull { type ->
            val bucket = if (type == MediaType.TV) data.tv else data.movies
            bucket.nextPage?.takeIf { bucket.fetched }?.let { type to it }
        }
        if (wanted.isEmpty()) return
        _state.update { it.copy(loadingMore = true, error = null) }
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            try {
                val title = TitleNormalizer.parse(request.query).name
                val incoming = coroutineScope {
                    wanted.map { (type, page) ->
                        async { Triple(type, page, repository.searchMedia(title, page, type)) }
                    }.awaitAll()
                }
                if (!matches(request)) return@launch
                incoming.forEach { (type, pageNumber, result) ->
                    val bucket = if (type == MediaType.TV) data.tv else data.movies
                    bucket.addPage(result, pageNumber,
                        if (type == MediaType.TV) result.tvHasMore else result.movieHasMore)
                }
                publish(request)
                enrichTv(visibleItems(data, request.type), request)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (matches(request)) {
                    publish(request)
                    _state.update { it.copy(error = "加载更多失败，请重试") }
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
