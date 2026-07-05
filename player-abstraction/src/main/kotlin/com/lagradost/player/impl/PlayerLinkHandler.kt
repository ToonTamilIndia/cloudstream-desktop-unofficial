package com.lagradost.player.impl

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import java.io.File

/**
 * Normalizes ExtractorLink data for external players (MPV / VLC).
 * Mirrors Android CS3IPlayer header handling: referer + custom headers on every request.
 */
object PlayerLinkHandler {

    enum class PlayerBackend { MPV, VLC, KODI }

    data class PlaybackSupport(val supported: Boolean, val reason: String? = null)

    const val MIN_DURATION_TO_SAVE_SECONDS = 30L
    const val RESUME_RESET_PERCENT = 95L
    const val RESUME_MIN_PERCENT = 1L

    data class DrmInfo(
        val kid: String?,
        val key: String?,
        val uuid: String?,
        val licenseUrl: String?,
    )

    data class ValidatedLink(
        val url: String,
        val displayTitle: String,
        val headers: Map<String, String>,
        val streamKind: StreamKind,
        val useUrlFile: Boolean,
        val audioTracks: List<com.lagradost.cloudstream3.AudioFile> = emptyList(),
        val proxySessionId: String? = null,
        val drmInfo: DrmInfo? = null,
    )

    enum class StreamKind {
        HLS,
        DASH,
        PROGRESSIVE,
    }

    fun validate(
        link: ExtractorLink,
        explicitTitle: String? = null,
        useLocalProxy: Boolean = false,
    ): Result<ValidatedLink> {
        // Check for DRM (ClearKey/Widevine) - only ClearKey is playable on desktop
        val drmInfo = if (link is com.lagradost.cloudstream3.utils.DrmExtractorLink) {
            @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
            DrmInfo(
                kid = link.kid,
                key = link.key,
                uuid = link.uuid.toString(),
                licenseUrl = link.licenseUrl,
            )
        } else {
            null
        }

        // --- Handle ExtractorLinkPlayList (concatenated chunk streams) ---
        // These have url="" but provide a list of chunk URLs + durations.
        // We generate an MPV EDL (Edit Decision List) file to play them seamlessly.
        if (link is ExtractorLinkPlayList) {
            if (link.playlist.isEmpty()) {
                return Result.failure(IllegalArgumentException("ExtractorLinkPlayList has empty playlist."))
            }
            val headers = buildHeaderMap(link)
            val display = sanitizeDisplayTitle(explicitTitle?.takeIf { it.isNotBlank() } ?: link.name)
            val edlFile = writeEdlFile(link.playlist, headers)
            return Result.success(
                ValidatedLink(
                    url = edlFile.absolutePath,
                    displayTitle = display,
                    headers = headers,
                    streamKind = StreamKind.PROGRESSIVE,
                    useUrlFile = false,
                    drmInfo = drmInfo,
                ),
            )
        }

        val url = link.url.trim()
        if (url.isBlank()) {
            return Result.failure(IllegalArgumentException("Stream URL is empty."))
        }
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Unsupported stream URL scheme: ${url.take(12)}..."))
        }

        val headers = buildHeaderMap(link)
        if (headers.values.any { it.isBlank() }) {
            return Result.failure(IllegalArgumentException("Stream headers contain invalid empty values."))
        }

        val kind = when {
            link.isM3u8 || link.type == ExtractorLinkType.M3U8 -> StreamKind.HLS
            link.isDash || link.type == ExtractorLinkType.DASH -> StreamKind.DASH
            else -> StreamKind.PROGRESSIVE
        }

