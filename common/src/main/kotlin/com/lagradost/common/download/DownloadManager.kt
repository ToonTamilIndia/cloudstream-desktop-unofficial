package com.lagradost.common.download

import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Result of resolving a stream for download (mirrors the proxy link headers).
 */
data class DownloadableStream(
    val url: String,
    val filename: String,
    val headers: Map<String, String>,
    val isM3u8: Boolean,
    val isDash: Boolean,
)

/**
 * A single download task's live state.
 */
data class DownloadTask(
    val id: String,
    val title: String,
    val outFile: File,
    val status: DownloadStatus,
    val progress: Double,      // 0.0 .. 1.0
    val bytesDownloaded: Long,
    val totalBytes: Long,      // 0 if unknown
    val error: String? = null,
)

enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

/**
 * Shared cross-platform media downloader, used by both the server-app (web UI)
 * and the desktop-app.
 *
 * Supports:
 *  - Direct file downloads (MP4/MKV/etc) with progress + headers.
 *  - HLS (.m3u8) downloads: fetches the master/media playlist, then downloads every
 *    segment (with the plugin headers) and concatenates them into a single .ts file.
 *
 * All network requests go through the OkHttp client provided via [configureClient].
 * The desktop passes its `app.baseClient` (which carries the CloudflareBypass /
 * CloudflareKiller interceptor + DoH), so CDN-protected streams work in both UIs.
 */
object DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = ConcurrentHashMap<String, DownloadTask>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Swap in a custom OkHttpClient (e.g. the app's baseClient with Cloudflare bypass).
     * Calling this after downloads have started is not supported.
     */
    fun configureClient(newClient: OkHttpClient) {
        client = newClient
    }

    fun list(): List<DownloadTask> = tasks.values.sortedByDescending { it.id }

    fun get(id: String): DownloadTask? = tasks[id]

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        tasks[id]?.let { tasks[id] = transform(it) }
    }

    fun cancel(id: String): Boolean {
        cancelled.add(id)
        jobs[id]?.cancel()
        val existing = tasks[id]
        if (existing != null && (existing.status == DownloadStatus.DOWNLOADING || existing.status == DownloadStatus.QUEUED)) {
            tasks[id] = existing.copy(status = DownloadStatus.CANCELLED, progress = existing.progress)
        }
        return true
    }

    /**
     * Start downloading [stream] to the downloads directory.
     * Returns the created task.
     */
    fun start(
        stream: DownloadableStream,
        title: String,
    ): DownloadTask? {
        val ext = when {
            stream.isM3u8 -> "ts"
            stream.isDash -> "m4v"
            else -> extensionOf(stream.url)
        }
        val baseName = sanitizeFilename(stream.filename)
        val dir = File(PlatformPaths.downloadsDir, baseName).also { it.parentFile?.mkdirs() }
        val outFile = File(dir, "$baseName.$ext")

        val id = java.util.UUID.randomUUID().toString()
        val task = DownloadTask(
            id = id,
            title = title,
            outFile = outFile,
            status = DownloadStatus.QUEUED,
            progress = 0.0,
            bytesDownloaded = 0,
            totalBytes = 0,
        )
        tasks[id] = task

        val job = scope.launch {
            try {
                tasks[id] = task.copy(status = DownloadStatus.DOWNLOADING)
                when {
                    stream.isM3u8 -> {
                        val segments = fetchHlsSegments(stream.url, stream.headers)
                        downloadAndConcat(segments, outFile, stream.headers, id)
                    }
                    stream.isDash -> downloadStreamToFile(stream.url, outFile, stream.headers, id)
                    else -> downloadStreamToFile(stream.url, outFile, stream.headers, id)
                }
                updateTask(id) { it.copy(status = DownloadStatus.COMPLETED, progress = 1.0, bytesDownloaded = outFile.length(), totalBytes = outFile.length()) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (cancelled.remove(id)) {
                    updateTask(id) { it.copy(status = DownloadStatus.CANCELLED) }
                    outFile.delete()
                } else {
                    updateTask(id) { it.copy(status = DownloadStatus.FAILED, error = e.message) }
                }
            } catch (e: Throwable) {
                AppLogger.e("Download $title failed", e)
                updateTask(id) { it.copy(status = DownloadStatus.FAILED, error = e.message) }
            } finally {
                jobs.remove(id)
                cancelled.remove(id)
            }
        }
        jobs[id] = job
        return task
    }

    private suspend fun downloadStreamToFile(url: String, outFile: File, headers: Map<String, String>, taskId: String) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            val total = resp.body?.contentLength() ?: 0L
            updateTask(taskId) { it.copy(totalBytes = total) }
            outFile.parentFile?.mkdirs()
            resp.body!!.byteStream().buffered().use { input ->
                java.nio.file.Files.newOutputStream(outFile.toPath()).buffered().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var count: Int
                    var done = 0L
                    val last = AtomicLong(0)
                    while (input.read(buf).also { count = it } != -1) {
                        ensureActive(taskId)
                        output.write(buf, 0, count)
                        done += count
                        val now = done
                        if (now - last.get() >= 256 * 1024) {
                            last.set(now)
                            updateTask(taskId) {
                                it.copy(
                                    bytesDownloaded = now,
                                    progress = if (total > 0) now.toDouble() / total else 0.0,
                                )
                            }
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    /**
     * Fetch an HLS playlist, returning the ordered segment URLs.
     * Resolves master playlists to the highest-bandwidth variant.
     */
    private suspend fun fetchHlsSegments(playlistUrl: String, headers: Map<String, String>): List<String> {
        val body: String = client.newCall(
            Request.Builder().url(playlistUrl).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} fetching playlist")
            resp.body!!.string()
        }

        val base = playlistUrl.substringBeforeLast('/') + "/"

        // Master playlist -> resolve to best variant, then fetch its media playlist.
        if (body.contains("#EXT-X-STREAM-INF")) {
            val variants = Regex("""#EXT-X-STREAM-INF[^\n]*\n([^\n]+)""").findAll(body).map { m ->
                val uri = m.groupValues[1].trim()
                val band = Regex("""BANDWIDTH=(\d+)""").find(m.groupValues[0])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                MediaVariant(uri, band)
            }.toList()
            val best = variants.maxByOrNull { it.bandwidth } ?: return emptyList()
            return fetchHlsSegments(resolve(base, best.uri), headers)
        }

        if (body.contains("#EXT-X-KEY") && !body.contains("#EXT-X-KEY:METHOD=NONE")) {
            throw java.io.IOException("Encrypted HLS (AES-128) is not supported by the downloader")
        }

        return body.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { resolve(base, it) }
    }

    private suspend fun downloadAndConcat(
        segments: List<String>,
        outFile: File,
        headers: Map<String, String>,
        taskId: String,
    ) {
        if (segments.isEmpty()) throw java.io.IOException("No HLS segments found")
        val total = segments.size.toLong()
        var done = 0L
        outFile.parentFile?.mkdirs()
        java.nio.file.Files.newOutputStream(outFile.toPath()).buffered().use { out ->
            var totalBytes = 0L
            segments.forEachIndexed { idx, segUrl ->
                ensureActive(taskId)
                val req = Request.Builder().url(segUrl).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} fetching segment $idx")
                    totalBytes += resp.body!!.byteStream().use { input -> input.copyTo(out) }
                }
                done++
                updateTask(taskId) {
                    it.copy(
                        bytesDownloaded = totalBytes,
                        totalBytes = totalBytes,
                        progress = done.toDouble() / total,
                    )
                }
            }
            out.flush()
        }
        if (cancelled.contains(taskId)) outFile.delete()
    }

    private fun resolve(base: String, uri: String): String {
        if (uri.startsWith("http")) return uri
        return if (base.endsWith("/")) base + uri else base + "/" + uri
    }

    private fun extensionOf(url: String): String {
        val noQuery = url.substringBefore('?').substringBefore('#')
        val dot = noQuery.lastIndexOf('.')
        return if (dot >= 0 && dot < noQuery.length - 1) noQuery.substring(dot + 1).take(6) else "mp4"
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(120)
    }

    private suspend fun ensureActive(taskId: String) {
        if (cancelled.contains(taskId)) throw kotlinx.coroutines.CancellationException("cancelled")
        kotlinx.coroutines.yield()
    }

    private data class MediaVariant(val uri: String, val bandwidth: Long)
}