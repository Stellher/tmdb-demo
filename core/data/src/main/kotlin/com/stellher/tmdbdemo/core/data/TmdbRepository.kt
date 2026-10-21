package com.stellher.tmdbdemo.core.data

import com.google.gson.annotations.SerializedName
import com.stellher.tmdbdemo.core.model.MediaRepository
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
        @Query("include_adult") adult: Boolean = false
    ): SearchDto

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

private data class SearchDto(val results: List<ShowDto> = emptyList())

private data class GenreDto(val id: Int = 0)

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
    private val seasonCache = ConcurrentHashMap<String, List<TvEpisode>>()
    private val detailLocks = ConcurrentHashMap<Int, Mutex>()

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
