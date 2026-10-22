package com.stellher.tmdbdemo.core.data

import com.google.gson.annotations.SerializedName
import com.stellher.tmdbdemo.core.model.MediaRepository
import com.stellher.tmdbdemo.core.model.MediaSearchItem
import com.stellher.tmdbdemo.core.model.MediaSearchPage
import com.stellher.tmdbdemo.core.model.MediaType
import com.stellher.tmdbdemo.core.model.TitleMatcher
import com.stellher.tmdbdemo.core.model.MovieDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.stellher.tmdbdemo.core.model.TvDetails
import com.stellher.tmdbdemo.core.model.TvEpisode
import com.stellher.tmdbdemo.core.model.TvSeason
import com.stellher.tmdbdemo.core.model.TvShow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private interface TmdbApi {
    @GET("search/tv")
    suspend fun searchShows(
        @Query("query") query: String,
        @Query("language") language: String = "zh-CN",
        @Query("include_adult") adult: Boolean = false,
        @Query("page") page: Int = 1
    ): SearchDto

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("language") language: String = "zh-CN",
        @Query("include_adult") adult: Boolean = false,
        @Query("page") page: Int = 1
    ): MovieSearchDto

    @GET("movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Int,
        @Query("language") language: String = "zh-CN"
    ): MovieDto

    @GET("tv/{id}")
    suspend fun details(
        @Path("id") id: Int,
        @Query("language") language: String = "zh-CN"
    ): ShowDto

    @GET("tv/{id}/season/{season}")
    suspend fun season(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("language") language: String = "zh-CN"
    ): SeasonDto
}

private data class SearchDto(
    val results: List<ShowDto> = emptyList(),
    @SerializedName("total_pages") val totalPages: Int = 1
)

private data class MovieSearchDto(
    val results: List<MovieDto> = emptyList(),
    @SerializedName("total_pages") val totalPages: Int = 1
)

private data class MovieDto(
    val id: Int = 0,
    val title: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("genre_ids") val genreIds: List<Int>? = null,
    val genres: List<GenreDto>? = null,
    val runtime: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null
)

private data class GenreDto(val id: Int = 0, val name: String? = null)

private data class ShowDto(
    val id: Int = 0,
    val name: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("origin_country") val originCountry: List<String>? = null,
    @SerializedName("genre_ids") val genreIds: List<Int>? = null,
    val genres: List<GenreDto>? = null,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int? = null,
    val seasons: List<SeasonDto>? = null,
    @SerializedName("last_episode_to_air") val lastEpisode: EpisodeDto? = null,
    @SerializedName("next_episode_to_air") val nextEpisode: EpisodeDto? = null
)