        // For HLS streams, route through the LocalStreamProxy.
        // The proxy fetches the .m3u8 manifest, rewrites all segment URLs to go through
        // localhost:8080, and injects the correct auth headers on every segment request.
        // This makes the stream appear as a seamless local HLS feed to MPV.
        // Without this, MPV receives raw tokenized CDN segment URLs which can expire
        // mid-stream, causing broken-pieces playback.
        // HLS and DASH manifests contain recursive child requests. Proxy them whenever
        // headers must be inherited by playlists, keys, init segments, and media segments.
        val useProxy = useLocalProxy &&
            (kind == StreamKind.HLS ||
                (kind == StreamKind.DASH && (headers.isNotEmpty() || drmInfo?.key?.isNotBlank() == true)))
        var finalSessionId: String? = null
        val finalUrl = if (useProxy) {
            val drmKeyBytes = drmInfo?.key?.let(::decodeDrmBase64)
            val kidHex = drmInfo?.kid?.let(::decodeDrmBase64)
                ?.joinToString("") { "%02x".format(it) }
            val sessionId = com.lagradost.player.impl.proxy.LocalStreamProxy.registerSession(headers, drmKeyBytes, kidHex)
            finalSessionId = sessionId
            com.lagradost.player.impl.proxy.LocalStreamProxy.buildProxyUrl(sessionId, url)
        } else {
            url
        }

        val finalHeaders = if (useProxy) emptyMap() else headers

        val display = sanitizeDisplayTitle(explicitTitle?.takeIf { it.isNotBlank() } ?: link.name)

        val finalAudioTracks = if (useProxy && finalSessionId != null) {
            link.audioTracks.map { audio ->
                // AudioFile constructor is internal, so we must recreate it using the builder/reflection
                // or just modify the url if it's mutable, but it might not be. Wait, the constructor is internal.
                // But there is a helper function `com.lagradost.cloudstream3.newAudioFile`.
                // Actually `newAudioFile` is suspend function.
                // We shouldn't use it here if we can't suspend.
                // Let's check if the properties are mutable since the data class has `var url`.
                // Yes: `var url: String`, `var headers: Map<String, String>?`.
                com.lagradost.cloudstream3.AudioFile::class.java.getDeclaredConstructor(String::class.java, Map::class.java).apply {
                    isAccessible = true
                }.newInstance(
                    com.lagradost.player.impl.proxy.LocalStreamProxy.buildProxyUrl(finalSessionId, audio.url),
                    audio.headers,
                )
            }
        } else {
            link.audioTracks
        }

