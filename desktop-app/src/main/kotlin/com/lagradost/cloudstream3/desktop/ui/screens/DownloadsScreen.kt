package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.common.download.DownloadManager
import com.lagradost.common.download.DownloadStatus
import com.lagradost.common.download.DownloadTask
import com.lagradost.common.platform.PlatformPaths
import java.io.File

@Composable
fun ComposeDownloadsScreen() {
    var tasks by remember { mutableStateOf<List<DownloadTask>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            tasks = DownloadManager.list()
            kotlinx.coroutines.delay(500)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DesktopUi.TextPrimary,
                )
                Text(
                    "${tasks.size} task${if (tasks.size == 1) "" else "s"} · ${PlatformPaths.downloadsDir.absolutePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopUi.TextMuted,
                )
            }
            Button(
                onClick = {
                    try {
                        java.awt.Desktop.getDesktop().open(PlatformPaths.downloadsDir)
                    } catch (_: Exception) {}
                },
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Folder")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = DesktopUi.TextMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No downloads yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopUi.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Start a download from any stream link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DesktopUi.TextMuted,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskCard(task = task, onCancel = { DownloadManager.cancel(task.id) })
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(task: DownloadTask, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DesktopUi.SurfaceCard,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = DesktopUi.TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    statusLine(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(task),
                )
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { task.progress.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = DesktopUi.Accent,
                    )
                }
            }

            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel")
                }
            } else if (task.status == DownloadStatus.COMPLETED) {
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            java.awt.Desktop.getDesktop().open(task.outFile)
                        } catch (_: Exception) {
                            try {
                                java.awt.Desktop.getDesktop().open(File(task.outFile.parentFile?.absolutePath ?: ""))
                            } catch (_: Exception) {}
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open")
                }
            }
        }
    }
}

private fun statusLine(task: DownloadTask): String {
    return when (task.status) {
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.DOWNLOADING -> {
            val pct = (task.progress * 100).toInt()
            if (task.totalBytes > 0) {
                "$pct% · ${formatBytes(task.bytesDownloaded)} / ${formatBytes(task.totalBytes)}"
            } else {
                "$pct% · ${formatBytes(task.bytesDownloaded)}"
            }
        }
        DownloadStatus.COMPLETED -> "Completed · ${formatBytes(task.outFile.length())}"
        DownloadStatus.FAILED -> "Failed: ${task.error ?: "unknown error"}"
        DownloadStatus.CANCELLED -> "Cancelled"
    }
}

@Composable
private fun statusColor(task: DownloadTask): androidx.compose.ui.graphics.Color {
    return when (task.status) {
        DownloadStatus.COMPLETED -> DesktopUi.Accent
        DownloadStatus.FAILED -> androidx.compose.ui.graphics.Color(0xFFFF8A8A)
        else -> DesktopUi.TextMuted
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    if (gb < 1024) return String.format("%.2f GB", gb)
    return String.format("%.2f TB", gb / 1024.0)
}