private data class SeasonDto(
    @SerializedName("season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    val episodes: List<EpisodeDto>? = null
)

private data class EpisodeDto(
    val id: Int = 0,
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
    @SerializedName("still_path") val stillPath: String? = null
)

private fun EpisodeDto.asModel() = TvEpisode(
    id = id, number = episodeNumber, seasonNumber = seasonNumber,
    title = name.orEmpty(), overview = overview.orEmpty(),
    airDate = airDate, minutes = runtime, stillPath = stillPath
)

private fun ShowDto.asModel(): TvShow = TvShow(
    id = id,
    title = name?.takeIf { it.isNotBlank() } ?: originalName.orEmpty(),
    originalTitle = originalName.orEmpty(),
    overview = overview.orEmpty(),
    firstAirDate = firstAirDate,
    posterPath = posterPath,
    backdropPath = backdropPath,
    originCountries = originCountry.orEmpty(),
    isAnimation = 16 in genreIds.orEmpty() || genres.orEmpty().any { it.id == 16 },
    numberOfEpisodes = numberOfEpisodes,
    numberOfSeasons = numberOfSeasons,
    lastAiredEpisode = lastEpisode?.asModel()
)

private fun ShowDto.asSearchItem() = MediaSearchItem(
    id = id, type = MediaType.TV,
    title = name?.takeIf { it.isNotBlank() } ?: originalName.orEmpty(),
    originalTitle = originalName.orEmpty(), overview = overview.orEmpty(),
    date = firstAirDate, posterPath = posterPath, backdropPath = backdropPath,
    isAnimation = 16 in genreIds.orEmpty() || genres.orEmpty().any { it.id == 16 },
    countries = originCountry.orEmpty(),
    numberOfEpisodes = numberOfEpisodes,
    lastAiredEpisode = lastEpisode?.asModel()
)

private fun MovieDto.asSearchItem() = MediaSearchItem(
    id = id, type = MediaType.MOVIE,
    title = title?.takeIf { it.isNotBlank() } ?: originalTitle.orEmpty(),
    originalTitle = originalTitle.orEmpty(), overview = overview.orEmpty(),
    date = releaseDate, posterPath = posterPath, backdropPath = backdropPath,
    isAnimation = 16 in genreIds.orEmpty() || genres.orEmpty().any { it.id == 16 }
)

private fun MovieDto.asDetails() = MovieDetails(
    id = id, title = title?.takeIf { it.isNotBlank() } ?: originalTitle.orEmpty(),
    originalTitle = originalTitle.orEmpty(), overview = overview.orEmpty(),
    releaseDate = releaseDate, posterPath = posterPath, backdropPath = backdropPath,
    runtimeMinutes = runtime, genres = genres.orEmpty().mapNotNull { it.name },
    rating = voteAverage
)

class TmdbRepository(token: String) : MediaRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "application/json")
            if (token.isNotBlank()) request.header("Authorization", "Bearer " + token)
            chain.proceed(request.build())
        }.build()

    private val api = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)

    private val detailsCache = ConcurrentHashMap<Int, TvDetails>()
    private val movieCache = ConcurrentHashMap<Int, MovieDetails>()
    private val seasonCache = ConcurrentHashMap<String, List<TvEpisode>>()
    private val detailLocks = ConcurrentHashMap<Int, Mutex>()

    /**
     * Query movie and TV endpoints independently; /search/multi may underrepresent
     * one category in its first page, and includes people we don't want.
     * Interleaving gives both media types visible positions on the first screen.
     */
    private suspend fun exactSearch(query: String, page: Int): MediaSearchPage = coroutineScope {
        val tvRequest = async { api.searchShows(query, page = page) }
        val movieRequest = async { api.searchMovies(query, page = page) }
        val tv = tvRequest.await()
        val movies = movieRequest.await()
        val tvItems = tv.results.filter { it.id > 0 }.map { it.asSearchItem() }
        val movieItems = movies.results.filter { it.id > 0 }.map { it.asSearchItem() }
        val merged = buildList {
            for (index in 0 until maxOf(tvItems.size, movieItems.size)) {
                tvItems.getOrNull(index)?.let(::add)
                movieItems.getOrNull(index)?.let(::add)
            }
        }.distinctBy { it.type to it.id }
        MediaSearchPage(merged, page < tv.totalPages || page < movies.totalPages)
    }

    override suspend fun searchMedia(query: String, page: Int): MediaSearchPage {
        if (query.isBlank()) return MediaSearchPage(emptyList(), false)
        val cleaned = query.trim()
        val primary = exactSearch(cleaned, page)
        // Never turn every page into a fuzzy search. Preserve exact results,
        // first-page ordering and API pagination whenever a relevant match exists.
        if (page > 1 || primary.items.any {
                TitleMatcher.isClose(cleaned, it.title) ||
                    TitleMatcher.isClose(cleaned, it.originalTitle)
            } || TitleMatcher.normalize(cleaned).length < 3
        ) return primary

        val discovered = mutableListOf<MediaSearchItem>()
        for (term in TitleMatcher.fallbackQueries(cleaned)) {
            // Network failures on a fallback must not hide valid primary results.
            val found = try { exactSearch(term, 1).items } catch (_: Exception) { emptyList() }
            discovered += found.filter { item ->
                TitleMatcher.isClose(cleaned, item.title) ||
                    TitleMatcher.isClose(cleaned, item.originalTitle)
            }
            if (discovered.any { TitleMatcher.score(cleaned, it.title) >= 0.75 }) break
        }

        val results = discovered.distinctBy { it.type to it.id }
            .sortedByDescending { maxOf(
                TitleMatcher.score(cleaned, it.title),
                TitleMatcher.score(cleaned, it.originalTitle)
            ) }
        if (results.isEmpty()) return primary
        // Keep the fuzzy results recognizable instead of returning every broad
        // substring match such as other titles containing "如故".
        return MediaSearchPage(
            items = results,
            hasMore = false,
            suggestedTitle = results.first().title.takeIf {
                TitleMatcher.normalize(it) != TitleMatcher.normalize(cleaned)
            }
        )
    }

    override suspend fun getMovieDetails(id: Int): MovieDetails {
        movieCache[id]?.let { return it }
        val details = api.movieDetails(id).asDetails()
        movieCache[id] = details
        return details
    }

    override suspend fun searchShows(query: String): List<TvShow> {
        if (query.isBlank()) return emptyList()
        return api.searchShows(query.trim()).results.map { it.asModel() }
            .filter { it.id != 0 }
            .distinctBy { it.id }
    }

    override suspend fun getDetails(id: Int): TvDetails {
        detailsCache[id]?.let { return it }
        // Avoid duplicated requests from rapid search-to-detail transitions.
        return detailLocks.getOrPut(id) { Mutex() }.withLock {
            detailsCache[id]?.let { return@withLock it }
            val dto = api.details(id)
            val mapped = TvDetails(
                show = dto.asModel(),
                seasons = dto.seasons.orEmpty().map {
                    TvSeason(it.seasonNumber, it.name.orEmpty(), it.episodeCount, it.airDate, it.posterPath)
                }.sortedBy { it.number },
                lastEpisode = dto.lastEpisode?.asModel(),
                nextEpisode = dto.nextEpisode?.asModel()
            )
            detailsCache[id] = mapped
            mapped
        }
    }

    override suspend fun getSeason(id: Int, season: Int): List<TvEpisode> {
        val key = id.toString() + ":" + season
        seasonCache[key]?.let { return it }
        val episodes = api.season(id, season).episodes.orEmpty()
            .map { it.asModel() }.sortedBy { it.number }
        seasonCache[key] = episodes
        return episodes
    }
}
