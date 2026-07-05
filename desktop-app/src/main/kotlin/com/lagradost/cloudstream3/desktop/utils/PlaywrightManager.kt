package com.lagradost.cloudstream3.desktop.utils

import com.lagradost.common.platform.PlatformPaths
import com.microsoft.playwright.Playwright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

object PlaywrightManager {

    private val _isInstalled = MutableStateFlow(false)
    val isInstalled = _isInstalled.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _showPrompt = MutableStateFlow(false)
    val showPrompt = _showPrompt.asStateFlow()

    fun triggerPrompt() {
        _showPrompt.value = true
    }

    fun dismissPrompt() {
        _showPrompt.value = false
    }

    private val playwrightPath = File(PlatformPaths.appDataDir, "playwright_browsers")

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded = _isDownloaded.asStateFlow()

    private val _systemBrowserType = MutableStateFlow("chromium")
    val systemBrowserType = _systemBrowserType.asStateFlow()

    private val _systemBrowserChannel = MutableStateFlow<String?>(null)
    val systemBrowserChannel = _systemBrowserChannel.asStateFlow()

    private val _systemBrowserExecutable = MutableStateFlow<String?>(null)
    val systemBrowserExecutable = _systemBrowserExecutable.asStateFlow()

    init {
        checkInstalled()
    }

