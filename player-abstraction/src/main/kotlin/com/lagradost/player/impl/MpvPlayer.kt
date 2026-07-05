package com.lagradost.player.impl

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.player.api.MediaPlayer
import com.lagradost.player.api.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Launches the user's external mpv executable with the original stream URL. */
class MpvPlayer : MediaPlayer {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val process = AtomicReference<Process?>(null)

    override suspend fun play(link: ExtractorLink, title: String?, subtitles: List<String>, startPositionMs: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val support = PlayerLinkHandler.playbackSupport(link, PlayerLinkHandler.PlayerBackend.MPV)
                require(support.supported) { support.reason ?: "MPV does not support this stream." }
                val stream = PlayerLinkHandler.validate(link, title, useLocalProxy = false).getOrThrow()
                val executable = findExecutable() ?: error("mpv executable not found. Install mpv and ensure it is on PATH.")
                val args = mutableListOf(executable, "--force-media-title=${stream.displayTitle}")
                if (startPositionMs > 0) args += "--start=${startPositionMs / 1000L}"
                stream.headers.forEach { (key, value) ->
                    when {
                        key.equals("user-agent", true) -> args += "--user-agent=$value"
                        key.equals("referer", true) || key.equals("referrer", true) -> args += "--referrer=$value"
                    }
                }
                args += PlayerLinkHandler.buildHeadersCliArg(stream.headers)
                subtitles.filter { it.isNotBlank() }.forEach { args += "--sub-file=$it" }
                args += stream.url

                process.getAndSet(null)?.destroy()
                val started = ProcessBuilder(args).redirectErrorStream(true).start()
                process.set(started)
                _state.value = PlayerState(isPlaying = true, currentUrl = stream.url)
                scope.launch {
                    try {
                        started.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { AppLogger.i("MPV: $it") }
                        }
                    } catch (_: java.io.IOException) {
                        // Expected when a previous MPV process is replaced and its output stream closes.
                    }
                    runCatching { started.waitFor() }
                    if (process.compareAndSet(started, null)) _state.value = PlayerState(isFinished = true)
                }
                AppLogger.i("Launching external MPV (${stream.streamKind}): ${stream.displayTitle}")
            }.onFailure { _state.value = PlayerState(error = it.message) }
        }

    private fun findExecutable(): String? {
        val os = System.getProperty("os.name").lowercase()
        val names = if (os.contains("win")) listOf("mpv.exe") else listOf("mpv")
        val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        for (dir in pathDirs) for (name in names) {
            File(dir, name).takeIf { it.isFile }?.let { return it.absolutePath }
        }
        if (os.contains("mac")) {
            listOf("/opt/homebrew/bin/mpv", "/usr/local/bin/mpv", "/Applications/mpv.app/Contents/MacOS/mpv")
                .firstOrNull { File(it).isFile }?.let { return it }
        }
        return null
    }

    override fun playLocal(file: File) {}
    override fun pause() {}
    override fun resume() {}
    override fun seek(positionMs: Long) {}
    override fun stop() { process.getAndSet(null)?.destroy() }
    override fun destroy() { stop(); scope.cancel() }
}
