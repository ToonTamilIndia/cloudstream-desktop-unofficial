package com.lagradost.server.routes

import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.player.impl.proxy.LocalStreamProxy

fun defaultSessionHeaders(referer: String): MutableMap<String, String> {
    val headers = mutableMapOf<String, String>()
    if (referer.isNotBlank()) {
        headers["Referer"] = referer
        if (referer.startsWith("http://") || referer.startsWith("https://")) {
            try {
                val uri = java.net.URI(referer)
                headers["Origin"] = "${uri.scheme}://${uri.host}"
            } catch (_: Exception) {}
        }
    }
    headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
    headers["Accept-Language"] = "en-US,en;q=0.9"
    headers["Sec-Fetch-Dest"] = "document"
    headers["Sec-Fetch-Mode"] = "navigate"
    headers["Sec-Fetch-Site"] = "none"
    headers["Sec-Fetch-User"] = "?1"
    headers["DNT"] = "1"
    headers["Connection"] = "keep-alive"
    return headers
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
fun toLinkResult(link: ExtractorLink): LinkResult {
    val drm = link as? DrmExtractorLink
    return LinkResult(
        name = link.name,
        url = link.url,
        quality = link.quality,
        type = link.type.name,
        headers = link.getAllHeaders(),
        isM3u8 = link.isM3u8,
        isDash = link.isDash,
        drmKid = drm?.kid,
        drmKey = drm?.key,
        drmUuid = drm?.uuid?.toString(),
        drmLicenseUrl = drm?.licenseUrl,
        audioTracks = link.audioTracks.takeIf { it.isNotEmpty() }?.map { track ->
            AudioTrackResult(url = track.url, headers = track.headers)
        },
        source = link.source,
        extractorData = link.extractorData,
    )
}

fun toSubtitleResult(sub: com.lagradost.cloudstream3.SubtitleFile): SubtitleResult {
    return SubtitleResult(lang = sub.lang, url = sub.url, headers = sub.headers)
}

fun mergeHeaders(perLinkHeaders: List<Map<String, String>>, defaults: MutableMap<String, String>) {
    val mergedHeaders = linkedMapOf<String, String>()
    perLinkHeaders.forEach { headers ->
        headers.forEach { (key, value) -> mergedHeaders[key] = value }
    }
    defaults.forEach { (key, value) ->
        if (mergedHeaders.keys.none { it.equals(key, ignoreCase = true) }) {
            mergedHeaders[key] = value
        }
    }
    val deduped = linkedMapOf<String, String>()
    mergedHeaders.forEach { (key, value) ->
        val existing = deduped.keys.firstOrNull { it.equals(key, ignoreCase = true) }
        if (existing != null) deduped.remove(existing)
        deduped[key] = value
    }
    defaults.clear()
    defaults.putAll(deduped)
}

fun createProxyForLinks(links: List<LinkResult>, subtitles: MutableList<SubtitleResult>, sessionHeaders: MutableMap<String, String>): Pair<String?, String?> {
    if (links.isEmpty()) return null to null
    val sessionId = LocalStreamProxy.registerSession(sessionHeaders)
    val proxyUrl = "http://localhost:${LocalStreamProxy.port}"
    subtitles.replaceAll { sub -> sub.copy(proxyUrl = LocalStreamProxy.buildProxyUrl(sessionId, sub.url)) }
    return sessionId to proxyUrl
}

fun assignProxyUrls(links: List<LinkResult>, sessionId: String?): List<LinkResult> {
    if (sessionId == null) return links
    return links.map { link ->
        val shouldProxy = link.isM3u8 || link.isDash || link.url.contains(".mp4")
        link.copy(proxyUrl = if (shouldProxy) LocalStreamProxy.buildProxyUrl(sessionId, link.url) else null)
    }
}
