package com.lagradost.server.network

import com.lagradost.common.logging.AppLogger
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import java.io.File

/**
 * Manages the shared headless browser used for solving Cloudflare challenges.
 *
 * Reuses the same browser instance across solves (Playwright + Chromium are pooled),
 * auto-detecting a system Chromium/Chrome/Edge via `which` first, then falling back to
 * a Playwright-bundled Chromium (PLAYWRIGHT_BROWSERS_PATH in the app data dir).
 */
object ServerBrowserManager {

    private val browserPath = File(
        com.lagradost.common.platform.PlatformPaths.appDataDir,
        "server_playwright_browsers",
    )

    @Volatile private var playwright: Playwright? = null
    @Volatile private var browser: com.microsoft.playwright.Browser? = null

    @Volatile private var detectedChannel: String? = null
    @Volatile private var detectedExecutable: String? = null
    @Volatile private var detectedType: String = "chromium"

    init {
        detectSystemBrowser()
    }

    private fun detectSystemBrowser() {
        data class Candidate(val channel: String?, val executable: String?, val type: String, val priority: Int)

        val osName = System.getProperty("os.name").lowercase()
        val candidates = mutableListOf<Candidate>()
        fun add(cmd: String, channel: String?, type: String, prio: Int) {
            val path = runCatching {
                Runtime.getRuntime().exec(arrayOf("which", cmd)).inputStream.bufferedReader().readText().trim()
            }.getOrDefault("")
            if (path.isNotEmpty() && File(path).exists()) candidates.add(Candidate(channel, path, type, prio))
        }

        val order = if (osName.contains("win")) {
            listOf("msedge", "chrome", null)
        } else {
            listOf("msedge", "chrome", null)
        }

        // Linux / mac via which.
        add("microsoft-edge-stable", "msedge", "chromium", 0)
        add("microsoft-edge", "msedge", "chromium", 0)
        add("google-chrome-stable", "chrome", "chromium", 1)
        add("google-chrome", "chrome", "chromium", 1)
        add("chromium-browser", null, "chromium", 2)
        add("chromium", null, "chromium", 2)
        add("brave-browser", null, "chromium", 3)

        if (osName.contains("win")) {
            val pf = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val pf86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            candidates += Candidate("msedge", "$pf86\\Microsoft\\Edge\\Application\\msedge.exe", "chromium", 0)
            candidates += Candidate("chrome", "$pf\\Google\\Chrome\\Application\\chrome.exe", "chromium", 1)
        }

        val best = candidates.minByOrNull { it.priority }
        if (best != null) {
            detectedChannel = best.channel
            detectedExecutable = best.executable
            detectedType = best.type
            AppLogger.i("ServerCloudflare: using ${best.executable} (channel=${best.channel})")
        } else {
            detectedChannel = null
            detectedExecutable = null
            detectedType = "chromium"
        }
    }

    private fun env(): Playwright.CreateOptions {
        val path = File(com.lagradost.common.platform.PlatformPaths.appDataDir, "server_playwright_browsers")
        val map = mutableMapOf("PLAYWRIGHT_BROWSERS_PATH" to path.absolutePath)
        if (detectedChannel != null || detectedExecutable != null) {
            map["PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD"] = "1"
        }
        return Playwright.CreateOptions().setEnv(map)
    }

    /** Returns the shared browser, launching it if needed. */
    fun getBrowser(): com.microsoft.playwright.Browser? {
        val existing = browser
        if (existing != null && existing.isConnected) return existing

        try {
            browser?.close()
            playwright?.close()
        } catch (_: Exception) {}

        return try {
            val pw = Playwright.create(env())
            playwright = pw
            val type = if (detectedExecutable != null) detectedType else "chromium"
            val launch = BrowserType.LaunchOptions()
                .setHeadless(true)
                .setIgnoreDefaultArgs(listOf("--enable-automation"))
                .setArgs(listOf("--disable-blink-features=AutomationControlled", "--disable-dev-shm-usage", "--disable-gpu"))

            if (detectedChannel != null) {
                launch.setChannel(detectedChannel!!)
            } else if (detectedExecutable != null) {
                launch.setExecutablePath(File(detectedExecutable!!).toPath())
            }

            val newBrowser = if (type == "firefox") pw.firefox().launch(launch) else pw.chromium().launch(launch)
            browser = newBrowser
            AppLogger.i("Server browser launched ($type, headless)")
            newBrowser
        } catch (e: Exception) {
            AppLogger.e("ServerBrowser: could not launch browser", e)
            reset()
            null
        }
    }

    fun reset() {
        try {
            browser?.close()
            playwright?.close()
        } catch (_: Exception) {}
        browser = null
        playwright = null
    }
}