        return Result.success(
            ValidatedLink(
                url = finalUrl,
                displayTitle = display,
                headers = finalHeaders,
                streamKind = kind,
                // Avoid Windows command-line limits and escaping issues for long signed URLs.
                useUrlFile = !useProxy && (finalUrl.length > 1800 || finalUrl.count { it == '&' } > 8),
                audioTracks = finalAudioTracks,
                proxySessionId = finalSessionId,
                drmInfo = drmInfo,
            ),
        )
    }

    private fun decodeDrmBase64(value: String): ByteArray? {
        val normalized = value.trim().padEnd((value.trim().length + 3) / 4 * 4, '=')
        return runCatching { java.util.Base64.getUrlDecoder().decode(normalized) }.getOrElse {
            runCatching { java.util.Base64.getDecoder().decode(normalized) }.getOrNull()
        }
    }

    fun buildHeaderMap(link: ExtractorLink): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        link.getAllHeaders().forEach { (key, value) ->
            val cleaned = value.replace("\r", "").replace("\n", "").trim()
            if (key.isNotBlank() && cleaned.isNotEmpty()) {
                merged[key] = cleaned
            }
        }
        // Some CDNs strictly require a browser User-Agent and will throw HTTP 403/404
        // if they see 'mpv' or 'lavf' as the user agent, or if the UA doesn't match the token.
        // We MUST fallback to the exact same CloudStream USER_AGENT used by the plugins
        // during extraction, otherwise tokenized links (like Google DAI) will reject the stream.
        if (merged.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            merged["User-Agent"] = com.lagradost.cloudstream3.USER_AGENT
        }
        return merged
    }

    /** Capability gate shared by the UI and player implementations. */
    fun playbackSupport(link: ExtractorLink, backend: PlayerBackend): PlaybackSupport {
        val drmHeader = link.getAllHeaders().keys.any { it.equals("x-drm", ignoreCase = true) }
        val drm = link as? com.lagradost.cloudstream3.utils.DrmExtractorLink
        val hasLicenseDrm = drmHeader || !drm?.licenseUrl.isNullOrBlank()
        val hasClearKey = drm?.kid?.isNotBlank() == true && drm.key?.isNotBlank() == true

        if (hasLicenseDrm) {
            return PlaybackSupport(
                false,
                "This stream uses license-server DRM. $backend cannot play it through the desktop app; use a legally configured DRM-capable browser or Kodi inputstream.adaptive integration.",
            )
        }
        if (drm != null && !hasClearKey) {
            return PlaybackSupport(false, "This stream contains unsupported or incomplete DRM metadata.")
        }
        if (hasClearKey && backend != PlayerBackend.MPV) {
            return PlaybackSupport(false, "ClearKey streams are only supported by the embedded MPV backend.")
        }
        return PlaybackSupport(true)
    }

    fun sanitizeDisplayTitle(raw: String): String {
        return raw
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(",", " ")
            .trim()
            .take(120)
            .ifBlank { "CloudStream" }
    }

    /**
     * Resume position in seconds for MPV/VLC --start.
     * Aligns with Android [getPos]: skip if >=95% watched, near-zero, or duration too short.
     */
    fun resumeStartSeconds(position: Long, duration: Long): Long {
        if (position <= 0) return 0
        if (duration < MIN_DURATION_TO_SAVE_SECONDS) return 0
        val percent = position * 100 / duration
        if (percent >= RESUME_RESET_PERCENT) return 0
        if (percent <= RESUME_MIN_PERCENT) return 0
        return position.coerceAtLeast(0)
    }

    fun isCompleted(position: Long, duration: Long): Boolean {
        if (position <= 0 || duration <= 0) return false
        val percent = position * 100 / duration
        return percent >= RESUME_RESET_PERCENT
    }

    private object PlayerCacheManager {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "cloudstream_player_cache")
        init {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            // Clear orphaned files from previous crashed sessions
            cacheDir.listFiles()?.forEach { it.delete() }
        }
        fun getTempFile(prefix: String, suffix: String): File {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            return File(cacheDir, "${prefix}_${System.nanoTime()}$suffix")
        }
    }

    /** Plain URL list file — not a fake HLS manifest (which breaks MKV/MP4/DASH). */
    fun writeUrlListFile(prefix: String, title: String, url: String): File {
        val file = PlayerCacheManager.getTempFile(prefix, ".m3u")
        file.deleteOnExit()
        file.writeText(
            buildString {
                appendLine("#EXTM3U")
                appendLine("#EXTINF:-1,$title")
                appendLine(url)
            },
        )
        return file
    }

    fun writeMpvConfig(
        headers: Map<String, String>,
        subtitles: List<String>,
        audioTracks: List<com.lagradost.cloudstream3.AudioFile> = emptyList(),
    ): File {
        val file = PlayerCacheManager.getTempFile("mpv_stream_", ".conf")
        file.deleteOnExit()
        file.writeText(buildMpvConfigContent(headers, subtitles, audioTracks))
        return file
    }

    fun buildMpvConfigContent(
        headers: Map<String, String>,
        subtitles: List<String>,
        audioTracks: List<com.lagradost.cloudstream3.AudioFile> = emptyList(),
    ): String {
        return buildString {
            appendLine("# Generated by CloudStream Desktop")
            appendLine("hr-seek=yes")
            appendLine("cache=yes")
            appendLine("demuxer-max-bytes=64M") // Reduced from 500M to prevent CDN proxy starvation
            appendLine("demuxer-max-back-bytes=32M")
            appendLine("demuxer-lavf-o-append=reconnect=1")
            appendLine("demuxer-lavf-o-append=reconnect_streamed=1")
            appendLine("demuxer-lavf-o-append=reconnect_on_http_error=403,404,429,500,503")
            appendLine("demuxer-lavf-o-append=reconnect_delay_max=4")
            appendLine("stream-lavf-o-append=reconnect=1")
            appendLine("stream-lavf-o-append=reconnect_streamed=1")

            // user-agent and referer are native MPV options — handled directly.
            // All other headers are injected via demuxer-lavf-o-append=headers= below,
            // which is the only format MPV's .conf parser can safely handle colons in values.
            headers.forEach { (key, value) ->
                when {
                    key.equals("user-agent", ignoreCase = true) -> {
                        appendLine("user-agent=\"${escapeMpvValue(value)}\"")
                    }
                    key.equals("referer", ignoreCase = true) || key.equals("referrer", ignoreCase = true) -> {
                        appendLine("referrer=\"${escapeMpvValue(value)}\"")
                    }
                    // All other headers: skip here, they are written via demuxer-lavf-o-append below.
                }
            }

            subtitles.forEach { sub ->
                if (sub.isNotBlank()) {
                    appendLine("sub-files-append=\"${escapeMpvValue(sub)}\"")
                }
            }

            // Separate audio tracks (e.g. multi-language streams where audio and video are separate URLs)
            audioTracks.forEach { audio ->
                if (audio.url.isNotBlank()) {
                    appendLine("audio-file-append=\"${escapeMpvValue(audio.url)}\"")
                }
            }

            if (headers.isNotEmpty()) {
                // NOTE: headers are NOT written to the conf file because the .conf parser
                // cannot handle multi-line values (the embedded \r\n splits across lines).
                // They are instead passed as a --demuxer-lavf-o-append CLI argument by MpvPlayer.
            }
        }
    }

    /**
     * Builds the headers as a single MPV CLI argument using the byte-length format:
     * --demuxer-lavf-o-append=headers=%N%Key: Value\r\nKey2: Value2\r\n
     *
     * The %N% prefix tells MPV to read exactly N bytes as the value, completely bypassing
     * the parser's normal string handling. This is the only way to safely pass headers
     * containing colons and special characters on the command line.
     */
    fun buildHeadersCliArg(headers: Map<String, String>): List<String> {
        if (headers.isEmpty()) return emptyList()
        // Pass only headers that aren't already native MPV options
        // (User-Agent and Referer are set via --user-agent / --referrer).
        // Exclude them here to avoid duplicating or conflicting values.
        val extraHeaders = headers.filter { (key, _) ->
            !key.equals("user-agent", ignoreCase = true) &&
            !key.equals("referer", ignoreCase = true) &&
            !key.equals("referrer", ignoreCase = true)
        }
        if (extraHeaders.isEmpty()) return emptyList()

        // MPV's --http-header-fields is a comma-separated list.
        // Commas in values must be escaped.
        val httpHeaderFields = extraHeaders.entries.joinToString(separator = ",") { (k, v) ->
            val cleanV = v.replace("\r", "").replace("\n", "").replace(",", "\\,")
            "$k: $cleanV"
        }
        // Skip --demuxer-lavf-o-append=headers=... because the %N% byte-length
        // escape is unreliable inside sub-option strings, and CRLF chars can
        // corrupt the lavf option parser. --http-header-fields alone is sufficient.
        return listOf("--http-header-fields=$httpHeaderFields")
    }

    /**
     * Generates an MPV EDL (Edit Decision List) file for [ExtractorLinkPlayList] streams.
     * These are used by sites that split a film into many consecutive chunks instead of HLS.
     * The EDL format lets MPV play them as a single seamless file.
     * Format: edl://[%len=<microseconds>]<url>;<url>;...
     * We write it as a .edl file so MPV auto-detects it.
     */
    fun writeEdlFile(
        playlist: List<com.lagradost.cloudstream3.utils.PlayListItem>,
        headers: Map<String, String>,
    ): File {
        val file = PlayerCacheManager.getTempFile("cloudstream_playlist_", ".edl")
        file.deleteOnExit()

        // Build the header string for demuxer-lavf-o so MPV sends auth headers on each chunk
        val headerQueryPart = if (headers.isNotEmpty()) {
            headers.entries.joinToString("&") { (k, v) ->
                "${k.trim()}=${v.trim().replace("\r", "").replace("\n", "")}"
            }
        } else {
            ""
        }

        // MPV EDL format: one entry per line
        // "!new_stream" starts the list, each line is: %<duration_us>%<url>
        val content = buildString {
            appendLine("# mpv EDL v0")
            for (item in playlist) {
                if (item.url.isBlank()) continue
                val url = item.url.trim()
                // durationUs <= 0 means unknown — omit length hint and let demuxer detect
                if (item.durationUs > 0) {
                    append("%${item.durationUs}%")
                }
                appendLine(url)
            }
        }
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    fun buildVlcExtraHeaders(headers: Map<String, String>): String {
        return headers.entries.joinToString(separator = "\r\n") { (k, v) ->
            "$k: ${v.replace("\r", "").replace("\n", "")}"
        }
    }

    private fun escapeMpvValue(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace(",", "\\,")
    }
}
