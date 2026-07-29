package com.lagradost.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.server.utils.respondJson
import io.ktor.server.application.*
import io.ktor.server.routing.*

data class SourceInfo(
    val name: String,
    val url: String,
    val lang: String,
    val hasMainPage: Boolean,
    val supportedTypes: List<String>,
    val hasQuickSearch: Boolean,
    val providerType: String,
)

/**
 * GET /api/sources - List all available sources/providers
 */
fun Route.registerSourcesRoutes() {
    get("/api/sources") {
        val excludedNames = setOf("TMDB", "Trakt", "MultiMovie")
        val sources = APIHolder.allProviders
            .filter { it.name !in excludedNames }
            .map { api ->
            SourceInfo(
                name = api.name,
                url = api.mainUrl,
                lang = api.lang,
                hasMainPage = api.hasMainPage,
                supportedTypes = api.supportedTypes.map { it.name },
                hasQuickSearch = api.hasQuickSearch,
                providerType = api.providerType.name
            )
        }
        call.respondJson(mapOf("sources" to sources))
    }

    get("/api/sources/{name}") {
        val name = call.parameters["name"]
        val api = APIHolder.getApiFromNameNull(name)
        if (api != null) {
            val info = SourceInfo(
                name = api.name,
                url = api.mainUrl,
                lang = api.lang,
                hasMainPage = api.hasMainPage,
                supportedTypes = api.supportedTypes.map { it.name },
                hasQuickSearch = api.hasQuickSearch,
                providerType = api.providerType.name
            )
            call.respondJson(info)
        } else {
            call.respondJson(mapOf("error" to "Source not found"))
        }
    }
}