    private fun checkInstalled() {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val isMac = osName.contains("mac")

        data class DetectedBrowser(
            val channel: String?,   // non-null for branded Chrome/Edge
            val executable: String, // path to the binary
            val browserType: String, // "chromium" or "firefox"
        )

        val browsers = mutableListOf<DetectedBrowser>()

        fun addIfExists(cmd: String, channel: String?, browserType: String) {
            val path = try {
                Runtime.getRuntime().exec(arrayOf("which", cmd)).inputStream.bufferedReader().readText().trim()
            } catch (_: Exception) { "" }
            if (path.isNotEmpty() && File(path).exists()) {
                browsers.add(DetectedBrowser(channel, path, browserType))
            }
        }

        fun addHardcoded(path: String, channel: String?, browserType: String) {
            val f = File(path)
            if (f.exists()) {
                browsers.add(DetectedBrowser(channel, f.absolutePath, browserType))
            }
        }

        if (isWindows) {
            val progFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val progFiles86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            val localAppData = System.getenv("LOCALAPPDATA") ?: "C:\\Users\\Default\\AppData\\Local"

            addHardcoded("$progFiles86\\Microsoft\\Edge\\Application\\msedge.exe", "msedge", "chromium")
            addHardcoded("$progFiles\\Microsoft\\Edge\\Application\\msedge.exe", "msedge", "chromium")
            addHardcoded("$progFiles\\Google\\Chrome\\Application\\chrome.exe", "chrome", "chromium")
            addHardcoded("$progFiles86\\Google\\Chrome\\Application\\chrome.exe", "chrome", "chromium")
            addHardcoded("$localAppData\\Google\\Chrome\\Application\\chrome.exe", "chrome", "chromium")
        } else if (isMac) {
            addHardcoded("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge", "msedge", "chromium")
            addHardcoded("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "chrome", "chromium")
            addIfExists("firefox", null, "firefox")
            addIfExists("firefox-developer-edition", null, "firefox")
            addIfExists("firefox-nightly", null, "firefox")
            addIfExists("brave-browser", null, "chromium")
        } else {
            // Linux — which-based detection
            val whichCommands = listOf(
                Triple("microsoft-edge-stable", "msedge", "chromium"),
                Triple("microsoft-edge", "msedge", "chromium"),
                Triple("google-chrome-stable", "chrome", "chromium"),
                Triple("google-chrome", "chrome", "chromium"),
                Triple("chromium-browser", null, "chromium"),
                Triple("chromium", null, "chromium"),
                Triple("brave-browser", null, "chromium"),
                Triple("brave", null, "chromium"),
                Triple("firefox", null, "firefox"),
                Triple("firefox-developer-edition", null, "firefox"),
                Triple("firefox-nightly", null, "firefox"),
            )
            for ((cmd, channel, browserType) in whichCommands) {
                addIfExists(cmd, channel, browserType)
            }

            // Hardcoded paths
            addHardcoded("/usr/bin/microsoft-edge-stable", "msedge", "chromium")
            addHardcoded("/usr/bin/microsoft-edge", "msedge", "chromium")
            addHardcoded("/usr/bin/google-chrome-stable", "chrome", "chromium")
            addHardcoded("/usr/bin/google-chrome", "chrome", "chromium")
            addHardcoded("/usr/bin/chromium-browser", null, "chromium")
            addHardcoded("/usr/bin/chromium", null, "chromium")
            addHardcoded("/usr/bin/brave-browser", null, "chromium")
            addHardcoded("/usr/bin/firefox", null, "firefox")
            addHardcoded("/snap/bin/chromium", null, "chromium")
            addHardcoded("/snap/bin/google-chrome-stable", "chrome", "chromium")
            addHardcoded("/snap/bin/firefox", null, "firefox")
            addHardcoded("/var/lib/flatpak/exports/bin/com.google.Chrome", "chrome", "chromium")
            addHardcoded("/var/lib/flatpak/exports/bin/com.microsoft.Edge", "msedge", "chromium")
            addHardcoded("/var/lib/flatpak/exports/bin/org.mozilla.firefox", null, "firefox")
            addHardcoded("/var/lib/flatpak/exports/bin/com.brave.Browser", null, "chromium")
        }

        // Priority: Edge > Chrome > Chromium > Brave > Firefox
        val order = listOf("msedge", "chrome", null)
        val selected = browsers.minByOrNull {
            val channel = it.channel
            val idx = order.indexOf(channel)
            if (idx >= 0) idx else order.size
        }

        if (selected != null) {
            _systemBrowserChannel.value = selected.channel
            _systemBrowserExecutable.value = selected.executable
            _systemBrowserType.value = selected.browserType
        } else {
            _systemBrowserChannel.value = null
            _systemBrowserExecutable.value = null
            _systemBrowserType.value = "chromium"
        }

        _isDownloaded.value = playwrightPath.exists() && (playwrightPath.listFiles()?.isNotEmpty() == true)
        _isInstalled.value = _systemBrowserChannel.value != null || _systemBrowserExecutable.value != null || _isDownloaded.value
    }

    suspend fun downloadBrowser() = withContext(Dispatchers.IO) {
        if (_isInstalled.value) return@withContext

        _isDownloading.value = true
        try {
            val env = mapOf("PLAYWRIGHT_BROWSERS_PATH" to playwrightPath.absolutePath)
            val options = Playwright.CreateOptions().setEnv(env)

            val playwright = Playwright.create(options)
            playwright.close()

            checkInstalled()
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("Playwright download or initialization failed", e)
        } finally {
            _isDownloading.value = false
        }
    }

    fun removeBrowser() {
        if (playwrightPath.exists()) {
            playwrightPath.deleteRecursively()
            checkInstalled()
        }
    }

    fun getEnvOptions(): Playwright.CreateOptions {
        val env = mutableMapOf("PLAYWRIGHT_BROWSERS_PATH" to playwrightPath.absolutePath)

        if (_systemBrowserChannel.value != null || _systemBrowserExecutable.value != null) {
            env["PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD"] = "1"
        }

        return Playwright.CreateOptions().setEnv(env)
    }

    var playwright: Playwright? = null
    var browser: com.microsoft.playwright.Browser? = null

    suspend fun getBrowser(): com.microsoft.playwright.Browser = withContext(Dispatchers.IO) {
        val b = browser
        if (b == null || !b.isConnected) {
            try {
                browser?.close()
            } catch (_: Exception) {}
            try {
                playwright?.close()
            } catch (_: Exception) {}

            playwright = Playwright.create(getEnvOptions())

            val channel = _systemBrowserChannel.value
            val executable = _systemBrowserExecutable.value
            val type = _systemBrowserType.value

            val launchOptions = com.microsoft.playwright.BrowserType.LaunchOptions()
                .setHeadless(true)

            if (type == "chromium") {
                launchOptions
                    .setIgnoreDefaultArgs(listOf("--enable-automation"))
                    .setArgs(
                        listOf(
                            "--disable-blink-features=AutomationControlled",
                            "--disable-dev-shm-usage",
                            "--disable-gpu",
                        ),
                    )
                if (channel != null) {
                    launchOptions.setChannel(channel)
                } else {
                    executable?.let { launchOptions.setExecutablePath(File(it).toPath()) }
                }
                browser = playwright!!.chromium().launch(launchOptions)
            } else {
                executable?.let { launchOptions.setExecutablePath(File(it).toPath()) }
                browser = playwright!!.firefox().launch(launchOptions)
            }
        }
        return@withContext browser!!
    }

    fun resetBrowser() {
        try {
            browser?.close()
        } catch (_: Exception) {}
        try {
            playwright?.close()
        } catch (_: Exception) {}
        browser = null
        playwright = null
    }
}
