package com.lagradost.cloudstream3.desktop.iptv

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class IptvChannel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val manifestType: String? = null,
    val drmType: String? = null,
    val licenseKey: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

object IptvParser {
    private val extinfRegex = Regex("""#EXTINF:.*?tvg-logo="([^"]*)".*?group-title="([^"]*)".*?tvg-id="([^"]*)".*?tvg-name="([^"]*)".*?,(.*)""")
    private val extinfFallbackRegex = Regex("""#EXTINF:.*?,(.*)""")

    fun parseStream(inputStream: InputStream, onChannel: (IptvChannel) -> Unit) {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var line: String?
        var extinfLine: String? = null
        var extinfName: String? = null
        var extinfLogo: String? = null
        var extinfGroup: String? = null
        var extinfTvgId: String? = null
        var extinfTvgName: String? = null
        var manifestType: String? = null
        var drmType: String? = null
        var licenseKey: String? = null
        val headers = linkedMapOf<String, String>()

        while (reader.readLine().also { line = it } != null) {
            val current = line!!.trim()
            when {
            current.startsWith("#KODIPROP:inputstream.adaptive.manifest_type=", ignoreCase = true) -> {
                manifestType = current.substringAfter('=').trim()
            }
            current.startsWith("#KODIPROP:inputstream.adaptive.license_type=", ignoreCase = true) -> {
                drmType = current.substringAfter('=').trim()
            }
            current.startsWith("#KODIPROP:inputstream.adaptive.license_key=", ignoreCase = true) -> {
                licenseKey = current.substringAfter('=').trim()
            }
            current.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) -> {
                headers["User-Agent"] = current.substringAfter('=').trim()
            }
            current.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) ||
                current.startsWith("#EXTVLCOPT:http-referer=", ignoreCase = true) -> {
                headers["Referer"] = current.substringAfter('=').trim()
            }
            current.startsWith("#EXTVLCOPT:http-origin=", ignoreCase = true) -> {
                headers["Origin"] = current.substringAfter('=').trim()
            }
            current.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                parseExtHttpHeaders(current.substringAfter(':')).forEach { (key, value) -> headers[key] = value }
            }
            current.startsWith("#EXTINF:") -> {
                val match = extinfRegex.find(current)
                if (match != null) {
                    extinfLogo = match.groupValues[1].takeIf { it.isNotBlank() }
                    extinfGroup = match.groupValues[2].takeIf { it.isNotBlank() }
                    extinfTvgId = match.groupValues[3].takeIf { it.isNotBlank() }
                    extinfTvgName = match.groupValues[4].takeIf { it.isNotBlank() }
                    extinfName = match.groupValues[5].trim().takeIf { it.isNotBlank() }
                        ?: extinfTvgName
                } else {
                    val fallback = extinfFallbackRegex.find(current)
                    extinfName = fallback?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                    extinfLogo = null; extinfGroup = null; extinfTvgId = null; extinfTvgName = null
                }
                extinfLine = current
            }
            current.isNotBlank() && !current.startsWith("#") && extinfLine != null -> {
                onChannel(
                    IptvChannel(
                        name = extinfName ?: "Unknown",
                        url = current,
                        logo = extinfLogo,
                        group = extinfGroup,
                        tvgId = extinfTvgId,
                        tvgName = extinfTvgName,
                        manifestType = manifestType,
                        drmType = drmType,
                        licenseKey = licenseKey,
                        headers = headers.toMap(),
                    )
                )
                extinfLine = null
                extinfName = null
                extinfLogo = null
                extinfGroup = null
                extinfTvgId = null
                extinfTvgName = null
                manifestType = null
                drmType = null
                licenseKey = null
                headers.clear()
            }
            }
        }
    }

    private fun parseExtHttpHeaders(json: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        Regex(""""([^"\\]+)"\s*:\s*"((?:\\.|[^"\\])*)"""").findAll(json).forEach { match ->
            val rawKey = match.groupValues[1]
            val key = when {
                rawKey.equals("cookie", true) -> "Cookie"
                rawKey.equals("user-agent", true) -> "User-Agent"
                rawKey.equals("referer", true) || rawKey.equals("referrer", true) -> "Referer"
                rawKey.equals("origin", true) -> "Origin"
                else -> rawKey
            }
            result[key] = match.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return result
    }

    fun parseStreamToList(inputStream: InputStream): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        parseStream(inputStream) { channels.add(it) }
        return channels
    }
}
