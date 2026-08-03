package com.lagradost.server.routes

import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.server.utils.respondJson
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.io.File

fun Route.registerSettingsRoutes() {
    get("/api/settings/{key}") {
        val key = call.parameters["key"] ?: ""
        val value = DesktopDataStore.getKey<Any>(key)
        call.respondJson(mapOf("key" to key, "value" to value))
    }

    post("/api/settings/{key}") {
        val key = call.parameters["key"] ?: ""
        val body = call.receive<String>()
        val json = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid JSON"))
            return@post
        }
        val value = json.opt("value")
        when (value) {
            is String -> DesktopDataStore.setKey(key, value)
            is Number -> DesktopDataStore.setKey(key, value.toInt())
            is Boolean -> DesktopDataStore.setKey(key, value)
            null -> DesktopDataStore.removeKey(key)
        }
        call.respondJson(mapOf("key" to key, "value" to value))
    }

    get("/api/settings") {
        call.respondJson(mapOf(
            "global_search_enabled" to (DesktopDataStore.getKey<Boolean>("global_search_enabled") ?: false),
            "doh_provider" to (DesktopDataStore.getKey<Int>("doh_provider") ?: 2),
        ))
    }

    post("/api/settings/clear_image_cache") {
        val cacheDir = File(PlatformPaths.appDataDir, "image_cache")
        var cleared = 0L
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                cleared += file.length()
                file.delete()
            }
        }
        val sizeMb = cleared / (1024 * 1024)
        call.respondJson(mapOf("success" to true, "size" to "${sizeMb}MB cleared"))
    }
}
