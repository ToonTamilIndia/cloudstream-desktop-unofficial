package com.lagradost.cloudstream3.desktop.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.delay
import java.awt.Canvas
import java.awt.Color
import java.awt.event.*
import java.io.File

@Composable
fun ComposeMpvPlayer(
    link: ExtractorLink,
    title: String?,
    subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    startPositionMs: Long,
    useLocalProxy: Boolean,
    onPlaybackReady: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onFinished: () -> Unit,
    onFullscreenToggle: (Boolean) -> Unit,
    onPositionChange: (Long, Long) -> Unit,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    var mpvHandle by remember { mutableStateOf<com.sun.jna.Pointer?>(null) }
    var hasEverPlayed by remember { mutableStateOf(false) }
    var lastFullscreenState by remember { mutableStateOf(false) }

    LaunchedEffect(mpvHandle) {
        val h = mpvHandle
        if (h != null) {
            val startTime = System.currentTimeMillis()
            while (true) {
                // Check if playback has started and track position
                val posStr = MpvLibrary.INSTANCE.mpv_get_property_string(h, "time-pos")
                val pos = posStr?.toDoubleOrNull()

                val durStr = MpvLibrary.INSTANCE.mpv_get_property_string(h, "duration")
                val dur = durStr?.toDoubleOrNull()

                if (pos != null && pos > 0.0) {
                    if (!hasEverPlayed) {
                        hasEverPlayed = true
                        onPlaybackReady()
                    }
                    if (dur != null && dur > 0.0) {
                        onPositionChange((pos * 1000).toLong(), (dur * 1000).toLong())
                    }
                }

                // Check fullscreen state
                val fsStr = MpvLibrary.INSTANCE.mpv_get_property_string(h, "fullscreen")
                val isFs = fsStr == "yes"
                if (isFs != lastFullscreenState) {
                    lastFullscreenState = isFs
                    onFullscreenToggle(isFs)
                }

                // Check for completion
                val eofStr = MpvLibrary.INSTANCE.mpv_get_property_string(h, "eof-reached")
                if (eofStr == "yes") {
                    if (hasEverPlayed) {
                        onFinished()
                    } else {
                        onPlaybackError("Stream failed to load or instantly ended.")
                    }
                    break
                }

                // Check timeout. Default to 45s to allow Playwright enough time to bypass Cloudflare.
                // Enforce minimum 45s even if user set it lower in settings, otherwise Cloudflare bypass will always fail.
                val timeoutStr = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT)
                val userTimeout = timeoutStr?.toLongOrNull() ?: 45000L
                val timeoutMs = maxOf(userTimeout, 45000L)
                if (!hasEverPlayed && System.currentTimeMillis() - startTime > timeoutMs) {
                    com.lagradost.common.logging.AppLogger.e("MPV timeout reached while buffering")
                    onPlaybackError("Connection timed out. The stream might be dead.")
                    break
                }

                delay(200)
            }
        }
    }

    val videoCanvas = remember {
        object : Canvas() {

            override fun addNotify() {
                super.addNotify()

                if (mpvHandle != null) return // Prevent multiple initializations (multi-audio bug)

                // Find MPV library and tell JNA where to find it
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val mpvExe = resolveMpvExecutable(isWindows)
                val mpvDir = if (mpvExe == null || mpvExe.absolutePath == "/") {
                    // On Linux/macOS, JNA will search system library paths automatically
                    if (isWindows) {
                        onPlaybackError("MPV library not found. Please install mpv.")
                        return
                    }
                    null
                } else {
                    val dir = mpvExe.parentFile
                    if (dir != null) {
                        System.setProperty("jna.library.path", dir.absolutePath)
                    }
                    dir
                }

                val lib = try {
                    MpvLibrary.INSTANCE
                } catch (t: Throwable) {
                    com.lagradost.common.logging.AppLogger.e("Unable to load libmpv", t)
                    onPlaybackError(MpvLibrary.installHint())
                    return
                }
                val handle = lib.mpv_create() ?: run {
                    onPlaybackError("Failed to initialize MPV Engine.")
                    return
                }
                mpvHandle = handle

                val portableConfigDir = if (mpvDir != null) File(mpvDir, "portable_config") else null

                lib.mpv_set_option_string(handle, "osc", "no")
                lib.mpv_set_option_string(handle, "vo", "gpu")

                // Apply User Settings & Logging
                PlayerConfig.applyMpvSettings(handle, lib)

                if (portableConfigDir != null && portableConfigDir.exists()) {
                    val configDirStr = portableConfigDir.absolutePath.replace("\\", "/")
                    lib.mpv_set_option_string(handle, "config-dir", configDirStr)
                    lib.mpv_set_option_string(handle, "config", "yes")
                    lib.mpv_set_option_string(handle, "load-scripts", "yes")
                    lib.mpv_set_option_string(handle, "osd-fonts-dir", "$configDirStr/fonts")
                    lib.mpv_set_option_string(handle, "sub-fonts-dir", "$configDirStr/fonts")
                }

                val wid = com.sun.jna.Native.getComponentID(this)
                lib.mpv_set_option_string(handle, "wid", wid.toString())

                lib.mpv_set_option_string(handle, "input-default-bindings", "yes")
                lib.mpv_set_option_string(handle, "input-vo-keyboard", "yes")
                lib.mpv_set_option_string(handle, "save-position-on-quit", "no")
                lib.mpv_set_option_string(handle, "resume-playback", "no")
                lib.mpv_set_option_string(handle, "keep-open", "yes")
                lib.mpv_set_option_string(handle, "tls-verify", "no")
                lib.mpv_set_option_string(handle, "ytdl", "no")
                lib.mpv_set_option_string(handle, "idle", "yes")

                // Network reliability optimizations
                val validated = PlayerLinkHandler.validate(link, title, useLocalProxy).getOrElse {
                    onPlaybackError(it.message ?: "Validation failed")
                    return
                }
                PlayerLinkHandler.playbackSupport(link, PlayerLinkHandler.PlayerBackend.MPV).let { support ->
                    if (!support.supported) {
                        onPlaybackError(support.reason ?: "MPV does not support this stream.")
                        return
                    }
                }

                val lavfAppendOptions = mutableListOf<String>()

                when (validated.streamKind) {
                    PlayerLinkHandler.StreamKind.HLS -> {
                        lib.mpv_set_option_string(handle, "hls-bitrate", "max")
                        lavfAppendOptions.add("reconnect=1,reconnect_streamed=1,reconnect_on_http_error=403,404,429,500,503")
                    }
                    PlayerLinkHandler.StreamKind.DASH -> {
                        lavfAppendOptions.add("reconnect=1,reconnect_streamed=1")
                    }
                    else -> {}
                }

                val startSec = startPositionMs / 1000L
                if (startSec > 0) {
                    lib.mpv_set_option_string(handle, "start", startSec.toString())
                }

                if (validated.displayTitle.isNotBlank()) {
                    lib.mpv_set_option_string(handle, "force-media-title", validated.displayTitle)
                    lib.mpv_set_option_string(handle, "title", validated.displayTitle)
                }

                // Rewrite subtitles if using proxy
                val sessionId = validated.proxySessionId
                val finalSubtitles = if (sessionId != null) {
                    subtitles.map {
                        it.copy(url = com.lagradost.player.impl.proxy.LocalStreamProxy.buildProxyUrl(sessionId, it.url))
                    }
                } else {
                    subtitles
                }

                // Headers & Config (We explicitly pass emptyList for subtitles to prevent blocking)
                val mpvConfig = PlayerLinkHandler.writeMpvConfig(validated.headers, emptyList(), validated.audioTracks)
                lib.mpv_set_option_string(handle, "include", mpvConfig.absolutePath.replace("\\", "/"))

                val headerArgs = PlayerLinkHandler.buildHeadersCliArg(validated.headers)
                headerArgs.forEach { arg ->
                    val split = arg.removePrefix("--").split("=", limit = 2)
                    if (split.size == 2) {
                        MpvLibrary.INSTANCE.mpv_set_option_string(handle, split[0], split[1])
                    }
                }

                // ClearKey DRM: pass decryption keys to MPV if present
                val drmInfo = validated.drmInfo
                if (drmInfo != null) {
                    val drmKey = drmInfo.key
                    val drmKid = drmInfo.kid
                    if (drmKey != null && drmKid != null) {
                        try {
                            val keyHex = base64ToHex(drmKey)
                            val kidHex = base64ToHex(drmKid)
                            lavfAppendOptions.add("decryption_key=$keyHex")
                            lavfAppendOptions.add("decryption_kid=$kidHex")
                            com.lagradost.common.logging.AppLogger.i("Applied ClearKey DRM decryption for ${validated.displayTitle}")
                        } catch (e: Exception) {
                            com.lagradost.common.logging.AppLogger.e("Failed to apply DRM decryption keys", e)
                        }
                    } else if (drmInfo.licenseUrl != null) {
                        com.lagradost.common.logging.AppLogger.w("Widevine/PlayReady DRM detected but not supported on desktop: ${drmInfo.licenseUrl}")
                    }
                }

                // Apply accumulated demuxer-lavf-o-append options in one shot
                if (lavfAppendOptions.isNotEmpty()) {
                    lib.mpv_set_option_string(handle, "demuxer-lavf-o-append", lavfAppendOptions.joinToString(","))
                }

                com.lagradost.common.logging.AppLogger.i("Initializing embedded MPV for URL: ${validated.url}")
                val initCode = lib.mpv_initialize(handle)
                if (initCode < 0) {
                    mpvHandle = null
                    lib.mpv_terminate_destroy(handle)
                    onPlaybackError("MPV initialization failed (error $initCode). ${MpvLibrary.installHint()}")
                    return
                }

                val urlTarget = if (validated.useUrlFile) {
                    PlayerLinkHandler.writeUrlListFile("cloudstream_mpv_url_", validated.displayTitle, validated.url).absolutePath
                } else {
                    validated.url
                }

                val safeUrl = urlTarget.replace("\\", "/")
                lib.mpv_command_string(handle, "loadfile \"$safeUrl\"")

                // Load subtitles asynchronously to prevent MPV from blocking the video stream
                // downloading 15+ subtitles sequentially (which causes video server timeouts).
                kotlin.concurrent.thread(isDaemon = true) {
                    Thread.sleep(1000)
                    finalSubtitles.forEach { sub ->
                        val escapedSub = sub.url.replace("\\", "\\\\").replace("\"", "\\\"")
                        val escapedTitle = sub.lang.replace("\\", "\\\\").replace("\"", "\\\"")
                        MpvLibrary.INSTANCE.mpv_command_string(handle, "sub-add \"$escapedSub\" auto \"$escapedTitle\"")
                    }
                }

                // Setup Mouse and Keyboard interactions
                val canvas = this
                canvas.addMouseMotionListener(object : MouseMotionAdapter() {
                    override fun mouseMoved(e: MouseEvent) {
                        mpvHandle?.let { h -> MpvLibrary.INSTANCE.mpv_command_string(h, "mouse ${e.x} ${e.y}") }
                    }
                    override fun mouseDragged(e: MouseEvent) {
                        mpvHandle?.let { h -> MpvLibrary.INSTANCE.mpv_command_string(h, "mouse ${e.x} ${e.y}") }
                    }
                })

                canvas.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        canvas.requestFocusInWindow()
                        mpvHandle?.let { h ->
                            if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
                                val inOscArea = e.y > canvas.height - 130 || e.y < 60
                                if (!inOscArea) {
                                    MpvLibrary.INSTANCE.mpv_command_string(h, "cycle fullscreen")
                                    return
                                }
                            }
                            MpvLibrary.INSTANCE.mpv_command_string(h, "mouse ${e.x} ${e.y}")
                            val btn = when (e.button) {
                                MouseEvent.BUTTON1 -> "MBTN_LEFT"
                                MouseEvent.BUTTON2 -> "MBTN_MID"
                                MouseEvent.BUTTON3 -> "MBTN_RIGHT"
                                else -> return
                            }
                            MpvLibrary.INSTANCE.mpv_command_string(h, "keydown $btn")
                        }
                    }
                    override fun mouseReleased(e: MouseEvent) {
                        mpvHandle?.let { h ->
                            val btn = when (e.button) {
                                MouseEvent.BUTTON1 -> "MBTN_LEFT"
                                MouseEvent.BUTTON2 -> "MBTN_MID"
                                MouseEvent.BUTTON3 -> "MBTN_RIGHT"
                                else -> return
                            }
                            MpvLibrary.INSTANCE.mpv_command_string(h, "keyup $btn")
                        }
                    }
                })

                canvas.addMouseWheelListener { e ->
                    mpvHandle?.let { h ->
                        val key = if (e.wheelRotation < 0) "WHEEL_UP" else "WHEEL_DOWN"
                        MpvLibrary.INSTANCE.mpv_command_string(h, "keypress $key")
                    }
                }

                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { e ->
                    if (e.id == KeyEvent.KEY_PRESSED) {
                        mpvHandle?.let { h ->
                            val mpvKey = awtKeyToMpv(e)
                            if (mpvKey?.contains("QUIT_OVERRIDE") == true) {
                                onCloseRequest()
                            } else if (mpvKey == "ENTER") {
                                MpvLibrary.INSTANCE.mpv_command_string(h, "cycle fullscreen")
                            } else if (mpvKey != null) {
                                // Prefer keypress for character keys if possible, but keydown works fine for all if exact.
                                // Actually, mpv handles 'keydown' for all mapped string names.
                                MpvLibrary.INSTANCE.mpv_command_string(h, "keydown $mpvKey")
                            }
                        }
                    } else if (e.id == KeyEvent.KEY_RELEASED) {
                        mpvHandle?.let { h ->
                            val mpvKey = awtKeyToMpv(e)
                            if (mpvKey != null && !mpvKey.contains("QUIT_OVERRIDE") && mpvKey != "ENTER") {
                                MpvLibrary.INSTANCE.mpv_command_string(h, "keyup $mpvKey")
                            }
                        }
                    }
                    false
                }

                canvas.requestFocusInWindow()
            }

            override fun removeNotify() {
                mpvHandle?.let { MpvLibrary.INSTANCE.mpv_terminate_destroy(it) }
                mpvHandle = null
                super.removeNotify()
            }
        }.apply {
            background = Color.BLACK
            isFocusable = true
        }
    }

    SwingPanel(
        background = androidx.compose.ui.graphics.Color.Black,
        factory = { videoCanvas },
        modifier = modifier,
    )
}

