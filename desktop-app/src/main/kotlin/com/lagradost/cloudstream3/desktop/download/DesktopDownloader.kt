package com.lagradost.cloudstream3.desktop.download

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.download.DownloadManager
import com.lagradost.common.download.DownloadStatus
import com.lagradost.common.download.DownloadableStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Desktop-side thin wrapper around the shared [DownloadManager] (in `common`).
 *
 * Uses `app.baseClient` (which carries the CloudflareBypass interceptor) so the
 * local download goes through the same network stack as the player.
 */
object DesktopDownloader {

    data class Progress(
        val status: com.lagradost.common.download.DownloadStatus,
        val fraction: Double = 0.0,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = 0,
        val error: String? = null,
    )

    @Volatile private var cancelled = false
    private val lastId = AtomicReference<String?>(null)

    init {
        DownloadManager.configureClient(app.baseClient)
    }

    suspend fun download(
        link: ExtractorLink,
        title: String,
        onProgress: (Progress) -> Unit,
    ): File? {
        cancelled = false
        val headers = link.getAllHeaders()
        val stream = DownloadableStream(
            url = link.url,
            filename = title,
            headers = headers,
            isM3u8 = link.isM3u8,
            isDash = link.isDash,
        )
        val task = DownloadManager.start(stream, title) ?: return null
        lastId.set(task.id)

        // Poll the manager and forward progress until the task finishes.
        return withContext(Dispatchers.IO) {
            var done = false
            while (!done) {
                if (cancelled) {
                    DownloadManager.cancel(task.id)
                    onProgress(Progress(com.lagradost.common.download.DownloadStatus.CANCELLED, error = "Cancelled"))
                    return@withContext null
                }
                val t = DownloadManager.get(task.id)
                if (t == null) {
                    onProgress(Progress(DownloadStatus.FAILED, error = "Download disappeared"))
                    return@withContext null
                }
                when (t.status) {
                    DownloadStatus.DOWNLOADING -> onProgress(
                        Progress(DownloadStatus.DOWNLOADING, t.progress, t.bytesDownloaded, t.totalBytes),
                    )
                    DownloadStatus.COMPLETED -> {
                        onProgress(Progress(DownloadStatus.COMPLETED, 1.0, t.bytesDownloaded, t.totalBytes))
                        return@withContext t.outFile
                    }
                    DownloadStatus.FAILED -> {
                        onProgress(Progress(DownloadStatus.FAILED, error = t.error ?: "Download failed"))
                        return@withContext null
                    }
                    DownloadStatus.CANCELLED -> return@withContext null
                    DownloadStatus.QUEUED -> {}
                }
                kotlinx.coroutines.delay(250)
            }
            null
        }
    }

    fun cancel() {
        cancelled = true
        lastId.get()?.let { DownloadManager.cancel(it) }
    }
}