package com.lagradost.server.routes

import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.WatchHistory
import com.lagradost.server.utils.respondJson
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

data class LibraryItem(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val apiName: String,
    val url: String,
    val type: String,
    val episode: Int?,
    val season: Int?,
    val progress: Long?,
    val lastWatched: Long?,
    val isFavorite: Boolean
)

fun Route.registerLibraryRoutes() {
    get("/api/library") {
        try {
            val bookmarks = DesktopDataStore.getBookmarks()
            val watchHistory = DesktopDataStore.getAllWatchHistory()
            val historyMap = watchHistory.groupBy { it.parentId }

            val items = bookmarks.map { bm ->
                val history = historyMap[bm.id]?.maxByOrNull { it.updateTime }
                LibraryItem(
                    id = bm.id,
                    name = bm.name,
                    posterUrl = bm.posterUrl,
                    apiName = bm.apiName,
                    url = bm.url,
                    type = "series",
                    episode = history?.episode,
                    season = history?.season,
                    progress = history?.position,
                    lastWatched = history?.updateTime,
                    isFavorite = true
                )
            }

            call.respondJson(mapOf("items" to items))
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to (e.message ?: "Unknown error"), "items" to emptyList<Any>()))
        }
    }

    get("/api/library/{id}") {
        val id = call.parameters["id"]
        try {
            val bm = DesktopDataStore.getBookmarks().find { it.id == id }
            if (bm != null) {
                val history = DesktopDataStore.getAllWatchHistory()
                    .filter { it.parentId == id }
                    .maxByOrNull { it.updateTime }
                call.respondJson(mapOf(
                    "item" to mapOf(
                        "id" to bm.id,
                        "name" to bm.name,
                        "posterUrl" to bm.posterUrl,
                        "apiName" to bm.apiName,
                        "url" to bm.url,
                        "type" to "series",
                        "episode" to history?.episode,
                        "season" to history?.season,
                        "progress" to history?.position,
                        "lastWatched" to history?.updateTime,
                        "isFavorite" to true
                    )
                ))
            } else {
                call.respondJson(mapOf("error" to "Item not found"))
            }
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }

    post("/api/library") {
        val body = call.receive<String>()
        val json = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid JSON"))
            return@post
        }

        try {
            val id = json.optString("id", "")
            val name = json.optString("name", "")
            val url = json.optString("url", "")
            val apiName = json.optString("apiName", "")
            val posterUrl = json.optString("posterUrl", "").ifEmpty { null }

            if (id.isNotBlank() && name.isNotBlank()) {
                DesktopDataStore.addBookmark(
                    DesktopBookmark(
                        id = id,
                        name = name,
                        url = url,
                        apiName = apiName,
                        posterUrl = posterUrl,
                    )
                )
            }

            call.respondJson(mapOf("status" to "ok", "message" to "Library item added"))
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }

    delete("/api/library/{id}") {
        val id = call.parameters["id"]
        if (id != null) {
            DesktopDataStore.removeBookmark(id)
        }
        call.respondJson(mapOf("status" to "ok", "message" to "Library item deleted"))
    }
}