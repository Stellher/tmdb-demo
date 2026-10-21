package com.stellher.tmdbdemo.core.model

data class TvShow(
    val id: Int,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val firstAirDate: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val originCountries: List<String>,
    val isAnimation: Boolean,
    val numberOfEpisodes: Int? = null,
    val numberOfSeasons: Int? = null,
    val lastAiredEpisode: TvEpisode? = null
) {
    val year: String get() = firstAirDate?.take(4).orEmpty().ifBlank { "未知年份" }
}

data class TvSeason(
    val number: Int,
    val title: String,
    val episodeCount: Int,
    val airDate: String?,
    val posterPath: String?
)

data class TvEpisode(
    val id: Int,
    val number: Int,
    val seasonNumber: Int,
    val title: String,
    val overview: String,
    val airDate: String?,
    val minutes: Int?,
    val stillPath: String?
)

data class TvDetails(
    val show: TvShow,
    val seasons: List<TvSeason>,
    val lastEpisode: TvEpisode?,
    val nextEpisode: TvEpisode?
)

interface MediaRepository {
    suspend fun searchShows(query: String): List<TvShow>
    suspend fun getDetails(id: Int): TvDetails
    suspend fun getSeason(id: Int, season: Int): List<TvEpisode>
}
