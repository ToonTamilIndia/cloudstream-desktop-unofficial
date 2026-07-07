package com.lagradost.cloudstream3.desktop.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.player.impl.PlayerLinkHandler
import com.lagradost.player.impl.proxy.LocalStreamProxy
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import org.json.JSONObject
import java.awt.Color
import javax.swing.SwingUtilities

private const val HLS_LIBRARY = "https://cdn.jsdelivr.net/npm/hls.js@0.14.17/dist/hls.min.js"
private const val DASH_LIBRARY = "https://cdnjs.cloudflare.com/ajax/libs/dashjs/4.7.4/dash.all.min.js"

/** Browser-backed player kept inside the Compose window; popup creation is explicitly disabled. */
@Composable
fun ComposeJwPlayer(
    link: ExtractorLink,
    title: String?,
    subtitles: List<SubtitleFile>,
    startPositionMs: Long,
    useLocalProxy: Boolean,
    onPlaybackReady: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onFinished: () -> Unit,
    onFullscreenToggle: (Boolean) -> Unit,
    onPositionChange: (Long, Long) -> Unit,
    onCloseRequest: () -> Unit,
    modifier: Modifier,
) {
    val validated = remember(link, title, useLocalProxy) {
        // Browser media requests cannot carry extractor headers directly. Always use the
        // localhost proxy for recursive DASH/HLS requests and ClearKey decryption.
        PlayerLinkHandler.validate(link, title, useLocalProxy = true)
    }
    val panel = remember { JFXPanel().apply { background = Color.BLACK } }
    val bridge = remember {
        JwPlayerBridge(
            onReady = onPlaybackReady,
            onError = onPlaybackError,
            onFinished = onFinished,
            onFullscreen = onFullscreenToggle,
            onPosition = onPositionChange,
            onClose = onCloseRequest,
        )
    }

        DisposableEffect(panel, validated) {
        val stream = validated.getOrElse {
            onPlaybackError(it.message ?: "Invalid stream")
            return@DisposableEffect onDispose { }
        }
        Platform.setImplicitExit(false)
        Platform.runLater {
            try {
                val view = WebView()
                val engine = view.engine
                engine.isJavaScriptEnabled = true
                engine.createPopupHandler = javafx.util.Callback { null }
                engine.confirmHandler = javafx.util.Callback { false }
                engine.loadWorker.stateProperty().addListener { _, _, state ->
                    if (state == Worker.State.SUCCEEDED) {
                        val window = engine.executeScript("window") as JSObject
                        window.setMember("cloudstreamBridge", bridge)
                        engine.executeScript("window.startCloudstreamPlayer()")
                    } else if (state == Worker.State.FAILED) {
                        val exc = engine.loadWorker.exception
                        bridge.error(exc?.message ?: "Unable to load the embedded JW Player page")
                    }
                }
                engine.setOnError { event ->
                    bridge.error("WebEngine error: ${event.message}")
                }
                panel.scene = Scene(view)
                engine.loadContent(buildPlayerHtml(stream, title, subtitles, startPositionMs))
            } catch (error: Throwable) {
                bridge.error(error.message ?: "Unable to initialize embedded browser")
            }
        }

        onDispose {
            Platform.runLater {
                val engine = (panel.scene?.root as? WebView)?.engine
                runCatching { engine?.executeScript("window.destroyCloudstreamPlayer && window.destroyCloudstreamPlayer()") }
                engine?.load(null)
                panel.scene = null
            }
        }
    }

    SwingPanel(
        factory = { panel },
        modifier = modifier,
        background = androidx.compose.ui.graphics.Color.Black,
    )
}

