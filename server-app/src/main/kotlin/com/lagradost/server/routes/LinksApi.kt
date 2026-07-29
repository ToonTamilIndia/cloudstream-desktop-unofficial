package com.lagradost.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.server.utils.respondJson
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class LinkResult(
    val name: String,
    val url: String,
    val quality: Int,
    val type: String,
    val headers: Map<String, String>?,
    val isM3u8: Boolean,
    val isDash: Boolean,
    val proxyUrl: String? = null,
    val drmKid: String? = null,
    val drmKey: String? = null,
    val drmUuid: String? = null,
    val drmLicenseUrl: String? = null,
    val audioTracks: List<AudioTrackResult>? = null,
    val source: String? = null,
    val extractorData: String? = null,
)

data class AudioTrackResult(
    val url: String,
    val headers: Map<String, String>? = null,
)

data class SubtitleResult(
    val lang: String,
    val url: String,
    val headers: Map<String, String>?,
    val proxyUrl: String? = null,
)

data class LinksResponse(
    val links: List<LinkResult>,
    val subtitles: List<SubtitleResult>,
    val sessionId: String?,
    val proxyUrl: String?
)

private suspend fun doExtractLinks(
    data: String,
    apiName: String,
    links: MutableList<LinkResult>,
    subtitles: MutableList<SubtitleResult>,
    allLinkHeaders: MutableList<Map<String, String>>,
) {
    val api = if (apiName.isNotBlank()) {
        APIHolder.getApiFromNameNull(apiName)
    } else {
        APIHolder.getApiFromUrlNull(data)
            ?: APIHolder.allProviders.firstOrNull { it.mainUrl != "NONE" }
    } ?: return

    coroutineScope {
        async {
            try {
                api.loadLinks(
                    data = data,
                    isCasting = false,
                    subtitleCallback = { sub ->
                        val sh = sub.headers
                        if (sh != null) allLinkHeaders.add(sh)
                        subtitles.add(toSubtitleResult(sub))
                    },
                    callback = { link ->
                        val h = link.getAllHeaders()
                        if (h.isNotEmpty()) allLinkHeaders.add(h)
                        links.add(toLinkResult(link))
                    }
                )
            } catch (_: Throwable) {}
        }.await()
    }
}

/**
 * POST /api/links
 * Extract stream links from a data URL (episode data)
 */
fun Route.registerLinksRoutes() {
    post("/api/links") {
        val body = call.receive<String>()
        val json = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid JSON body"))
            return@post
        }

        val data = json.optString("data", "")
        val referer = json.optString("referer", "")
        val apiName = json.optString("apiName", "")

        if (data.isBlank()) {
            call.respondJson(mapOf("error" to "Field 'data' is required"))
            return@post
        }

        val links = mutableListOf<LinkResult>()
        val subtitles = mutableListOf<SubtitleResult>()
        val allLinkHeaders = mutableListOf<Map<String, String>>()
        val sessionHeaders = defaultSessionHeaders(referer)

        var sessionId: String? = null
        var proxyUrl: String? = null

        runBlocking {
            doExtractLinks(data, apiName, links, subtitles, allLinkHeaders)
            mergeHeaders(allLinkHeaders, sessionHeaders)
            val result = createProxyForLinks(links, subtitles, sessionHeaders)
            sessionId = result.first
            proxyUrl = result.second
        }

        call.respondJson(LinksResponse(
            links = assignProxyUrls(links, sessionId),
            subtitles = subtitles,
            sessionId = sessionId,
            proxyUrl = proxyUrl
        ))
    }

    // GET /api/links — legacy support, delegates to shared logic
    get("/api/links") {
        val data = call.request.queryParameters["data"] ?: ""
        val referer = call.request.queryParameters["referer"] ?: ""
        val apiName = call.request.queryParameters["apiName"] ?: ""

        if (data.isBlank()) {
            call.respondJson(mapOf("error" to "Query parameter 'data' is required"))
            return@get
        }

        val links = mutableListOf<LinkResult>()
        val subtitles = mutableListOf<SubtitleResult>()
        val allLinkHeaders = mutableListOf<Map<String, String>>()
        val sessionHeaders = defaultSessionHeaders(referer)

        var sessionId: String? = null
        var proxyUrl: String? = null

        runBlocking {
            doExtractLinks(data, apiName, links, subtitles, allLinkHeaders)
            mergeHeaders(allLinkHeaders, sessionHeaders)
            val result = createProxyForLinks(links, subtitles, sessionHeaders)
            sessionId = result.first
            proxyUrl = result.second
        }

        call.respondJson(LinksResponse(
            links = assignProxyUrls(links, sessionId),
            subtitles = subtitles,
            sessionId = sessionId,
            proxyUrl = proxyUrl
        ))
    }
}
