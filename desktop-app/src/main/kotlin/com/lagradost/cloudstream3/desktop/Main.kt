@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class)

package com.lagradost.cloudstream3.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.init.initNetwork
import com.lagradost.cloudstream3.desktop.init.initPlugins
import com.lagradost.cloudstream3.desktop.init.initProviders
import com.lagradost.cloudstream3.desktop.init.initProxy
import com.lagradost.cloudstream3.desktop.init.initSecurity
import com.lagradost.cloudstream3.desktop.init.launchAutoUpdater
import com.lagradost.cloudstream3.desktop.ui.CloudstreamApp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Single unified entry point for CloudStream Desktop Client.
 *
 * Initialization order (matches Android startup semantics):
 *   1. Local stream proxy
 *   2. Security (exception handler, BouncyCastle, DataStore)
 *   3. Network (OkHttp clients, Jackson mapper, WebView, cookies)
 *   4. Built-in meta-providers (TMDB, Trakt, CrossTMDB)
 *   5. Load installed plugins (dex2jar conversion) + cloned sites
 *   6. Background auto-updater
 *   7. Compose UI window
 */
fun main() {
    AppLogger.i("Launching CloudStream Desktop Client...")
    AppLogger.i("Platform: ${PlatformPaths.currentOS}")
    AppLogger.i("App data directory: ${PlatformPaths.appDataDir.absolutePath}")

    initProxy()
    initSecurity()
    initNetwork()
    initProviders()
    initPlugins()
    launchAutoUpdater()
    com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        AppUpdater.checkForUpdates()
    }
    // Load Kodi config from saved settings
    com.lagradost.player.impl.KodiConfig.run {
        host = com.lagradost.common.storage.DesktopDataStore.getKey<String>("kodi_host") ?: host
        port = com.lagradost.common.storage.DesktopDataStore.getKey<String>("kodi_port")?.toIntOrNull() ?: port
        username = com.lagradost.common.storage.DesktopDataStore.getKey<String>("kodi_user") ?: username
        password = com.lagradost.common.storage.DesktopDataStore.getKey<String>("kodi_pass") ?: password
    }

    application {
        setSingletonImageLoaderFactory { context ->
            coil3.ImageLoader.Builder(context)
                .memoryCache {
                    coil3.memory.MemoryCache.Builder()
                        .maxSizePercent(context, 0.25)
                        .build()
                }
                .diskCache {
                    coil3.disk.DiskCache.Builder()
                        .directory(File(PlatformPaths.appDataDir, "image_cache").also { it.mkdirs() }.toOkioPath())
                        .maxSizeBytes(512L * 1024 * 1024) // 512MB
                        .build()
                }
                .components {
                    add(
                        coil3.network.okhttp.OkHttpNetworkFetcherFactory(
                            callFactory = { request ->
                                com.lagradost.cloudstream3.app.baseClient.newCall(request)
                            }
                        )
                    )
                }
                .crossfade(true)
                .build()
        }

        val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
        val windowWidth = (screenSize.width * 0.7).toInt().dp
        val windowHeight = (screenSize.height * 0.7).toInt().dp
        val state = androidx.compose.ui.window.rememberWindowState(
            width = windowWidth,
            height = windowHeight,
            position = androidx.compose.ui.window.WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "CloudStream - Unofficial Desktop Client (Pre-Alpha)",
            state = state,
            icon = androidx.compose.ui.res.painterResource("logo_ui.png"),
        ) {
            val awtWindow = this.window
            androidx.compose.runtime.CompositionLocalProvider(
                com.lagradost.cloudstream3.desktop.ui.LocalWindowState provides state,
                com.lagradost.cloudstream3.desktop.ui.LocalAwtWindow provides awtWindow,
            ) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    CloudstreamApp()

                    // App Update Dialog Overlay
                    val latestRelease by AppUpdater.latestRelease.collectAsState()
                    if (latestRelease != null) {
                        var showUpdateDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
                        if (showUpdateDialog) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showUpdateDialog = false },
                                title = { androidx.compose.material3.Text("Update Available: v${latestRelease!!.tag_name.removePrefix("v")}", style = androidx.compose.material3.MaterialTheme.typography.titleLarge) },
                                text = {
                                    androidx.compose.foundation.layout.Column {
                                        androidx.compose.material3.Text("A new version of CloudStream Desktop is available!", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                                        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                                        androidx.compose.material3.Text(latestRelease!!.body ?: "", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 10)
                                    }
                                },
                                confirmButton = {
                                    androidx.compose.material3.Button(onClick = {
                                        try {
                                            java.awt.Desktop.getDesktop().browse(java.net.URI(latestRelease!!.html_url))
                                        } catch (e: Exception) {}
                                        showUpdateDialog = false
                                    }) { androidx.compose.material3.Text("Download") }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { showUpdateDialog = false }) { androidx.compose.material3.Text("Later") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
