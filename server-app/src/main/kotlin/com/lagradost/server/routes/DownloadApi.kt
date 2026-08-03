package com.lagradost.server.routes

import com.lagradost.common.download.DownloadManager
import com.lagradost.common.download.DownloadStatus
import com.lagradost.common.download.DownloadableStream
import com.lagradost.server.utils.respondJson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

private fun defaultDownloadHeaders(): MutableMap<String, String> = defaultSessionHeaders("")

/**
 * Build a DownloadableStream from the extracted link payload.
 * Merges per-link headers over the default session headers, giving
 * CDN protection (Referer/Cookie/User-Agent) the best chance of succeeding.
 */
private fun buildDownloadableStream(
    url: String,
    headers: Map<String, String>?,
    title: String,
    isM3u8: Boolean,
    isDash: Boolean,
): DownloadableStream {
    val defaults = defaultDownloadHeaders()
    if (headers != null) mergeHeaders(listOf(headers), defaults)
    val filename = title.ifBlank { "download" }
    return DownloadableStream(
        url = url,
        filename = filename,
        headers = defaults,
        isM3u8 = isM3u8,
        isDash = isDash,
    )
}

/**
 * A JSON-safe view of a download task (no File objects).
 */
private fun com.lagradost.common.download.DownloadTask.toPayload(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "filename" to outFile.name,
    "path" to outFile.absolutePath,
    "status" to status.name,
    "progress" to progress,
    "bytesDownloaded" to bytesDownloaded,
    "totalBytes" to totalBytes,
    "error" to error,
)

fun Route.registerDownloadRoutes() {
    post("/api/downloads") {
        val body = try {
            call.receive<String>()
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid body"))
            return@post
        }
        val json = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid JSON body"))
            return@post
        }

        val url = json.optString("url", "")
        if (url.isBlank()) {
            call.respondJson(mapOf("error" to "Field 'url' is required"))
            return@post
        }

        val title = json.optString("title", "")
        val isM3u8 = json.optBoolean("isM3u8", false)
        val isDash = json.optBoolean("isDash", false)
        val headers = json.optJSONObject("headers")?.let { headersObj ->
            val map = mutableMapOf<String, String>()
            headersObj.keys().forEach { k -> map[k] = headersObj.optString(k, "") }
            map
        }

        try {
            val stream = buildDownloadableStream(url, headers, title, isM3u8, isDash)
            val task = DownloadManager.start(stream, title)
                ?: run {
                    call.respondJson(mapOf("error" to "A download is already in progress"))
                    return@post
                }
            call.respondJson(task.toPayload())
        } catch (e: Throwable) {
            call.respondJson(mapOf("error" to "Failed to start download: ${e.message}"))
        }
    }

    get("/api/downloads") {
        call.respondJson(mapOf("downloads" to DownloadManager.list().map { it.toPayload() }))
    }

    get("/api/downloads/{id}/status") {
        val id = call.parameters["id"] ?: ""
        val task = DownloadManager.get(id)
        if (task == null) {
            call.respondJson(mapOf("error" to "Download not found"))
            return@get
        }
        call.respondJson(task.toPayload())
    }

    delete("/api/downloads/{id}") {
        val id = call.parameters["id"] ?: ""
        DownloadManager.cancel(id)
        call.respondJson(mapOf("cancelled" to true))
    }

    get("/api/downloads/{id}/file") {
        val id = call.parameters["id"] ?: ""
        val task = DownloadManager.get(id)
        if (task == null) {
            call.respondJson(mapOf("error" to "Download not found"))
            return@get
        }
        if (task.status != DownloadStatus.COMPLETED) {
            call.respondJson(mapOf("error" to "Download not complete"))
            return@get
        }
        val file = task.outFile
        if (!file.exists()) {
            call.respondJson(mapOf("error" to "File does not exist"))
            return@get
        }
        call.respondFile(file)
    }
}