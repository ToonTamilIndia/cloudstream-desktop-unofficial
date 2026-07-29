package com.lagradost.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.server.utils.respondJson
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

/**
 * GET /api/episodes?url={url}
 * Get episodes for a TV series or anime
 */
fun Route.registerEpisodesRoutes() {
    get("/api/episodes") {
        val url = call.request.queryParameters["url"]
        val apiName = call.request.queryParameters["api"]

        if (url.isNullOrBlank()) {
            call.respondJson(mapOf("error" to "Query parameter 'url' is required"))
            return@get
        }

        val api = if (apiName != null) {
            APIHolder.getApiFromNameNull(apiName)
        } else {
            APIHolder.getApiFromUrlNull(url)
        }

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

        if (response == null) {
            call.respondJson(mapOf("error" to "Failed to load episodes"))
            return@get
        }

        // Handle anime with dub status
        if (response is com.lagradost.cloudstream3.AnimeLoadResponse) {
            val anime = response
            val allEpisodes = mutableListOf<Map<String, Any?>>()

            anime.episodes.forEach { (dubStatus, episodes) ->
                episodes.forEach { ep ->
                    allEpisodes.add(
                        mapOf(
                            "data" to ep.data,
                            "name" to ep.name,
                            "season" to ep.season,
                            "episode" to ep.episode,
                            "posterUrl" to api.fixUrlNull(ep.posterUrl),
                            "description" to ep.description,
                            "date" to ep.date,
                            "runtime" to ep.runTime,
                            "dubStatus" to dubStatus.name
                        )
                    )
                }
            }

            call.respondJson(mapOf(
                "name" to response.name,
                "type" to response.type.name,
                "episodes" to allEpisodes.sortedBy { (it["episode"] as? Int) ?: 0 }
            ))
        }
        // Handle TV series
        else if (response is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
            val episodes = response.episodes.map { ep ->
                mapOf(
                    "data" to ep.data,
                    "name" to ep.name,
                    "season" to ep.season,
                    "episode" to ep.episode,
                    "posterUrl" to api.fixUrlNull(ep.posterUrl),
                    "description" to ep.description,
                    "date" to ep.date,
                    "runtime" to ep.runTime
                )
            }

            call.respondJson(mapOf(
                "name" to response.name,
                "type" to response.type.name,
                "episodes" to episodes
            ))
        } else {
            call.respondJson(mapOf("error" to "Not a TV series or anime"))
        }
    }
}