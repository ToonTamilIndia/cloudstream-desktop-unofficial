@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class)

package com.lagradost.cloudstream3.desktop.network

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.lagradost.common.logging.AppLogger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

enum class DohProvider(val title: String) {
    NONE("Off (System Default)"),
    GOOGLE("Google"),
    CLOUDFLARE("Cloudflare"),
    ADGUARD("AdGuard"),
    QUAD9("Quad9"),
    DNSWATCH("DNSWatch"),
    DNSSB("DNS.SB"),
    CANADIAN_SHIELD("Canadian Shield"),
}

object NetworkConfig {
    const val PREF_DOH_PROVIDER = "doh_provider"

    /**
     * Returns the effective DoH provider.
     * When no preference has been saved we default to Cloudflare, so hosts that are
     * blocked or unresolvable via the local ISP DNS (e.g. net52.cc) can still be reached.
     * An explicit "Off" ("System Default") choice is honored.
     */
    fun getDohProvider(): DohProvider {
        val index = DesktopDataStore.getKey<Int>(PREF_DOH_PROVIDER)
            ?: return DohProvider.CLOUDFLARE
        return DohProvider.values().getOrNull(index) ?: DohProvider.CLOUDFLARE
    }

    /** Builds a standalone DoH resolver using a dedicated bootstrap client (no interceptor recursion). */
    private fun buildDohResolver(provider: DohProvider): okhttp3.Dns? {
        if (provider == DohProvider.NONE) return null
        val bootstrap = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val builder = DnsOverHttps.Builder().client(bootstrap)

        fun add(url: String, bootstrapIps: List<String>): okhttp3.Dns {
            val hosts = bootstrapIps.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
            return builder
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(hosts)
                .build()
        }

        return when (provider) {
            DohProvider.GOOGLE -> add("https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4"))
            DohProvider.CLOUDFLARE -> add("https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1"))
            DohProvider.ADGUARD -> add("https://dns.adguard.com/dns-query", listOf("94.140.14.140", "94.140.14.141"))
            DohProvider.QUAD9 -> add("https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112"))
            DohProvider.DNSWATCH -> add("https://resolver2.dns.watch/dns-query", listOf("84.200.69.80", "84.200.70.40"))
            DohProvider.DNSSB -> add("https://doh.dns.sb/dns-query", listOf("185.222.222.222", "45.11.45.11"))
            DohProvider.CANADIAN_SHIELD -> add("https://private.canadianshield.cira.ca/dns-query", listOf("149.112.121.10", "149.112.122.10"))
            DohProvider.NONE -> null
        }
    }

    private fun ipv4First(list: List<InetAddress>): List<InetAddress> =
        list.sortedBy { if (it is Inet4Address) 0 else 1 }

    /**
     * Rebuilds and assigns the global NiceHttp clients (`app.baseClient` and `insecureApp.baseClient`)
     * using the current DNS over HTTPS configuration.
     *
     * Resolution prefers the fast system DNS and transparently falls back to DoH when the host
     * cannot be resolved locally — this keeps streams reachable even when the ISP DNS blocks hosts.
     */
    fun updateGlobalNetworkClients() {
        val provider = getDohProvider()
        val dohResolver = buildDohResolver(provider)

        val baseBuilder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cookieJar(DesktopCookieJar())

        baseBuilder.dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val system = runCatching { okhttp3.Dns.SYSTEM.lookup(hostname) }.getOrElse { emptyList() }
                if (system.isNotEmpty()) return ipv4First(system)
                val doh = dohResolver
                if (doh != null) {
                    val viaDoh = runCatching { doh.lookup(hostname) }.getOrElse { emptyList() }
                    if (viaDoh.isNotEmpty()) return ipv4First(viaDoh)
                }
                return system
            }
        })

        // Apply CloudflareKiller interceptor
        baseBuilder.addInterceptor(CloudflareKiller())

        // Apply to main client
        app.baseClient = baseBuilder.build()
        // CRITICAL: Restore defaultHeaders that NiceHttp uses for ALL requests.
        // Without this, OkHttp sends 'okhttp/4.x' as User-Agent which Cloudflare blocks.
        app.defaultHeaders = mapOf("user-agent" to com.lagradost.cloudstream3.USER_AGENT)

        // Apply to insecure client
        val insecureBuilder = app.baseClient.newBuilder()
        try {
            insecureBuilder.ignoreAllSSLErrors()
        } catch (_: Exception) {}
        insecureApp.baseClient = insecureBuilder.build()
        insecureApp.defaultHeaders = mapOf("user-agent" to com.lagradost.cloudstream3.USER_AGENT)

        // Suppress noisy OkHttp connection pool leak warnings from third-party plugins
        java.util.logging.Logger.getLogger(OkHttpClient::class.java.name).level = java.util.logging.Level.SEVERE
        java.util.logging.Logger.getLogger(okhttp3.internal.platform.Platform::class.java.name).level = java.util.logging.Level.SEVERE

        AppLogger.i("Initialized global NiceHttp clients with DoH Provider: ${provider.title} (with system-DNS fallback)")
    }
}