private fun buildPlayerHtml(
    stream: PlayerLinkHandler.ValidatedLink,
    title: String?,
    subtitles: List<SubtitleFile>,
    startPositionMs: Long,
): String {
    val startSeconds = (startPositionMs / 1000.0).coerceAtLeast(0.0)
    val urlJson = JSONObject.quote(stream.url)
    val subtitleTracks = subtitles.filter { it.url.isNotBlank() }
    return buildString {
        appendLine("<!doctype html>")
        appendLine("<html><head><meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">")
        when (stream.streamKind) {
            PlayerLinkHandler.StreamKind.HLS -> appendLine("<script src=\"$HLS_LIBRARY\"></script>")
            PlayerLinkHandler.StreamKind.DASH -> appendLine("<script src=\"$DASH_LIBRARY\"></script>")
            else -> {}
        }
        appendLine("<style>html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#000}video{width:100%;height:100%;display:block}</style>")
        appendLine("</head><body>")
        append("<video id=\"v\" autoplay playsinline")
        if (stream.streamKind == PlayerLinkHandler.StreamKind.PROGRESSIVE) append(" src=$urlJson")
        appendLine(">")
        subtitleTracks.forEach { sub ->
            val subUrl = stream.proxySessionId?.let { LocalStreamProxy.buildProxyUrl(it, sub.url) } ?: sub.url
            appendLine("<track kind=\"captions\" src=\"${JSONObject.quote(subUrl)}\" label=\"${sub.lang}\">")
        }
        appendLine("</video>")
        appendLine("""
<script>
let v=document.getElementById('v'),pl=null,timer;
window.onerror=function(m,s,l,c,e){try{cloudstreamBridge.error('JS: '+(e&&e.stack||m))}catch(ex){}};
function initHls(){if(Hls.isSupported()){pl=new Hls();pl.loadSource($urlJson);pl.attachMedia(v);}else if(v.canPlayType('application/vnd.apple.mpegurl')){v.src=$urlJson;}else{cloudstreamBridge.error('HLS not supported');}}
function initDash(){pl=dashjs.MediaPlayer().create();pl.initialize(v,$urlJson,true);}
${when (stream.streamKind) {
    PlayerLinkHandler.StreamKind.HLS -> "initHls();"
    PlayerLinkHandler.StreamKind.DASH -> "initDash();"
    else -> ""
}}
v.addEventListener('canplay',function(){if(timer){clearTimeout(timer);timer=null;}cloudstreamBridge.ready();if($startSeconds>0)v.currentTime=$startSeconds;});
v.addEventListener('timeupdate',function(){cloudstreamBridge.position(v.currentTime||0,v.duration||0);});
v.addEventListener('ended',function(){cloudstreamBridge.finished();});
v.addEventListener('error',function(){cloudstreamBridge.error(v.error?('Video err '+v.error.code+': '+v.error.message):'Video playback failed');});
timer=setTimeout(function(){cloudstreamBridge.error('Playback setup timed out');timer=null;},60000);
window.destroyCloudstreamPlayer=function(){if(timer){clearTimeout(timer);}try{if(pl&&pl.destroy)pl.destroy()}catch(e){}try{if(pl&&pl.reset)pl.reset()}catch(e){}v.pause();v.removeAttribute('src');v.load();};
window.open=function(){return null;};
</script>
""".trimIndent())
        appendLine("</body></html>")
    }
}

class JwPlayerBridge(
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val onFinished: () -> Unit,
    private val onFullscreen: (Boolean) -> Unit,
    private val onPosition: (Long, Long) -> Unit,
    private val onClose: () -> Unit,
) {
    private fun dispatch(block: () -> Unit) = SwingUtilities.invokeLater(block)

    fun ready() = dispatch(onReady)
    fun error(message: String?) = dispatch { onError(message ?: "JW Player playback failed") }
    fun finished() = dispatch(onFinished)
    fun fullscreen(value: Boolean) = dispatch { onFullscreen(value) }
    fun position(positionSeconds: Double, durationSeconds: Double) = dispatch {
        onPosition((positionSeconds * 1000).toLong(), (durationSeconds * 1000).toLong())
    }
    fun close() = dispatch(onClose)
}
