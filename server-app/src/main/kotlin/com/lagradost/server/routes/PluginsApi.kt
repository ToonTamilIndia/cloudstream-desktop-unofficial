package com.lagradost.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.PluginSettingsSchemaRegistry
import com.lagradost.runtime.loader.ExtensionLoader
import com.lagradost.server.utils.respondJson
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class PluginInfo(
    val name: String,
    val version: String?,
    val fileName: String?,
    val url: String?,
    val enabled: Boolean,
    val hasUpdate: Boolean
)

fun Route.registerPluginsRoutes() {
    get("/api/plugins") {
        val disabledPlugins = DesktopDataStore.getKey<Set<String>>("disabled_plugins") ?: emptySet()

        val plugins = mutableListOf<PluginInfo>()

        // Built-in providers
        APIHolder.allProviders.filter { it.sourcePlugin == "built-in" }.forEach { api ->
            plugins.add(
                PluginInfo(
                    name = api.name,
                    version = null,
                    fileName = null,
                    url = null,
                    enabled = true,
                    hasUpdate = false
                )
            )
        }

        // Check extensions directory (recursive, matching Server.kt:loadPlugins())
        val extensionsDir = PlatformPaths.extensionsDir
        if (extensionsDir.exists()) {
            extensionsDir.walkTopDown()
                .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
                .filter { !it.name.endsWith("-jvm.jar") }
                .forEach { file ->
                    val name = file.nameWithoutExtension
                    plugins.add(
                        PluginInfo(
                            name = name,
                            version = null,
                            fileName = file.name,
                            url = null,
                            enabled = !disabledPlugins.contains(name),
                            hasUpdate = false
                        )
                    )
                }
        }

        call.respondJson(mapOf("plugins" to plugins))
    }

    post("/api/plugins/{name}/toggle") {
        val name = call.parameters["name"] ?: ""
        if (name.isBlank()) {
            call.respondJson(mapOf("error" to "Plugin name is required"))
            return@post
        }

        val body = call.receive<String>()
        val json = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid JSON"))
            return@post
        }
        val enabled = json.optBoolean("enabled", true)

        val extensionsDir = PlatformPaths.extensionsDir
        val file = if (extensionsDir.exists()) {
            extensionsDir.walkTopDown()
                .find { it.isFile && it.nameWithoutExtension == name && (it.extension == "jar" || it.extension == "cs3") }
        } else null

        if (file == null) {
            call.respondJson(mapOf("error" to "Plugin file not found: $name"))
            return@post
        }

        val disabledPlugins = (DesktopDataStore.getKey<Set<String>>("disabled_plugins") ?: emptySet()).toMutableSet()

        if (enabled) {
            // Enable: remove from disabled list and reload
            disabledPlugins.remove(name)
            DesktopDataStore.setKey("disabled_plugins", disabledPlugins)
            try {
                if (!ExtensionLoader.isPluginLoaded(file.absolutePath)) {
                    try {
                        ExtensionLoader.loadAndInit(file)
                    } catch (e: SecurityException) {
                        ExtensionLoader.loadAndInit(file, forceBypassSecurity = true)
                    }
                }
            } catch (e: Throwable) {
                call.respondJson(mapOf("error" to "Failed to load plugin: ${e.message}"))
                return@post
            }
        } else {
            // Disable: unload and add to disabled list
            try {
                ExtensionLoader.unloadPlugin(file.absolutePath)
            } catch (e: Throwable) {
                call.respondJson(mapOf("error" to "Failed to unload plugin: ${e.message}"))
                return@post
            }
            disabledPlugins.add(name)
            DesktopDataStore.setKey("disabled_plugins", disabledPlugins)
        }

        call.respondJson(mapOf("name" to name, "enabled" to enabled))
    }

    get("/api/repositories") {
        val reposJson = DesktopDataStore.getKey<String>("repos") ?: "[]"
        val repos = try {
            org.json.JSONArray(reposJson).toList().map { it.toString() }
        } catch (e: Exception) {
            emptyList<String>()
        }

        call.respondJson(mapOf("repositories" to repos))
    }

    post("/api/repositories") {
        val body = call.receive<String>()
        val json = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid JSON"))
            return@post
        }

        val url = json.optString("url", "").trim()
        if (url.isBlank()) {
            call.respondJson(mapOf("error" to "Repository URL is required"))
            return@post
        }

        val reposJson = DesktopDataStore.getKey<String>("repos") ?: "[]"
        val repos = try {
            org.json.JSONArray(reposJson).toList().map { it.toString() }.toMutableList()
        } catch (e: Exception) {
            mutableListOf<String>()
        }
        if (url !in repos) {
            repos.add(url)
            DesktopDataStore.setKey("repos", org.json.JSONArray(repos.toList()).toString())
        }
        call.respondJson(mapOf("repositories" to repos))
    }

    delete("/api/repositories/{url}") {
        val repoUrl = call.parameters["url"] ?: ""
        val reposJson = DesktopDataStore.getKey<String>("repos") ?: "[]"
        val repos = try {
            org.json.JSONArray(reposJson).toList().map { it.toString() }.toMutableList()
        } catch (e: Exception) {
            mutableListOf<String>()
        }
        repos.remove(repoUrl)
        DesktopDataStore.setKey("repos", org.json.JSONArray(repos.toList()).toString())
        call.respondJson(mapOf("repositories" to repos, "status" to "ok"))
    }

    // GET /api/plugins/browse — Fetch available plugins from all configured repos
    get("/api/plugins/browse") {
        val reposJson = DesktopDataStore.getKey<String>("repos") ?: "[]"
        val repoUrls = try {
            org.json.JSONArray(reposJson).toList().map { it.toString() }
        } catch (e: Exception) {
            emptyList<String>()
        }

        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val allPlugins = mutableListOf<Map<String, Any?>>()

        for (repoUrl in repoUrls) {
            try {
                val manifestReq = HttpRequest.newBuilder()
                    .uri(URI.create(repoUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build()
                val manifestResp = client.send(manifestReq, HttpResponse.BodyHandlers.ofString())
                if (manifestResp.statusCode() != 200) continue

                val manifestJson = org.json.JSONObject(manifestResp.body())
                val pluginLists = manifestJson.optJSONArray("pluginLists")
                    ?: continue

                for (i in 0 until pluginLists.length()) {
                    val listUrl = pluginLists.getString(i)
                    try {
                        val listReq = HttpRequest.newBuilder()
                            .uri(URI.create(listUrl))
                            .header("User-Agent", "Mozilla/5.0")
                            .GET()
                            .build()
                        val listResp = client.send(listReq, HttpResponse.BodyHandlers.ofString())
                        if (listResp.statusCode() != 200) continue

                        val listArray = org.json.JSONArray(listResp.body())
                        for (j in 0 until listArray.length()) {
                            val pluginJson = listArray.getJSONObject(j)
                            val tvTypes: List<String> = pluginJson.optJSONArray("tvTypes")?.let { arr ->
                                (0 until arr.length()).map { arr.getString(it) }
                            } ?: emptyList()
                            allPlugins.add(
                                mapOf(
                                    "name" to pluginJson.optString("name", ""),
                                    "internalName" to pluginJson.optString("internalName", ""),
                                    "version" to pluginJson.optInt("version", 0),
                                    "description" to pluginJson.optString("description", ""),
                                    "iconUrl" to pluginJson.optString("iconUrl", ""),
                                    "jarUrl" to pluginJson.optString("jarUrl", pluginJson.optString("url", "")),
                                    "language" to pluginJson.optString("language", ""),
                                    "tvTypes" to tvTypes,
                                    "repoUrl" to repoUrl,
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        call.respondJson(mapOf("plugins" to allPlugins))
    }

    // POST /api/plugins/install — Download and install a plugin from a repo
    // If a SecurityException occurs on load, returns { needsBypass: true, name: "..." }
    // Consumer should show a confirmation dialog then POST /api/plugins/install with { forceBypass: true }
    post("/api/plugins/install") {
        val body = call.receive<String>()
        val json = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid JSON"))
            return@post
        }

        val internalName = json.optString("internalName", "")
        val jarUrl = json.optString("jarUrl", "")
        val forceBypass = json.optBoolean("forceBypass", false)
        if (internalName.isBlank() || jarUrl.isBlank()) {
            call.respondJson(mapOf("error" to "Fields 'internalName' and 'jarUrl' are required"))
            return@post
        }

        val extensionsDir = PlatformPaths.extensionsDir
        extensionsDir.mkdirs()

        val targetFile = File(extensionsDir, "$internalName.jar")

        // Download JAR
        try {
            val encodedUrl = jarUrl.replace(" ", "%20")
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
            val req = HttpRequest.newBuilder()
                .uri(URI.create(encodedUrl))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
            if (resp.statusCode() != 200) {
                call.respondJson(mapOf("error" to "Download failed with status ${resp.statusCode()}"))
                return@post
            }
            resp.body().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            targetFile.delete()
            call.respondJson(mapOf("error" to "Download failed: ${e.message}"))
            return@post
        }

        // Load the plugin
        try {
            ExtensionLoader.loadAndInit(targetFile, forceBypassSecurity = forceBypass)
            call.respondJson(mapOf("success" to true, "name" to internalName))
        } catch (e: SecurityException) {
            // Security bypass needed — keep the downloaded file, tell UI to ask user
            call.respondJson(mapOf(
                "needsBypass" to true,
                "name" to internalName,
                "message" to (e.message ?: "Security verification failed. Allow unsafe plugin?"),
            ))
        } catch (e: Exception) {
            targetFile.delete()
            call.respondJson(mapOf("error" to "Installation failed: ${e.message}"))
        }
    }

    // DELETE /api/plugins/{name} — Uninstall a plugin
    delete("/api/plugins/{name}") {
        val name = call.parameters["name"] ?: ""
        if (name.isBlank()) {
            call.respondJson(mapOf("error" to "Plugin name is required"))
            return@delete
        }

        val extensionsDir = PlatformPaths.extensionsDir
        val file = if (extensionsDir.exists()) {
            extensionsDir.walkTopDown()
                .find { it.isFile && it.nameWithoutExtension == name && (it.extension == "jar" || it.extension == "cs3") }
        } else null

        if (file == null) {
            call.respondJson(mapOf("error" to "Plugin not found: $name"))
            return@delete
        }

        try {
            ExtensionLoader.unloadPlugin(file.absolutePath)
        } catch (_: Throwable) {}
        file.delete()

        // Also delete -jvm.jar companion if exists (search recursively)
        if (extensionsDir.exists()) {
            extensionsDir.walkTopDown()
                .find { it.isFile && it.name == "${name}-jvm.jar" }
                ?.delete()
        }

        call.respondJson(mapOf("success" to true, "name" to name))
    }

    // GET /api/plugins/{name}/settings — Get plugin settings schemas
    get("/api/plugins/{name}/settings") {
        val name = call.parameters["name"] ?: ""
        if (name.isBlank()) {
            call.respondJson(mapOf("error" to "Plugin name is required"))
            return@get
        }

        val prefName = PluginSettingsSchemaRegistry.findPrefNameForPlugin(name, name)
        if (prefName == null) {
            call.respondJson(mapOf("settings" to emptyList<Map<String, Any?>>()))
            return@get
        }

        val schemas = PluginSettingsSchemaRegistry.getSettingsForPlugin(prefName)
        val settingsList = schemas.map { schema ->
            val fullKey = if (schema.isGlobal) schema.key else schema.pluginPrefName + schema.key
            val currentValue = if (schema.isGlobal) {
                null // global settings (shared_prefs) are not in DesktopDataStore
            } else {
                DesktopDataStore.getKey<Any>(fullKey) ?: schema.defaultValue
            }
            mapOf(
                "key" to schema.key,
                "type" to schema.type,
                "defaultValue" to (schema.defaultValue?.toString() ?: ""),
                "value" to (currentValue?.toString() ?: ""),
                "isGlobal" to schema.isGlobal,
            )
        }

        call.respondJson(mapOf("settings" to settingsList, "prefName" to prefName))
    }

    // POST /api/plugins/{name}/settings — Save a plugin setting
    post("/api/plugins/{name}/settings") {
        val name = call.parameters["name"] ?: ""
        if (name.isBlank()) {
            call.respondJson(mapOf("error" to "Plugin name is required"))
            return@post
        }

        val body = call.receive<String>()
        val json = try {
            org.json.JSONObject(body)
        } catch (e: Exception) {
            call.respondJson(mapOf("error" to "Invalid JSON"))
            return@post
        }

        val key = json.optString("key", "")
        val valueStr = json.optString("value", "")
        if (key.isBlank()) {
            call.respondJson(mapOf("error" to "Field 'key' is required"))
            return@post
        }

        val prefName = PluginSettingsSchemaRegistry.findPrefNameForPlugin(name, name)
        if (prefName == null) {
            call.respondJson(mapOf("error" to "Plugin not found in settings registry"))
            return@post
        }

        val fullKey = prefName + key
        DesktopDataStore.setKey(fullKey, valueStr)
        call.respondJson(mapOf("success" to true, "key" to fullKey, "value" to valueStr))
    }
}
