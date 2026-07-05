package com.lagradost.player.impl

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.player.api.MediaPlayer
import com.lagradost.player.api.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class KodiAuthException(message: String) : Exception(message)

object KodiConfig {
    var host: String = "localhost"
    var port: Int = 8080
    var username: String = ""
    var password: String = ""
    var httpProto: String = "http"

    val baseUrl: String get() = "$httpProto://$host:$port/jsonrpc"

    fun isConfigured(): Boolean = host.isNotBlank() && port > 0

    val authHeaderValue: String? get() {
        if (username.isBlank()) return null
        val raw = "$username:$password"
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw.toByteArray())
    }
}

class KodiPlayer : MediaPlayer {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    override suspend fun play(link: ExtractorLink, title: String?, subtitles: List<String>, startPositionMs: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                if (!KodiConfig.isConfigured()) {
                    val err = "Kodi not configured. Set host/port in Settings."
                    _state.update { it.copy(isLoading = false, error = err) }
                    return@withContext Result.failure(IllegalStateException(err))
                }

                PlayerLinkHandler.playbackSupport(link, PlayerLinkHandler.PlayerBackend.KODI).let { support ->
                    if (!support.supported) {
                        val error = IllegalArgumentException(support.reason)
                        _state.update { it.copy(isLoading = false, error = error.message) }
                        return@withContext Result.failure(error)
                    }
                }

                val validated = PlayerLinkHandler.validate(link, title).getOrElse { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                    return@withContext Result.failure(error)
                }

                val fileUrl = if (validated.headers.isEmpty()) {
                    validated.url
                } else {
                    val encodedHeaders = validated.headers.entries.joinToString("&") { (key, value) ->
                        val encodedKey = java.net.URLEncoder.encode(key, Charsets.UTF_8)
                        val encodedValue = java.net.URLEncoder.encode(value, Charsets.UTF_8)
                        "$encodedKey=$encodedValue"
                    }
                    "${validated.url}|$encodedHeaders"
                }
                val displayName = validated.displayTitle.ifBlank { "CloudStream" }

                val json = buildJsonRpc(
                    "Player.Open",
                    mapOf("item" to mapOf("file" to fileUrl))
                )

                val response = try {
                    sendJsonRpc(json)
                } catch (e: KodiAuthException) {
                    _state.update { it.copy(isLoading = false, error = e.message) }
                    return@withContext Result.failure(e)
                }
                if (response.contains("error")) {
                    val err = "Kodi error: $response"
                    AppLogger.e(err)
                    _state.update { it.copy(isLoading = false, error = err) }
                    return@withContext Result.failure(Exception(err))
                }

                _state.update {
                    it.copy(
                        isPlaying = true,
                        isLoading = false,
                        currentUrl = validated.url,
                    )
                }
                AppLogger.i("KodiPlayer: Sent $displayName to Kodi at ${KodiConfig.baseUrl}")
                Result.success(Unit)
            } catch (e: Exception) {
                val refused = e is java.net.ConnectException
                val err = if (refused) {
                    "Kodi is not reachable at ${KodiConfig.host}:${KodiConfig.port}. Start Kodi and enable its web server, or choose MPV/VLC."
                } else {
                    "Kodi connection failed: ${e.message ?: e.javaClass.simpleName}"
                }
                AppLogger.e(err)
                _state.update { it.copy(isPlaying = false, isLoading = false, error = err) }
                Result.failure(IllegalStateException(err, e))
            }
        }

    override fun playLocal(file: File) {
        // Local file playback via Kodi is possible but needs file path resolution
        AppLogger.i("KodiPlayer: Local file playback not implemented")
    }

    override fun pause() {
        sendSimpleCommand("Player.PlayPause", mapOf("playerid" to 1))
    }

    override fun resume() {
        sendSimpleCommand("Player.PlayPause", mapOf("playerid" to 1))
    }

    override fun seek(positionMs: Long) {
        // Kodi seeks in seconds via Player.Seek
        val seconds = positionMs / 1000L
        sendSimpleCommand("Player.Seek", mapOf("playerid" to 1, "value" to mapOf("seconds" to seconds)))
    }

    override fun stop() {
        sendSimpleCommand("Player.Stop", mapOf("playerid" to 1))
        _state.update { it.copy(isPlaying = false, isFinished = true) }
    }

    override fun destroy() {
        stop()
    }

    private fun sendSimpleCommand(method: String, params: Map<String, Any>) {
        try {
            sendJsonRpc(buildJsonRpc(method, params))
        } catch (e: Exception) {
            AppLogger.e("Kodi command $method failed: ${e.message}")
        }
    }

    private fun buildJsonRpc(method: String, params: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"$method\",\"params\":{")
        params.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"$k\":")
            when (v) {
                is Map<*, *> -> sb.append(buildMapValue(v as Map<String, Any>))
                is String -> sb.append("\"$v\"")
                is Number -> sb.append(v)
                is Boolean -> sb.append(v)
                else -> sb.append("\"$v\"")
            }
        }
        sb.append("}}")
        return sb.toString()
    }

    private fun buildMapValue(map: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.append("{")
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"$k\":")
            when (v) {
                is Map<*, *> -> sb.append(buildMapValue(v as Map<String, Any>))
                is String -> sb.append("\"$v\"")
                is Number -> sb.append(v)
                is Boolean -> sb.append(v)
                is List<*> -> sb.append(v.joinToString(",", "[", "]") { "\"$it\"" })
                else -> sb.append("\"$v\"")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    private fun sendJsonRpc(json: String): String {
        val url = URL(KodiConfig.baseUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        KodiConfig.authHeaderValue?.let { conn.setRequestProperty("Authorization", it) }
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        try {
            conn.outputStream.write(json.toByteArray(Charsets.UTF_8))
            val responseCode = conn.responseCode
            if (responseCode == 401) {
                throw KodiAuthException(
                    "Kodi returned HTTP 401 (Unauthorized) at ${KodiConfig.baseUrl}. " +
                    "Please check your username/password in Settings or enter credentials below."
                )
            }
            return if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                throw java.io.IOException("Kodi HTTP $responseCode: $errorBody")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun testConnection(): Result<String> {
        return try {
            val response = sendJsonRpc(buildJsonRpc("JSONRPC.Version", emptyMap()))
            if (response.contains("error")) {
                Result.failure(Exception("Kodi error: $response"))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
