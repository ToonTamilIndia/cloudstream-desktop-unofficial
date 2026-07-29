package com.lagradost.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.player.impl.proxy.LocalStreamProxy
import com.lagradost.server.utils.respondJson
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URI
import java.util.Base64
import java.util.UUID

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

/**
 * POST /api/links
 * Extract stream links from a data URL (episode data)
 *
 * Request body: { "data": "...", "referer": "..." }
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
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

        // Create a session for the proxy
        val sessionHeaders = mutableMapOf<String, String>()
        if (referer.isNotBlank()) {
            sessionHeaders["Referer"] = referer
            if (referer.startsWith("http://") || referer.startsWith("https://")) {
                try {
                    val uri = URI(referer)
                    sessionHeaders["Origin"] = "${uri.scheme}://${uri.host}"
                } catch (_: Exception) {}
            }
        }
        sessionHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        sessionHeaders["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        sessionHeaders["Accept-Language"] = "en-US,en;q=0.9"
        sessionHeaders["Sec-Fetch-Dest"] = "document"
        sessionHeaders["Sec-Fetch-Mode"] = "navigate"
        sessionHeaders["Sec-Fetch-Site"] = "none"
        sessionHeaders["Sec-Fetch-User"] = "?1"
        sessionHeaders["DNT"] = "1"
        sessionHeaders["Connection"] = "keep-alive"

        var sessionId: String? = null
        var proxyUrl: String? = null

        runBlocking {
            coroutineScope {
                // Find the API that can handle this data
                val api = if (apiName.isNotBlank()) {
                    APIHolder.getApiFromNameNull(apiName)
                } else {
                    APIHolder.getApiFromUrlNull(data)
                        ?: APIHolder.allProviders.firstOrNull { it.mainUrl != "NONE" }
                }

                if (api != null) {
                    async {
                        try {
                            api.loadLinks(
                                data = data,
                                isCasting = false,
                                subtitleCallback = { sub ->
                                    val sh = sub.headers
                                    if (sh != null) {
                                        allLinkHeaders.add(sh)
                                    }
                                    subtitles.add(
                                        SubtitleResult(
                                            lang = sub.lang,
                                            url = sub.url,
                                            headers = sub.headers
                                        )
                                    )
                                },
                                callback = { link ->
                                    val h = link.getAllHeaders()
                                    if (h.isNotEmpty()) {
                                        allLinkHeaders.add(h)
                                    }
                                    val drm = link as? DrmExtractorLink
                                    links.add(
                                        LinkResult(
                                            name = link.name,
                                            url = link.url,
                                            quality = link.quality,
                                            type = link.type.name,
                                            headers = h,
                                            isM3u8 = link.isM3u8,
                                            isDash = link.isDash,
                                            drmKid = drm?.kid,
                                            drmKey = drm?.key,
                                            drmUuid = drm?.uuid?.toString(),
                                            drmLicenseUrl = drm?.licenseUrl,
                                            audioTracks = link.audioTracks.takeIf { it.isNotEmpty() }?.map { track ->
                                                AudioTrackResult(
                                                    url = track.url,
                                                    headers = track.headers
                                                )
                                            }
                                        )
                                    )
                                }
                            )
                        } catch (e: Throwable) {
                            // Provider threw during link extraction — return empty links
                        }
                    }.await()
                }
            }
 
            // If we have links, create a proxy session for auth headers
            if (links.isNotEmpty()) {
                // Merge per-link headers first (take priority over defaults)
                val mergedHeaders = linkedMapOf<String, String>()
                allLinkHeaders.forEach { headers ->
                    headers.forEach { (key, value) ->
                        mergedHeaders[key] = value
                    }
                }
                // Apply session defaults only for keys not set by per-link headers
                sessionHeaders.forEach { (key, value) ->
                    if (mergedHeaders.keys.none { it.equals(key, ignoreCase = true) }) {
                        mergedHeaders[key] = value
                    }
                }
                // Deduplicate case-insensitive keys — keep the last value
                val deduped = linkedMapOf<String, String>()
                mergedHeaders.forEach { (key, value) ->
                    val existing = deduped.keys.firstOrNull { it.equals(key, ignoreCase = true) }
                    if (existing != null) {
                        deduped.remove(existing)
                    }
                    deduped[key] = value
                }
                sessionHeaders.clear()
                sessionHeaders.putAll(deduped)
                sessionId = LocalStreamProxy.registerSession(sessionHeaders)
                proxyUrl = "http://localhost:${LocalStreamProxy.port}"
                // Proxy subtitle URLs through the same session so they inherit auth headers
                subtitles.replaceAll { sub ->
                    sub.copy(proxyUrl = LocalStreamProxy.buildProxyUrl(sessionId, sub.url))
                }
            }
        }

        val linksWithProxy = if (sessionId != null) {
            links.map { link ->
                val shouldProxy = link.isM3u8 || link.isDash || link.url.contains(".mp4")
                link.copy(proxyUrl = if (shouldProxy) LocalStreamProxy.buildProxyUrl(sessionId, link.url) else null)
            }
        } else {
            links
        }

        call.respondJson(LinksResponse(
            links = linksWithProxy,
            subtitles = subtitles,
            sessionId = sessionId,
            proxyUrl = proxyUrl
        ))
    }

    // Alternative: GET /api/links?data={data}
    get("/api/links") {
        val data = call.request.queryParameters["data"] ?: ""
        val referer = call.request.queryParameters["referer"] ?: ""

        if (data.isBlank()) {
            call.respondJson(mapOf("error" to "Query parameter 'data' is required"))
            return@get
        }

        // Reuse POST logic with query params
        val apiName = call.request.queryParameters["apiName"] ?: ""
        val json = org.json.JSONObject().apply {
            put("data", data)
            put("referer", referer)
        }

        call.respondJson(
            runBlocking {
                val links = mutableListOf<LinkResult>()
                val subtitles = mutableListOf<SubtitleResult>()
                val allLinkHeaders = mutableListOf<Map<String, String>>()
                val sessionHeaders = mutableMapOf<String, String>()
                if (referer.isNotBlank()) {
                    sessionHeaders["Referer"] = referer
                    if (referer.startsWith("http://") || referer.startsWith("https://")) {
                        try {
                            val uri = java.net.URI(referer)
                            sessionHeaders["Origin"] = "${uri.scheme}://${uri.host}"
                        } catch (_: Exception) {}
                    }
                }
                sessionHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                sessionHeaders["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
                sessionHeaders["Accept-Language"] = "en-US,en;q=0.9"
                sessionHeaders["Sec-Fetch-Dest"] = "document"
                sessionHeaders["Sec-Fetch-Mode"] = "navigate"
                sessionHeaders["Sec-Fetch-Site"] = "none"
                sessionHeaders["Sec-Fetch-User"] = "?1"
                sessionHeaders["DNT"] = "1"
                sessionHeaders["Connection"] = "keep-alive"

                var sessionId: String? = null
                var proxyUrl: String? = null

                coroutineScope {
                    val api = if (apiName.isNotBlank()) {
                        APIHolder.getApiFromNameNull(apiName)
                    } else {
                        APIHolder.getApiFromUrlNull(data)
                            ?: APIHolder.allProviders.firstOrNull { it.mainUrl != "NONE" }
                    }

                    if (api != null) {
                        async {
                            try {
                                api.loadLinks(
                                    data = data,
                                    isCasting = false,
                                    subtitleCallback = { sub ->
                                        val sh = sub.headers
                                        if (sh != null) {
                                            allLinkHeaders.add(sh)
                                        }
                                        subtitles.add(
                                            SubtitleResult(
                                                lang = sub.lang,
                                                url = sub.url,
                                                headers = sh,
                                            )
                                        )
                                    },
                                    callback = { link ->
                                        val h = link.getAllHeaders()
                                        if (h.isNotEmpty()) {
                                            allLinkHeaders.add(h)
                                        }
                                        val drm = link as? DrmExtractorLink
                                        links.add(
                                            LinkResult(
                                                name = link.name,
                                                url = link.url,
                                                quality = link.quality,
                                                type = link.type.name,
                                                headers = h,
                                                isM3u8 = link.isM3u8,
                                                isDash = link.isDash,
                                                drmKid = drm?.kid,
                                                drmKey = drm?.key,
                                                drmUuid = drm?.uuid?.toString(),
                                                drmLicenseUrl = drm?.licenseUrl,
                                                audioTracks = link.audioTracks.takeIf { it.isNotEmpty() }?.map { track ->
                                                    AudioTrackResult(
                                                        url = track.url,
                                                        headers = track.headers
                                                    )
                                                }
                                            )
                                        )
                                    }
                                )
                            } catch (e: Throwable) {
                                // Provider threw during link extraction — return empty links
                            }
                        }.await()
                    }
                }

                if (links.isNotEmpty()) {
                    // Merge per-link headers first (take priority over defaults)
                    val mergedHeaders = linkedMapOf<String, String>()
                    allLinkHeaders.forEach { headers ->
                        headers.forEach { (key, value) ->
                            mergedHeaders[key] = value
                        }
                    }
                    // Apply session defaults only for keys not set by per-link headers
                    sessionHeaders.forEach { (key, value) ->
                        if (mergedHeaders.keys.none { it.equals(key, ignoreCase = true) }) {
                            mergedHeaders[key] = value
                        }
                    }
                    // Deduplicate case-insensitive keys — keep the last value
                    val deduped = linkedMapOf<String, String>()
                    mergedHeaders.forEach { (key, value) ->
                        val existing = deduped.keys.firstOrNull { it.equals(key, ignoreCase = true) }
                        if (existing != null) {
                            deduped.remove(existing)
                        }
                        deduped[key] = value
                    }
                    sessionHeaders.clear()
                    sessionHeaders.putAll(deduped)
                    sessionId = LocalStreamProxy.registerSession(sessionHeaders)
                    proxyUrl = "http://localhost:${LocalStreamProxy.port}"
                    subtitles.replaceAll { sub ->
                        sub.copy(proxyUrl = LocalStreamProxy.buildProxyUrl(sessionId, sub.url))
                    }
                }

                val linksWithProxy = if (sessionId != null) {
                    links.map { link ->
                        val shouldProxy = link.isM3u8 || link.isDash || link.url.contains(".mp4")
                        link.copy(proxyUrl = if (shouldProxy) LocalStreamProxy.buildProxyUrl(sessionId, link.url) else null)
                    }
                } else {
                    links
                }

                LinksResponse(
                    links = linksWithProxy,
                    subtitles = subtitles,
                    sessionId = sessionId,
                    proxyUrl = proxyUrl,
                )
            }
        )
    }
}