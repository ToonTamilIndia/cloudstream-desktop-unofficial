package com.lagradost.server.network

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Server-side Cloudflare bypass for the web UI.
 *
 * The desktop app installs a CloudflareKiller interceptor on `app.baseClient`, so its
 * media proxy transparently solves Cloudflare challenges (via headless Chromium) and
 * playback works. The server-app previously did not install this interceptor, so streams
 * played through the web UI came back as Cloudflare challenge pages (403/503) and failed.
 *
 * This interceptor is installed into `app.baseClient` (which the media proxy derives
 * from). It detects a Cloudflare challenge (403/503/429 + `Server: cloudflare` +
 * text/html), solves it per-host via headless Chromium (Playwright), and caches the
 * resulting `cf_clearance` cookie + User-Agent so later manifest/segment requests
 * are accepted.
 */
class CloudflareBypassInterceptor : Interceptor {

    companion object {
        const val TAG = "CloudflareBypass"

        private val ERROR_CODES = listOf(403, 503, 429)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")

        private val resolvedCookies = ConcurrentHashMap<String, String>()
        private val resolvingHosts = ConcurrentHashMap.newKeySet<String>()
        private val failedHosts = ConcurrentHashMap.newKeySet<String>()
        private val browserMutex = Mutex()

        fun clearCache() {
            resolvedCookies.clear()
            failedHosts.clear()
        }
    }

    private val netClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val host = request.url.host

        resolvedCookies[host]?.let { cookies ->
            return@runBlocking netClient.newCall(withCookies(request, cookies)).execute()
        }

        if (failedHosts.contains(host)) {
            return@runBlocking chain.proceed(request)
        }

        val response = chain.proceed(request)
        val serverHeader = response.header("Server") ?: ""
        val contentType = response.header("Content-Type") ?: response.header("content-type") ?: ""
        val isChallenge = response.code in ERROR_CODES &&
            CLOUDFLARE_SERVERS.any { serverHeader.contains(it, ignoreCase = true) } &&
            contentType.contains("text/html", ignoreCase = true)

        if (!isChallenge) {
            return@runBlocking response
        }
        response.close()

        if (!resolvingHosts.add(host)) {
            return@runBlocking chain.proceed(request)
        }
        try {
            val cookie = solve(host, request)
            if (cookie != null) {
                resolvedCookies[host] = cookie
                return@runBlocking netClient.newCall(withCookies(request, cookie)).execute()
            }
        } finally {
            resolvingHosts.remove(host)
        }

        failedHosts.add(host)
        AppLogger.w("$TAG: Could not solve Cloudflare for $host")
        chain.proceed(request)
    }

    /** Returns a merged cookie header after solving, or null if unsolvable. */
    private suspend fun solve(host: String, original: Request): String? = browserMutex.withLock {
        AppLogger.i("$TAG: Launching headless Chromium to solve Cloudflare for $host")
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            val browser = ServerBrowserManager.getBrowser() ?: return@withContext null
            try {
                val context = browser.newContext(
                    com.microsoft.playwright.Browser.NewContextOptions()
                        .setUserAgent(com.lagradost.cloudstream3.USER_AGENT)
                        .setViewportSize(1920, 1080)
                        .setBypassCSP(true)
                        .setIgnoreHTTPSErrors(true),
                )
                context.addInitScript(
                    """
                    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                    try { for (let prop in window) { if (prop.startsWith('cdc_')) delete window[prop]; } } catch(e) {}
                    """.trimIndent(),
                )
                val page = context.newPage()
                page.setDefaultNavigationTimeout(20000.0)
                page.route("**/*") { route ->
                    when (route.request().resourceType()) {
                        "image", "media", "font", "stylesheet" -> route.abort()
                        else -> route.resume()
                    }
                }

                try {
                    page.navigate(original.url.toString(), com.microsoft.playwright.Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED))
                } catch (_: com.microsoft.playwright.TimeoutError) {}

                var cookie: String? = null
                for (i in 1..40) {
                    delay(500)
                    try {
                        val cookies = context.cookies()
                        if (cookies.any { it.name == "cf_clearance" }) {
                            cookie = cookies.joinToString("; ") { "${it.name}=${it.value}" }
                            break
                        }
                    } catch (_: Exception) {
                        break
                    }
                }

                try { context.close() } catch (_: Exception) {}
                if (cookie != null) AppLogger.i("$TAG: Solved Cloudflare for $host")
                else AppLogger.w("$TAG: No cf_clearance obtained for $host")
                cookie
            } catch (e: Exception) {
                AppLogger.e("$TAG: Playwright resolution failed for $host", e)
                ServerBrowserManager.reset()
                null
            }
        }
    }

    private fun withCookies(request: Request, cookieHeader: String): Request =
        request.newBuilder()
            .apply {
                val existing = request.header("cookie")?.let { parseCookieMap(it) } ?: emptyMap()
                val merged = parseCookieMap(cookieHeader) + existing
                if (merged.isNotEmpty()) {
                    header("cookie", merged.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            .build()

    private fun parseCookieMap(cookie: String): Map<String, String> =
        cookie.split(";")
            .mapNotNull { pair ->
                val split = pair.split("=", limit = 2)
                val k = split.getOrNull(0)?.trim().orEmpty()
                val v = split.getOrNull(1)?.trim().orEmpty()
                if (k.isNotEmpty() && v.isNotEmpty()) k to v else null
            }
            .toMap()
}