private fun resolveMpvExecutable(isWindows: Boolean): File? {
    // Bundled (portable) paths
    val resDir = System.getProperty("compose.application.resources.dir")

    val names = if (isWindows) listOf("libmpv-2.dll") else listOf("libmpv.so", "libmpv.dylib")

    val candidates = listOfNotNull(
        resDir?.let { File(it, "mpv") },
        File("mpv"),
        File("2_cloudstream_desktop/mpv"),
        File("desktop-app/mpv"),
        File("desktop-app/appResources/mpv")
    )
    for (base in candidates) {
        for (name in names) {
            val f = File(base, name)
            if (f.isFile) return f.absoluteFile
        }
    }

    // On Linux/macOS, check system-installed library paths
    if (!isWindows) {
        val osName = System.getProperty("os.name").lowercase()

        // Check ldconfig first (Linux)
        if (osName.contains("linux")) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("ldconfig", "-p"))
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!
                    // Matches lines like: "libmpv.so (libc6,x86-64) => /usr/lib/x86_64-linux-gnu/libmpv.so"
                    val match = Regex("""\s+(libmpv\S*)\s+.*=>\s+(/\S+)""").find(l)
                    if (match != null) {
                        val libFile = File(match.groupValues[2])
                        if (libFile.isFile) {
                            return libFile.absoluteFile
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Common system library paths
        val systemPaths = listOf(
            "/usr/lib/x86_64-linux-gnu",
            "/usr/lib/aarch64-linux-gnu",
            "/usr/lib/arm-linux-gnueabihf",
            "/usr/lib",
            "/usr/local/lib",
            "/lib/x86_64-linux-gnu",
            "/lib/aarch64-linux-gnu",
            "/opt/homebrew/lib",
            "/usr/local/opt/mpv/lib",
            "/usr/lib64",
        )
        for (dir in systemPaths) {
            val dirFile = File(dir)
            if (dirFile.isDirectory) {
                for (name in names) {
                    val f = File(dirFile, name)
                    if (f.isFile) return f.absoluteFile
                }
                // Also search for versioned .so files like libmpv.so.1, libmpv.so.2
                val versioned = dirFile.listFiles { _, name ->
                    name.startsWith("libmpv.so") && name.endsWith(".so")
                }
                if (!versioned.isNullOrEmpty()) {
                    return versioned.first().absoluteFile
                }
            }
        }
    }

    // If nothing found, still return a non-null fallback so JNA can try system library path
    // This prevents the "MPV executable not found" early exit
    return File("/")
}

private fun base64ToHex(base64: String): String {
    val decoded = java.util.Base64.getDecoder().decode(base64.trim())
    return decoded.joinToString("") { "%02x".format(it) }
}

private fun awtKeyToMpv(e: KeyEvent): String? {
    if (e.isShiftDown) {
        when (e.keyCode) {
            KeyEvent.VK_3 -> return "#"
            KeyEvent.VK_1 -> return "!"
            KeyEvent.VK_2 -> return "@"
            KeyEvent.VK_4 -> return "$"
            KeyEvent.VK_5 -> return "%"
            KeyEvent.VK_6 -> return "^"
            KeyEvent.VK_7 -> return "&"
            KeyEvent.VK_8 -> return "*"
            KeyEvent.VK_9 -> return "("
            KeyEvent.VK_0 -> return ")"
            KeyEvent.VK_OPEN_BRACKET -> return "{"
            KeyEvent.VK_CLOSE_BRACKET -> return "}"
            KeyEvent.VK_COMMA -> return "<"
            KeyEvent.VK_PERIOD -> return ">"
            KeyEvent.VK_MINUS -> return "_"
            KeyEvent.VK_EQUALS -> return "+"
            KeyEvent.VK_Q -> return "QUIT_OVERRIDE"
            in KeyEvent.VK_A..KeyEvent.VK_Z -> {
                val letter = KeyEvent.getKeyText(e.keyCode).uppercase()
                val ctrl = if (e.isControlDown) "Ctrl+" else ""
                val alt = if (e.isAltDown) "Alt+" else ""
                return "$ctrl$alt$letter"
            }
        }
    }

    val baseKey = when (e.keyCode) {
        KeyEvent.VK_SPACE -> "SPACE"
        KeyEvent.VK_LEFT -> "LEFT"
        KeyEvent.VK_RIGHT -> "RIGHT"
        KeyEvent.VK_UP -> "UP"
        KeyEvent.VK_DOWN -> "DOWN"
        KeyEvent.VK_ENTER -> "ENTER"
        KeyEvent.VK_ESCAPE -> "ESC"
        KeyEvent.VK_BACK_SPACE -> "BS"
        KeyEvent.VK_DELETE -> "DEL"
        KeyEvent.VK_TAB -> "TAB"
        KeyEvent.VK_PAGE_UP -> "PGUP"
        KeyEvent.VK_PAGE_DOWN -> "PGDWN"
        KeyEvent.VK_HOME -> "HOME"
        KeyEvent.VK_END -> "END"

        KeyEvent.VK_Q -> "QUIT_OVERRIDE"

        in KeyEvent.VK_A..KeyEvent.VK_Z -> KeyEvent.getKeyText(e.keyCode).lowercase()
        in KeyEvent.VK_0..KeyEvent.VK_9 -> KeyEvent.getKeyText(e.keyCode)

        KeyEvent.VK_COMMA -> ","
        KeyEvent.VK_PERIOD -> "."
        KeyEvent.VK_SLASH, KeyEvent.VK_DIVIDE -> "/"
        KeyEvent.VK_MULTIPLY -> "*"
        KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> "-"
        KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> "+"
        KeyEvent.VK_OPEN_BRACKET -> "["
        KeyEvent.VK_CLOSE_BRACKET -> "]"
        KeyEvent.VK_BACK_SLASH -> "\\"
        KeyEvent.VK_SEMICOLON -> ";"
        KeyEvent.VK_QUOTE -> "'"

        else -> return null
    }

    val alt = if (e.isAltDown) "Alt+" else ""
    val ctrl = if (e.isControlDown) "Ctrl+" else ""
    val shift = if (e.isShiftDown && e.keyCode !in KeyEvent.VK_A..KeyEvent.VK_Z && baseKey.length > 1) "Shift+" else ""

    return "$ctrl$alt$shift$baseKey"
}
