package com.lagradost.server

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.player.impl.proxy.LocalStreamProxy
import com.lagradost.runtime.loader.ExtensionLoader
import com.lagradost.server.routes.*
import com.lagradost.server.utils.respondJson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    val isServerOnly = args.contains("--server")
    val port = args.indexOf("--port").let { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1].toIntOrNull() ?: 8080
        else 8080
    }

    AppLogger.i("Starting CloudStream Desktop Server...")
    AppLogger.i("Platform: ${PlatformPaths.currentOS}")
    AppLogger.i("App data directory: ${PlatformPaths.appDataDir.absolutePath}")
    AppLogger.i("Server port: $port")

    initServerServices()

    val server = embeddedServer(Netty, port = port) {
        install(CORS) {
            allowHost("localhost:$port")
            allowHost("localhost:5173")
            allowHost("127.0.0.1:$port")
            allowHost("127.0.0.1:5173")
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.Origin)
            allowCredentials = true
        }
        routing {
            get("/api/health") {
                call.respondJson(mapOf(
                    "status" to "ok",
                    "version" to "0.1.0",
                    "sources" to APIHolder.allProviders.size
                ))
            }

            registerSourcesRoutes()
            registerSearchRoutes()
            registerMainPageRoutes()
            registerDetailsRoutes()
            registerEpisodesRoutes()
            registerLinksRoutes()
            registerLibraryRoutes()
            registerPluginsRoutes()
            registerSettingsRoutes()
            registerIptvRoutes()

            get("/") {
                call.respondRedirect("/index.html")
            }
        }
    }

    AppLogger.i("Server started on port $port")
    server.start(wait = !isServerOnly)
}

private fun initServerServices() {
    AppLogger.i("Initializing server services...")

    try {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("Unhandled exception in ${thread.name}", throwable)
        }
        java.security.Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), java.security.Security.getProviders().size + 1)
        DesktopDataStore.init()
    } catch (e: Exception) {
        AppLogger.e("Security init failed", e)
    }

    try {
        LocalStreamProxy.start()
    } catch (e: Exception) {
        AppLogger.e("Proxy init failed", e)
    }

    try {
        val builtIns = listOf(
            com.lagradost.cloudstream3.metaproviders.TmdbProvider().apply {
                name = "TMDB"
            },
            com.lagradost.cloudstream3.metaproviders.TraktProvider(),
            com.lagradost.cloudstream3.metaproviders.CrossTmdbProvider(),
        )
        synchronized(APIHolder.allProviders) {
            builtIns.forEach { provider ->
                provider.sourcePlugin = "built-in"
                APIHolder.allProviders.add(provider)
                APIHolder.addPluginMapping(provider)
            }
        }
    } catch (e: Exception) {
        AppLogger.e("Provider init failed", e)
    }

    try {
        loadPlugins()
    } catch (e: Exception) {
        AppLogger.e("Plugin loading failed", e)
    }

    AppLogger.i("Server services initialized")
}

/**
 * Load installed extension JARs from the shared Extensions directory.
 * Mirrors the desktop-app's PluginInit.loadInstalledPlugins() logic,
 * so the server-app sees the same providers as the desktop client.
 */
private fun loadPlugins() {
    val extensionsDir = PlatformPaths.extensionsDir
    if (!extensionsDir.exists()) {
        AppLogger.i("No extensions directory found at ${extensionsDir.absolutePath}")
        return
    }

    val jarFiles = extensionsDir.walkTopDown()
        .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
        .filter { !it.name.endsWith("-jvm.jar") }
        .toList()

    if (jarFiles.isEmpty()) {
        AppLogger.i("No plugins found in ${extensionsDir.absolutePath}")
        return
    }

    var loaded = 0
    var failed = 0
    var skipped = 0
    val disabledPlugins = DesktopDataStore.getKey<Set<String>>("disabled_plugins") ?: emptySet()
    for (jarFile in jarFiles) {
        val pluginName = jarFile.nameWithoutExtension
        if (disabledPlugins.contains(pluginName)) {
            skipped++
            AppLogger.i("Skipping disabled plugin: $pluginName")
            continue
        }
        try {
            ExtensionLoader.loadAndInit(jarFile)
            loaded++
        } catch (e: Throwable) {
            failed++
            AppLogger.e("Failed to load plugin ${jarFile.name}: ${e.message}")
        }
    }
    AppLogger.i("Plugin loading complete: $loaded loaded, $failed failed, $skipped skipped (of ${jarFiles.size} total)")
}