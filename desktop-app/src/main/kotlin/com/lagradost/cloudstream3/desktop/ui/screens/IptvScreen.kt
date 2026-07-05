package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.iptv.IptvChannel
import com.lagradost.cloudstream3.desktop.iptv.IptvParser
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeIptvScreen() {
    var m3uUrl by remember { mutableStateOf(DesktopDataStore.getKey<String>("iptv_m3u_url") ?: "") }
    var channels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    val playVideo = LocalVideoPlayer.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(m3uUrl) {
        if (m3uUrl.isNotBlank()) {
            isLoading = true
            statusText = "Loading channels..."
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL(m3uUrl)
                    val conn = url.openConnection()
                    conn.setRequestProperty("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
                    val content = conn.inputStream.bufferedReader().readText()
                    val parsed = IptvParser.parseM3u(content)
                    Result.success(parsed)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            if (result.isSuccess) {
                channels = result.getOrDefault(emptyList())
                statusText = "Loaded ${channels.size} channels"
            } else {
                statusText = "Error: ${result.exceptionOrNull()?.message}"
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "IPTV Channels",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = DesktopUi.TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = m3uUrl,
                onValueChange = {
                    m3uUrl = it
                    DesktopDataStore.setKey("iptv_m3u_url", it)
                },
                label = { Text("M3U Playlist URL") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    channels = emptyList()
                    statusText = ""
                    // Re-trigger Load by forcing recomposition via state change
                    DesktopDataStore.setKey("iptv_m3u_url", m3uUrl)
                    // The LaunchedEffect key is m3uUrl, so it re-triggers if url changed
                    // Force trigger by toggling
                },
                enabled = m3uUrl.isNotBlank() && !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Load")
                }
            }
        }

        if (statusText.isNotBlank()) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = DesktopUi.TextMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (channels.isNotEmpty()) {
            Text(
                "${channels.size} channels",
                style = MaterialTheme.typography.titleSmall,
                color = DesktopUi.TextMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val groups = channels.groupBy { it.group ?: "Ungrouped" }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                groups.forEach { (group, groupChannels) ->
                    item {
                        Text(
                            group,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DesktopUi.Accent,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(groupChannels, key = { "${it.url}|${it.name}" }) { channel ->
                        IptvChannelCard(
                            channel = channel,
                            onPlay = {
                                val link = ExtractorLink(
                                    source = "IPTV",
                                    name = channel.name,
                                    url = channel.url,
                                    referer = "",
                                    quality = 1080,
                                    type = ExtractorLinkType.M3U8,
                                )
                                playVideo(
                                    VideoLaunchData(
                                        links = listOf(link),
                                        initialIndex = 0,
                                        title = channel.name,
                                        subtitles = emptyList(),
                                        startPositionMs = 0,
                                        history = WatchHistory(
                                            parentId = "iptv_${channel.url.hashCode()}",
                                            showName = channel.name,
                                            showUrl = channel.url,
                                            apiName = "IPTV",
                                            posterUrl = channel.logo,
                                            episode = null,
                                            season = null,
                                            episodeId = null,
                                            position = 0,
                                            duration = 0,
                                        ),
                                    )
                                )
                            },
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun IptvChannelCard(
    channel: IptvChannel,
    onPlay: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = DesktopUi.SurfaceCard,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = DesktopUi.TextPrimary,
                )
                if (channel.group != null) {
                    Text(
                        channel.group,
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopUi.TextMuted,
                    )
                }
            }
            Button(
                onClick = onPlay,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.Accent),
            ) {
                Text("Play")
            }
        }
    }
}
