package com.lagradost.cloudstream3.desktop.iptv

data class IptvChannel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
)

object IptvParser {
    fun parseM3u(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                // Parse #EXTINF:-1 tvg-id="" tvg-name="" tvg-logo="" group-title="",Name
                val logo = Regex("""tvg-logo="([^"]*)"""").find(line)?.groupValues?.getOrNull(1)
                val group = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.getOrNull(1)
                val tvgId = Regex("""tvg-id="([^"]*)"""").find(line)?.groupValues?.getOrNull(1)
                val tvgName = Regex("""tvg-name="([^"]*)"""").find(line)?.groupValues?.getOrNull(1)

                // Name is after the last comma in #EXTINF line
                val name = line.substringAfterLast(",", "").trim().ifBlank {
                    // Fallback: extract from tvg-name
                    tvgName ?: "Unknown"
                }

                // Next line should be the URL
                i++
                if (i < lines.size) {
                    val url = lines[i].trim()
                    if (url.isNotBlank() && !url.startsWith("#")) {
                        channels.add(
                            IptvChannel(
                                name = name,
                                url = url,
                                logo = logo?.takeIf { it.isNotBlank() },
                                group = group?.takeIf { it.isNotBlank() },
                                tvgId = tvgId?.takeIf { it.isNotBlank() },
                                tvgName = tvgName?.takeIf { it.isNotBlank() },
                            )
                        )
                    }
                }
            }
            i++
        }
        return channels
    }
}
