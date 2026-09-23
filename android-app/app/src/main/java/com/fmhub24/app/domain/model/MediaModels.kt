package com.fmhub24.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind { MOVIE, SERIES, EPISODE, UNKNOWN }

@Serializable
data class MediaItem(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val kind: MediaKind = MediaKind.UNKNOWN,
    val rating: Float? = null,
    val description: String? = null,
)

@Serializable
data class MediaSection(val id: String, val title: String, val items: List<MediaItem> = emptyList())

@Serializable
data class Episode(
    val id: String,
    val number: Int,
    val title: String? = null,
    val description: String? = null,
)

@Serializable
data class Season(val number: Int, val title: String? = null, val episodes: List<Episode> = emptyList())

@Serializable
data class MediaDetails(
    val item: MediaItem,
    val genres: List<String> = emptyList(),
    val audioLanguages: List<String> = emptyList(),
    val seasons: List<Season> = emptyList(),
)

@Serializable
data class MediaPage<T>(val items: List<T>, val page: Int, val hasNext: Boolean)

@Serializable
data class Stream(
    val url: String,
    val quality: Int? = null,
    val mimeType: String? = null,
    val referer: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class Subtitle(val url: String, val language: String, val format: String? = null)

@Serializable
data class WatchEntry(
    val media: MediaItem,
    val progressPercent: Int = 0,
    val label: String = "Continue watching",
)
