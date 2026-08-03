package com.lagradost.common.platform

import java.io.File

/**
 * Cross-platform path resolution for the CloudStream Desktop client.
 *
 * Replaces all direct `System.getenv("APPDATA")` calls with proper
 * OS-aware paths that work on Windows, macOS, and Linux.
 *
 * Directory layout per OS:
 *   Windows: %APPDATA%/CloudStreamDesktop/
 *   macOS:   ~/Library/Application Support/CloudStreamDesktop/
 *   Linux:   ~/.local/share/CloudStreamDesktop/
 */
object PlatformPaths {
    enum class OS { WINDOWS, MACOS, LINUX, UNKNOWN }

    val currentOS: OS by lazy {
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("win") -> OS.WINDOWS
            osName.contains("mac") -> OS.MACOS
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> OS.LINUX
            else -> OS.UNKNOWN
        }
    }

    /** The base application data directory, OS-aware. */
    val appDataDir: File by lazy {
        val basePath =
            when (currentOS) {
                OS.WINDOWS -> {
                    val appData = System.getenv("APPDATA")
                    if (!appData.isNullOrEmpty()) appData else System.getProperty("user.home")
                }
                OS.MACOS -> System.getProperty("user.home") + "/Library/Application Support"
                OS.LINUX -> System.getProperty("user.home") + "/.local/share"
                OS.UNKNOWN -> System.getProperty("user.home")
            }
        File(basePath, "CloudStreamDesktop").also { it.mkdirs() }
    }

    /** Directory for persistent data store (bookmarks, history, preferences). */
    val dataDir: File by lazy {
        File(appDataDir, "data").also { it.mkdirs() }
    }

    /** Directory for SharedPreferences JSON files. */
    val sharedPrefsDir: File by lazy {
        File(appDataDir, "shared_prefs").also { it.mkdirs() }
    }

    /** Directory for installed extensions/plugins. */
    val extensionsDir: File by lazy {
        File(appDataDir, "Extensions").also { it.mkdirs() }
    }

    /** Directory for cache files. */
    val cacheDir: File by lazy {
        File(appDataDir, "cache").also { it.mkdirs() }
    }

    /** Directory for log files. */
    val logsDir: File by lazy {
        File(appDataDir, "logs").also { it.mkdirs() }
    }

    /**
     * Directory for downloaded media (movies/episodes saved by the downloader).
     * Cross-platform: Windows -> %USERPROFILE%\Downloads\CloudStream
     *                macOS   -> ~/Downloads/CloudStream
     *                Linux   -> ~/Downloads/CloudStream
     * Falls back to the app data dir if the user's Downloads folder is unavailable.
     */
    val downloadsDir: File by lazy {
        val base = System.getProperty("user.home")
        val guesses = listOf(
            File(base, "Downloads" + File.separator + "CloudStream"),
            File(System.getenv("USERPROFILE"), "Downloads" + File.separator + "CloudStream"),
            File(base, "CloudStream" + File.separator + "Downloads"),
        )
        val chosen = guesses.firstOrNull { it.exists() || it.parentFile.exists() || it.parentFile.parentFile.exists() }
            ?: File(appDataDir, "downloads")
        chosen.also {
            try {
                it.mkdirs()
            } catch (e: Exception) {
                // fall back to appDataDir if mkdir fails (read-only home, etc.)
                File(appDataDir, "downloads").also { f -> f.mkdirs() }.also { _ -> }
            }
        }
    }
}
