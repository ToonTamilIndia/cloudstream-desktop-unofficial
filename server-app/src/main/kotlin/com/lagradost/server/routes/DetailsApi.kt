package com.lagradost.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.server.utils.respondJson
import com.lagradost.server.utils.typeToDisplayName
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

data class DetailsResponse(
    val name: String,
    val url: String,
    val apiName: String,
    val type: String,
    val displayType: String?,
    val posterUrl: String?,
    val posterHeaders: Map<String, String>? = null,
    val year: Int?,
    val plot: String?,
    val score: String?,
    val tags: List<String>?,
    val duration: Int?,
    val recommendations: List<SearchResultItem>?,
    val actors: List<ActorData>?,
    val comingSoon: Boolean,
    val backgroundPosterUrl: String?,
    val logoUrl: String?,
    val contentRating: String?,
    val trailers: List<TrailerData>?,
    val episodes: List<EpisodeData>?,
    val dubEpisodes: Map<String, Int>?,
    val showStatus: String? = null,
    val uniqueUrl: String? = null,
    val nextAiring: NextAiringData? = null
)

data class NextAiringData(
    val episode: Int,
    val unixTime: Long,
    val season: Int? = null
)

data class ActorData(
    val name: String,
    val image: String?,
    val role: String?,
    val voiceActorName: String? = null,
    val voiceActorImage: String? = null
)

data class TrailerData(
    val url: String,
    val type: String,
    val headers: Map<String, String>? = null,
    val referer: String? = null
)

data class EpisodeData(
    val data: String,
    val name: String?,
    val season: Int?,
    val episode: Int?,
    val posterUrl: String?,
    val description: String?,
    val date: Long?,
    val runtime: Int?
)

/**
 * GET /api/details?url={url}
 * Load metadata/details for a movie, TV show, or anime
 */
fun Route.registerDetailsRoutes() {
    get("/api/details") {
        val url = call.request.queryParameters["url"]
        val apiName = call.request.queryParameters["api"]

        if (url.isNullOrBlank()) {
            call.respondJson(mapOf("error" to "Query parameter 'url' is required"))
            return@get
        }

        val api = if (apiName != null) {
            APIHolder.getApiFromNameNull(apiName)
        } else {
            null
        } ?: APIHolder.getApiFromUrlNull(url)
            ?: APIHolder.allProviders.firstOrNull { url.contains(it.mainUrl.removeSuffix("/")) }

        if (api == null) {
            call.respondJson(mapOf("error" to "Provider not found"))
            return@get
        }

        val response = runBlocking {
            try {
                api.load(url)
            } catch (e: Throwable) {
                null
            }
        }

        if (response != null) {
            val displayType = typeToDisplayName(response.type)
            val details = DetailsResponse(
                name = response.name,
                url = response.url,
                apiName = response.apiName,
                type = response.type.name,
                displayType = displayType,
                posterUrl = api.fixUrlNull(response.posterUrl),
                posterHeaders = response.posterHeaders,
                year = response.year,
                plot = response.plot,
                score = response.score?.toString(),
                tags = response.tags,
                duration = response.duration,
                recommendations = response.recommendations?.map {
                    SearchResultItem(
                        name = it.name,
                        url = it.url,
                        apiName = it.apiName,
                        type = it.type?.name,
                        displayType = typeToDisplayName(it.type),
                        posterUrl = api.fixUrlNull(it.posterUrl),
                        posterHeaders = it.posterHeaders,
                        id = it.id,
                        quality = it.quality?.name,
                        score = it.score?.toString(),
                        year = null
                    )
                },
                actors = response.actors?.map {
                    ActorData(
                        name = it.actor.name,
                        image = api.fixUrlNull(it.actor.image),
                        role = it.roleString,
                        voiceActorName = it.voiceActor?.name,
                        voiceActorImage = api.fixUrlNull(it.voiceActor?.image)
                    )
                },
                comingSoon = response.comingSoon,
                backgroundPosterUrl = api.fixUrlNull(response.backgroundPosterUrl),
                logoUrl = api.fixUrlNull(response.logoUrl),
                contentRating = response.contentRating,
                trailers = response.trailers.map { trailer ->
                    TrailerData(
                        url = trailer.extractorUrl,
                        type = if (trailer.raw) "raw" else "extractor",
                        headers = trailer.headers.ifEmpty { null },
                        referer = trailer.referer
                    )
                },
                showStatus = (response as? com.lagradost.cloudstream3.TvSeriesLoadResponse)?.showStatus?.name
                    ?: (response as? com.lagradost.cloudstream3.AnimeLoadResponse)?.showStatus?.name,
                uniqueUrl = response.uniqueUrl.takeIf { it != response.url },
                nextAiring = (response as? com.lagradost.cloudstream3.TvSeriesLoadResponse)?.nextAiring?.let {
                    NextAiringData(episode = it.episode, unixTime = it.unixTime, season = it.season)
                } ?: (response as? com.lagradost.cloudstream3.AnimeLoadResponse)?.nextAiring?.let {
                    NextAiringData(episode = it.episode, unixTime = it.unixTime, season = it.season)
                },
                episodes = (response as? com.lagradost.cloudstream3.TvSeriesLoadResponse)?.episodes?.map { ep ->
                    EpisodeData(
                        data = ep.data,
                        name = ep.name,
                        season = ep.season,
                        episode = ep.episode,
                        posterUrl = api.fixUrlNull(ep.posterUrl),
                        description = ep.description,
                        date = ep.date,
                        runtime = ep.runTime
                    )
                },
                dubEpisodes = (response as? com.lagradost.cloudstream3.AnimeLoadResponse)?.episodes?.mapKeys { (key, _) ->
                    key.name
                }?.mapValues { (_, episodes) ->
                    episodes.size
                }
            )
            call.respondJson(details)
        } else {
            call.respondJson(mapOf("error" to "Failed to load details"))
        }
    }
}