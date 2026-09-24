package com.fmhub24.app.data.provider

import com.fmhub24.app.domain.model.Episode
import com.fmhub24.app.domain.model.MediaDetails
import com.fmhub24.app.domain.model.MediaItem
import com.fmhub24.app.domain.model.MediaKind
import com.fmhub24.app.domain.model.MediaPage
import com.fmhub24.app.domain.model.MediaSection
import com.fmhub24.app.domain.model.Season
import com.fmhub24.app.domain.model.Stream
import com.fmhub24.app.domain.model.Subtitle
import com.fmhub24.app.domain.repository.ContentRepository
import com.fmhub24.app.domain.repository.ProviderConfigurator
import com.fmhub24.app.domain.repository.RepositoryResult
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Adapter for the vendored backend/moviebox-api FastAPI contract. */
class MovieboxApiRepository : ContentRepository, ProviderConfigurator {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    @Volatile private var baseUrl: String = ""

    override suspend fun configure(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = baseUrl.trim().removeSuffix("/")
        val valid = runCatching { URL(normalized).protocol in setOf("http", "https") }.getOrDefault(false)
        if (valid && normalized.isNotBlank()) {
            this@MovieboxApiRepository.baseUrl = normalized
            true
        } else false
    }

    override suspend fun home(page: Int, forceRefresh: Boolean): RepositoryResult<List<MediaSection>> =
        request("/home").map { root ->
            val sections = root.obj("sections")?.jsonArray ?: JsonArray(emptyList())
            sections.mapIndexed { index, sectionElement ->
                val section = sectionElement.jsonObject
                val title = section.string("section") ?: "Featured"
                val items = section.array("items").map { item(it.jsonObject, title) }
                MediaSection("section-$index", title, items)
            }
        }

    override suspend fun search(query: String, page: Int): RepositoryResult<MediaPage<MediaItem>> =
        request("/search?q=${encode(query)}&page=${page.coerceAtLeast(1)}").map { root ->
            val items = root.array("items").map { item(it.jsonObject, "Search") }
            val current = root.int("page") ?: page.coerceAtLeast(1)
            val total = root.int("total") ?: items.size
            MediaPage(items, current, current * items.size < total && items.isNotEmpty())
        }

    override suspend fun details(id: String): RepositoryResult<MediaDetails> =
        request("/detail/${encode(id)}").map { root ->
            val data = root.obj("data") ?: root
            val item = item(data, "Details", fallbackId = id)
            val seasons = data.array("seasons").map { season(it.jsonObject, id) }
            MediaDetails(
                item = item,
                genres = data.arrayStrings("genres") + data.arrayStrings("genre"),
                audioLanguages = data.arrayStrings("languages") + data.arrayStrings("langs"),
                seasons = seasons,
            )
        }

    override suspend fun episodes(id: String, season: Int, page: Int): RepositoryResult<MediaPage<Episode>> =
        details(id).map { details ->
            val selected = details.seasons.firstOrNull { it.number == season } ?: details.seasons.firstOrNull()
            MediaPage(selected?.episodes.orEmpty(), page.coerceAtLeast(1), false)
        }

    override suspend fun streams(episodeId: String): RepositoryResult<List<Stream>> =
        request("/api/stream/${encode(episodeId)}?detail_path=${encode(episodeId)}").map { root ->
            root.array("sources").map { source ->
                val value = source.jsonObject
                Stream(
                    url = value.string("url").orEmpty(),
                    quality = value.string("resolution")?.filter(Char::isDigit)?.toIntOrNull(),
                    mimeType = value.string("format"),
                    referer = null,
                )
            }.filter { it.url.isNotBlank() }
        }

    override suspend fun subtitles(episodeId: String): RepositoryResult<List<Subtitle>> =
        request("/api/stream/${encode(episodeId)}/captions?detail_path=${encode(episodeId)}").map { root ->
            root.array("captions").mapNotNull { caption ->
                val value = caption.jsonObject
                val url = value.string("url") ?: value.string("file") ?: return@mapNotNull null
                Subtitle(url, value.string("language") ?: value.string("lanName") ?: "Unknown", value.string("format"))
            }
        }

    private suspend fun request(path: String): RepositoryResult<JsonObject> = withContext(Dispatchers.IO) {
        val configured = baseUrl
        if (configured.isBlank()) return@withContext RepositoryResult.Failure("Configure the Moviebox API URL in Settings first.", false)
        runCatching {
            val connection = (URL(configured + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Accept", "application/json")
            }
            connection.use { response ->
                val body = response.inputStream.bufferedReader().use { it.readText() }
                if (response.responseCode !in 200..299) error("Moviebox API returned HTTP ${response.responseCode}")
                json.parseToJsonElement(body).jsonObject
            }
        }.fold(
            onSuccess = { RepositoryResult.Success(it) },
            onFailure = { RepositoryResult.Failure(it.message ?: "Moviebox API request failed") },
        )
    }

    private fun item(value: JsonObject, section: String, fallbackId: String? = null): MediaItem {
        val kind = when {
            section.contains("animation", true) -> MediaKind.SERIES
            section.contains("tv", true) || section.contains("series", true) -> MediaKind.SERIES
            else -> MediaKind.MOVIE
        }
        val subjectId = value.string("subject_id") ?: value.string("subjectId")
        val slug = value.string("slug") ?: value.string("detailPath")
        return MediaItem(
            id = slug ?: subjectId ?: fallbackId.orEmpty(),
            externalId = subjectId,
            title = value.string("name") ?: value.string("title") ?: "Untitled",
            posterUrl = value.string("poster_url") ?: value.obj("cover")?.string("url") ?: value.string("poster"),
            backdropUrl = value.string("backdrop_url") ?: value.string("banner") ?: value.string("banner_url"),
            year = value.int("year") ?: value.string("releaseDate")?.take(4)?.toIntOrNull(),
            kind = kind,
            rating = value.float("rating") ?: value.float("imdbRatingValue") ?: value.float("imdb_rating"),
            description = value.string("description"),
        )
    }

    private fun season(value: JsonObject, id: String): Season {
        val number = value.int("seasonNumber") ?: value.int("number") ?: value.int("seq") ?: 1
        val episodes = value.array("episodes").mapIndexed { index, element ->
            val episode = element.jsonObject
            Episode(
                id = episode.string("id") ?: episode.string("episodeId") ?: "$id-$number-${index + 1}",
                number = episode.int("episodeNumber") ?: episode.int("number") ?: episode.int("seq") ?: index + 1,
                title = episode.string("title") ?: episode.string("name"),
                description = episode.string("description"),
            )
        }
        return Season(number, value.string("title"), episodes)
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.array(key: String) = (this[key] as? JsonArray) ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String) = string(key)?.toIntOrNull()
    private fun JsonObject.float(key: String) = string(key)?.toFloatOrNull()
    private fun JsonObject.arrayStrings(key: String) = array(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    private fun <T, R> RepositoryResult<T>.map(transform: (T) -> R): RepositoryResult<R> = when (this) {
        is RepositoryResult.Success -> RepositoryResult.Success(transform(value))
        is RepositoryResult.Failure -> this
    }
}
EOF
