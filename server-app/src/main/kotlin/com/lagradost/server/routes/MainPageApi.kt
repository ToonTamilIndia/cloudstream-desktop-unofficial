package com.lagradost.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.server.utils.respondJson
import com.lagradost.server.utils.typeToDisplayName
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

/**
 * GET /api/mainpage?source={name}
 * Returns the provider's main page categories (Trending, Popular, etc.)
 */
fun Route.registerMainPageRoutes() {
    get("/api/mainpage") {
        val sourceName = call.request.queryParameters["source"]

        if (sourceName.isNullOrBlank()) {
            call.respondJson(mapOf("error" to "Query parameter 'source' is required"))
            return@get
        }

        val api = APIHolder.getApiFromNameNull(sourceName)
        if (api == null || !api.hasMainPage || api.mainPage.isEmpty()) {
            call.respondJson(mapOf("categories" to emptyList<Any>()))
            return@get
        }

        val categories = mutableListOf<Map<String, Any?>>()

        runBlocking {
            for (pageData in api.mainPage) {
                try {
                    val request = MainPageRequest(
                        pageData.name,
                        pageData.data,
                        pageData.horizontalImages
                    )
                    val response = api.getMainPage(1, request)
                    if (response != null && response.items.isNotEmpty()) {
                        for (item in response.items) {
                            categories.add(
                                mapOf(
                                    "name" to item.name,
                                    "isHorizontalImages" to item.isHorizontalImages,
                                    "items" to item.list.map { result ->
                                        val resultYear = (result as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year
                                            ?: (result as? com.lagradost.cloudstream3.MovieSearchResponse)?.year
                                            ?: (result as? com.lagradost.cloudstream3.AnimeSearchResponse)?.year
                                        mapOf(
                                            "name" to result.name,
                                            "url" to result.url,
                                            "apiName" to result.apiName,
                                            "type" to result.type?.name,
                                            "displayType" to typeToDisplayName(result.type),
                                            "posterUrl" to api.fixUrlNull(result.posterUrl),
                                            "id" to result.id,
                                            "quality" to result.quality?.name,
                                            "score" to result.score?.toString(),
                                            "year" to resultYear
                                        )
                                    }
                                )
                            )
                        }
                    }
                } catch (e: Throwable) {
                    // Skip failed categories
                }
            }
        }

        call.respondJson(mapOf("categories" to categories))
    }
}
