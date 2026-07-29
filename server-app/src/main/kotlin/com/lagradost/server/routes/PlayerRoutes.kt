package com.lagradost.server.routes

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.player.impl.proxy.LocalStreamProxy
import com.lagradost.server.utils.respondJson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val HLS_LIBRARY = "https://cdn.jsdelivr.net/npm/hls.js@0.14.17/dist/hls.min.js"
private const val DASH_LIBRARY = "https://cdnjs.cloudflare.com/ajax/libs/dashjs/4.7.4/dash.all.min.js"

/**
 * GET /api/player?data={data}&referer={referer}
 * Generate player HTML with HLS.js/DASH.js for browser playback
 */
fun Route.registerPlayerRoutes() {
    get("/api/player") {
        val data = call.request.queryParameters["data"] ?: ""
        val referer = call.request.queryParameters["referer"] ?: ""
        val title = call.request.queryParameters["title"] ?: "Video"
        val startPosition = call.request.queryParameters["start"]?.toLongOrNull() ?: 0L

        if (data.isBlank()) {
            call.respondJson(mapOf("error" to "Query parameter 'data' is required"))
            return@get
        }

        val links = mutableListOf<LinkResult>()
        val subtitles = mutableListOf<SubtitleResult>()
        val sessionHeaders = mutableMapOf<String, String>()

        if (referer.isNotBlank()) {
            sessionHeaders["Referer"] = referer
        }
        sessionHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

        val api = APIHolder.getApiFromUrlNull(data)
            ?: APIHolder.allProviders.firstOrNull { it.mainUrl != "NONE" }

        if (api != null) {
            runBlocking {
                api.loadLinks(
                    data = data,
                    isCasting = false,
                    subtitleCallback = { sub ->
                        subtitles.add(
                            SubtitleResult(
                                lang = sub.lang,
                                url = sub.url,
                                headers = sub.headers
                            )
                        )
                    },
                    callback = { link ->
                        val h = link.getAllHeaders()
                        links.add(
                            LinkResult(
                                name = link.name,
                                url = link.url,
                                quality = link.quality,
                                type = link.type.name,
                                headers = h,
                                isM3u8 = link.isM3u8,
                                isDash = link.isDash
                            )
                        )
                    }
                )
            }
        }

        val bestLink = links.maxByOrNull { it.quality }
        if (bestLink == null) {
            call.respondText("<html><body><h1>No links found</h1></body></html>", ContentType.Text.Html)
            return@get
        }

        val streamKind = when {
            bestLink.isM3u8 -> "hls"
            bestLink.isDash -> "dash"
            else -> "progressive"
        }

        val sessionId = LocalStreamProxy.registerSession(sessionHeaders)

        val streamUrl = if (bestLink.isM3u8 || bestLink.isDash || bestLink.url.contains(".mp4")) {
            LocalStreamProxy.buildProxyUrl(sessionId, bestLink.url)
        } else {
            bestLink.url
        }

        val subtitleTracks = subtitles.filter { it.url.isNotBlank() }.map { sub ->
            val subUrl = LocalStreamProxy.buildProxyUrl(sessionId, sub.url)
            """<track kind="captions" src="${org.json.JSONObject.quote(subUrl)}" label="${sub.lang}">"""
        }.joinToString("\n")

        val startSeconds = (startPosition / 1000.0).coerceAtLeast(0.0)

        val playerHtml = buildString {
            appendLine("<!doctype html>")
            appendLine("<html><head><meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">")
            appendLine("<title>$title - CloudStream</title>")

            when (streamKind) {
                "hls" -> appendLine("<script src=\"$HLS_LIBRARY\"></script>")
                "dash" -> appendLine("<script src=\"$DASH_LIBRARY\"></script>")
            }

            appendLine("""
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html,body { width: 100%; height: 100%; overflow: hidden; background: #000; font-family: system-ui, sans-serif; }
#player-container { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: #000; }
#v { width: 100%; height: 100%; display: block; }
.controls { position: absolute; bottom: 0; left: 0; right: 0; background: linear-gradient(transparent,rgba(0,0,0,0.8)); padding: 20px; opacity: 0; transition: opacity 0.3s; }
.controls.show { opacity: 1; }
.progress-bar { width: 100%; height: 4px; background: rgba(255,255,255,0.3); cursor: pointer; position: relative; }
.progress-fill { height: 100%; background: #ff9800; width: 0; transition: width 0.1s; }
.buttons { display: flex; align-items: center; gap: 15px; margin-top: 10px; }
button { background: none; border: none; color: white; cursor: pointer; font-size: 24px; }
.time { color: white; font-size: 14px; }
.quality-select { background: #333; color: white; border: none; padding: 5px 10px; border-radius: 4px; }
</style>
</head>
<body>
<div id="player-container">
    <video id="v" autoplay playsinline>
        $subtitleTracks
    </video>
    <div class="controls" id="controls">
        <div class="progress-bar" id="progress">
            <div class="progress-fill" id="progressFill"></div>
        </div>
        <div class="buttons">
            <button id="playPause">⏸</button>
            <span class="time" id="timeDisplay">0:00 / 0:00</span>
            <button id="fullscreen">⛶</button>
            <select class="quality-select" id="quality">
                <option value="">Auto</option>
            </select>
        </div>
    </div>
</div>
<script>
const video = document.getElementById('v');
const controls = document.getElementById('controls');
const progress = document.getElementById('progress');
const progressFill = document.getElementById('progressFill');
const timeDisplay = document.getElementById('timeDisplay');
const playPause = document.getElementById('playPause');
const fullscreen = document.getElementById('fullscreen');
const quality = document.getElementById('quality');

let player = null;
const streamUrl = ${org.json.JSONObject.quote(streamUrl)};
const startSeconds = $startSeconds;

function formatTime(s) {
    if (!s || !isFinite(s)) return "0:00";
    const m = Math.floor(s / 60);
    const sec = Math.floor(s % 60);
    return m + ":" + (sec < 10 ? "0" : "") + sec;
}

function initHls() {
    if (Hls.isSupported()) {
        player = new Hls();
        player.loadSource(streamUrl);
        player.attachMedia(video);
        player.on(Hls.Events.MANIFEST_PARSED, () => {
            if (startSeconds > 0) video.currentTime = startSeconds;
        });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = streamUrl;
        if (startSeconds > 0) video.currentTime = startSeconds;
    } else {
        alert('HLS not supported');
    }
}

function initDash() {
    player = dashjs.MediaPlayer().create();
    player.initialize(video, streamUrl, true);
}

${when (streamKind) {
    "hls" -> "initHls();"
    "dash" -> "initDash();"
    else -> "video.src = streamUrl;"
}}

video.addEventListener('loadedmetadata', () => {
    updateTime();
});

video.addEventListener('timeupdate', updateTime);

function updateTime() {
    const cur = formatTime(video.currentTime);
    const dur = formatTime(video.duration);
    timeDisplay.textContent = cur + " / " + dur;
    if (video.duration > 0) {
        progressFill.style.width = (video.currentTime / video.duration * 100) + "%";
    }
}

progress.addEventListener('click', (e) => {
    const rect = progress.getBoundingClientRect();
    const pos = (e.clientX - rect.left) / rect.width;
    video.currentTime = pos * video.duration;
});

video.addEventListener('click', () => {
    if (video.paused) video.play(); else video.pause();
});

video.addEventListener('mousemove', () => {
    controls.classList.add('show');
    clearTimeout(window.hideControls);
    window.hideControls = setTimeout(() => controls.classList.remove('show'), 3000);
});

playPause.addEventListener('click', () => {
    if (video.paused) video.play(); else video.pause();
});

playPause.textContent = video.paused ? "▶" : "⏸";

video.addEventListener('play', () => playPause.textContent = "⏸");
video.addEventListener('pause', () => playPause.textContent = "▶");

fullscreen.addEventListener('click', () => {
    if (document.fullscreenElement) {
        document.exitFullscreen();
    } else {
        document.documentElement.requestFullscreen();
    }
});
</script>
</body>
</html>
""".trimIndent())
        }

        call.respondText(playerHtml, ContentType.Text.Html)
    }

    // Get proxy session info
    get("/api/proxy/session/{sessionId}") {
        val sessionId = call.parameters["sessionId"]
        val session = sessionId?.let { LocalStreamProxy.getSession(it) }

        if (session != null) {
            call.respondJson(mapOf(
                "sessionId" to sessionId,
                "hasDrm" to (session.drmKey != null),
                "kidHex" to session.kidHex
            ))
        } else {
            call.respondJson(mapOf("error" to "Session not found"))
        }
    }
}