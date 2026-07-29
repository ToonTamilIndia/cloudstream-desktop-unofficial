package com.lagradost.server.routes

import com.lagradost.player.impl.proxy.LocalStreamProxy
import com.lagradost.server.utils.respondJson
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import okhttp3.OkHttpClient
import okhttp3.Request

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
            val lines = content.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("#EXTINF:")) {
                    val info = line.removePrefix("#EXTINF:")
                    val name = info.substringAfterLast(",").trim()
                    val logo = Regex("""tvg-logo="([^"]*)"""").find(info)?.groupValues?.getOrNull(1) ?: ""
                    val group = Regex("""group-title="([^"]*)"""").find(info)?.groupValues?.getOrNull(1) ?: "Ungrouped"
                    val url = lines.getOrNull(i + 1)?.trim() ?: ""
                    if (url.isNotBlank() && !url.startsWith("#")) {
                        channels.add(IptvChannel(name = name, url = url, logo = logo, group = group))
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
            val sessionHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept" to "*/*",
            )
            val sessionId = LocalStreamProxy.registerSession(sessionHeaders)
            val channelsWithProxy = channels.map { ch ->
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
