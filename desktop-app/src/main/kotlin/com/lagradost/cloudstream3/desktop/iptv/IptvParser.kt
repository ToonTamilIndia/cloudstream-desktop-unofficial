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

        while (reader.readLine().also { line = it } != null) {
            val current = line!!.trim()
            if (current.startsWith("#EXTINF:")) {
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
            } else if (current.isNotBlank() && !current.startsWith("#") && extinfLine != null) {
                onChannel(
                    IptvChannel(
                        name = extinfName ?: "Unknown",
                        url = current,
                        logo = extinfLogo,
                        group = extinfGroup,
                        tvgId = extinfTvgId,
                        tvgName = extinfTvgName,
                    )
                )
                extinfLine = null
                extinfName = null
                extinfLogo = null
                extinfGroup = null
                extinfTvgId = null
                extinfTvgName = null
            }
        }
    }

    fun parseStreamToList(inputStream: InputStream): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        parseStream(inputStream) { channels.add(it) }
        return channels
    }
}
