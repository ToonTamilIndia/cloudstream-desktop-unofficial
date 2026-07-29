package com.lagradost.server.routes

import com.lagradost.player.impl.proxy.LocalStreamProxy
import com.lagradost.server.utils.respondJson
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

data class IptvChannel(
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
    val proxyUrl: String? = null,
)

data class IptvPlaylist(
    val url: String,
    val name: String,
    val channels: List<IptvChannel>,
    val groups: List<String>,
)

private val httpClient = OkHttpClient()

/**
 * Parse pipe-separated parameters appended to a stream URL.
 * e.g. "http://example.com/stream.m3u8|User-Agent=Mozilla&Referer=http://ref"
 * Returns (cleanUrl, parsedHeaders).
 */
private fun parsePipeParams(rawUrl: String): Pair<String, Map<String, String>> {
    val pipeIndex = rawUrl.indexOf('|')
    if (pipeIndex < 0) return rawUrl to emptyMap()
    val cleanUrl = rawUrl.substring(0, pipeIndex)
    val params = rawUrl.substring(pipeIndex + 1)
    val headers = mutableMapOf<String, String>()
    params.split("&").forEach { pair ->
        val eqIdx = pair.indexOf('=')
        if (eqIdx > 0) {
            val key = URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8")
            val value = URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8")
            val lower = key.lowercase()
            when {
                lower == "user-agent" -> headers["User-Agent"] = value
                lower == "referer" || lower == "referrer" -> headers["Referer"] = value
                lower == "origin" -> headers["Origin"] = value
                lower == "cookie" -> headers["Cookie"] = value
                lower == "accept" -> headers["Accept"] = value
                // Treat unknown keys as custom headers
                else -> headers[key] = value
            }
        }
    }
    return cleanUrl to headers
}

fun Route.registerIptvRoutes() {
    post("/api/iptv/load") {
        val body = call.receive<String>()
        val json = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid JSON"))
            return@post
        }
        val m3uUrl = json.optString("url", "")
        if (m3uUrl.isBlank()) {
            call.respondJson(mapOf("error" to "Field 'url' is required"))
            return@post
        }

        try {
            val request = Request.Builder().url(m3uUrl).build()
            val response = httpClient.newCall(request).execute()
            val content = response.body!!.string()

            val channels = mutableListOf<IptvChannel>()
            val channelHeaders = mutableMapOf<Int, Map<String, String>>()
            val lines = content.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("#EXTINF:")) {
                    val info = line.removePrefix("#EXTINF:")
                    val name = info.substringAfterLast(",").trim()
                    val logo = Regex("""tvg-logo="([^"]*)"""").find(info)?.groupValues?.getOrNull(1) ?: ""
                    val group = Regex("""group-title="([^"]*)"""").find(info)?.groupValues?.getOrNull(1) ?: "Ungrouped"
                    val rawUrl = lines.getOrNull(i + 1)?.trim() ?: ""
                    if (rawUrl.isNotBlank() && !rawUrl.startsWith("#")) {
                        val (cleanUrl, pipeHeaders) = parsePipeParams(rawUrl)
                        val idx = channels.size
                        channels.add(IptvChannel(name = name, url = cleanUrl, logo = logo, group = group))
                        if (pipeHeaders.isNotEmpty()) {
                            channelHeaders[idx] = pipeHeaders
                        }
                    }
                    i += 2
                } else {
                    i++
                }
            }

            val groups = channels.map { it.group }.distinct().sorted()
            val playlistName = Regex("""#PLAYLIST:(.+)""").find(content)?.groupValues?.getOrNull(1)?.trim()
                ?: m3uUrl.substringAfterLast("/").substringBeforeLast(".").ifBlank { "IPTV Playlist" }

            // Register proxy session for IPTV channel streaming
            val defaultHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept" to "*/*",
            )
            val defaultSessionId = LocalStreamProxy.registerSession(defaultHeaders)
            val channelsWithProxy = channels.mapIndexed { idx, ch ->
                val chHeaders = channelHeaders[idx]
                val sessionId = if (chHeaders != null) {
                    LocalStreamProxy.registerSession(defaultHeaders + chHeaders)
                } else {
                    defaultSessionId
                }
                ch.copy(proxyUrl = LocalStreamProxy.buildProxyUrl(sessionId, ch.url))
            }

            call.respondJson(IptvPlaylist(
                url = m3uUrl,
                name = playlistName,
                channels = channelsWithProxy,
                groups = groups,
            ))
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Failed to load playlist: ${e.message}"))
        }
    }
}
