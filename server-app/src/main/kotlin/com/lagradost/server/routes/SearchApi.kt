package com.lagradost.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.server.utils.respondJson
import com.lagradost.server.utils.typeToDisplayName
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

data class SearchResultItem(
    val name: String,
    val url: String,
    val apiName: String,
    val type: String?,
    val displayType: String?,
    val posterUrl: String?,
    val posterHeaders: Map<String, String>? = null,
    val id: Int?,
    val quality: String?,
    val score: String?,
    val year: Int?
)

/**
 * GET /api/search?q={query}&page={page}&source={source}
 * Search for movies, TV shows, anime, etc.
 */
fun Route.registerSearchRoutes() {
    get("/api/search") {
        val query = call.request.queryParameters["q"] ?: call.request.queryParameters["query"] ?: ""
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val source = call.request.queryParameters["source"]

        if (query.isBlank()) {
            call.respondJson(mapOf(
                "results" to emptyList<Any>(),
                "hasNext" to false,
                "query" to query,
                "page" to page
            ))
            return@get
        }

        val results = mutableListOf<SearchResultItem>()

        runBlocking {
            coroutineScope {
                val searchJobs = if (source != null) {
                    listOf(async {
                        APIHolder.getApiFromNameNull(source)?.search(query, page)
                    })
                } else {
                    APIHolder.allProviders.map { api ->
                        async {
                            try {
                                api.search(query, page)
                            } catch (e: Throwable) {
                                null
                            }
                        }
                    }
                }

                searchJobs.awaitAll().forEach { searchResponseList ->
                    searchResponseList?.items?.forEach { result ->
                        val provider = APIHolder.getApiFromNameNull(result.apiName)
                        results.add(
                            SearchResultItem(
                                name = result.name,
                                url = result.url,
                                apiName = result.apiName,
                                type = result.type?.name,
                                displayType = typeToDisplayName(result.type),
                                posterUrl = provider?.fixUrlNull(result.posterUrl) ?: result.posterUrl,
                                posterHeaders = result.posterHeaders,
                                id = result.id,
                                quality = result.quality?.name,
                                score = result.score?.toString(),
                                year = (result as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year
                                    ?: (result as? com.lagradost.cloudstream3.MovieSearchResponse)?.year
                                    ?: (result as? com.lagradost.cloudstream3.AnimeSearchResponse)?.year
                            )
                        )
                    }
                }
            }
        }

        call.respondJson(mapOf(
            "results" to results,
            "hasNext" to (results.size >= 20),
            "query" to query,
            "page" to page
        ))
    }

    get("/api/quicksearch") {
        val query = call.request.queryParameters["q"] ?: call.request.queryParameters["query"] ?: ""

        if (query.isBlank()) {
            call.respondJson(mapOf("error" to "Query parameter 'q' is required"))
            return@get
        }

        val results = mutableListOf<SearchResultItem>()

        runBlocking {
            coroutineScope {
                APIHolder.allProviders
                    .filter { it.hasQuickSearch }
                    .map { api ->
                        async {
                            try {
                                api.quickSearch(query)
                            } catch (e: Throwable) {
                                null
                            }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .flatten()
                    .forEach { result ->
                        val provider = APIHolder.getApiFromNameNull(result.apiName)
                        results.add(
                            SearchResultItem(
                                name = result.name,
                                url = result.url,
                                apiName = result.apiName,
                                type = result.type?.name,
                                displayType = typeToDisplayName(result.type),
                                posterUrl = provider?.fixUrlNull(result.posterUrl) ?: result.posterUrl,
                                posterHeaders = result.posterHeaders,
                                id = result.id,
                                quality = result.quality?.name,
                                score = result.score?.toString(),
                                year = null
                            )
                        )
                    }
            }
        }

        call.respondJson(mapOf("results" to results))
    }
}