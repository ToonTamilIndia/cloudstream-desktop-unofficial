package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.iptv.IptvChannel
import com.lagradost.cloudstream3.desktop.iptv.IptvParser
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeIptvScreen() {
    var m3uUrl by remember { mutableStateOf(DesktopDataStore.getKey<String>("iptv_m3u_url") ?: "") }
    var groups by remember { mutableStateOf<Map<String, List<IptvChannel>>>(emptyMap()) }
    var totalChannels by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val playVideo = LocalVideoPlayer.current
    val coroutineScope = rememberCoroutineScope()

    val loadPlaylist = { url: String ->
        if (url.isNotBlank()) {
            isLoading = true
            statusText = "Downloading playlist..."
            selectedGroup = null
            groups = emptyMap()
            totalChannels = 0
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val conn = java.net.URI(url).toURL().openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
                    conn.connectTimeout = 30000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true

                    val totalSize = conn.contentLengthLong
                    val inputStream: InputStream = conn.inputStream

                    val channelCount = java.util.concurrent.atomic.AtomicInteger(0)
                    val buffer = mutableListOf<IptvChannel>()
                    val groupMap = mutableMapOf<String, MutableList<IptvChannel>>()
                    val groupOrder = mutableListOf<String>()

                    IptvParser.parseStream(inputStream) { channel ->
                        val idx = channelCount.incrementAndGet()
                        if (idx % 5000 == 0) {
                            statusText = if (totalSize > 0) {
                                "Loaded $idx channels..."
                            } else {
                                "Loaded $idx channels..."
                            }
                        }
                        val groupName = channel.group ?: "Ungrouped"
                        synchronized(groupMap) {
                            if (groupMap.containsKey(groupName)) {
                                groupMap[groupName]!!.add(channel)
                            } else {
                                groupMap[groupName] = mutableListOf(channel)
                                groupOrder.add(groupName)
                            }
                        }
                    }

                    inputStream.close()
                    conn.disconnect()

                    val finalMap = groupOrder.associateWith { groupMap[it] ?: emptyList() }
                    withContext(Dispatchers.Main) {
                        groups = finalMap
                        totalChannels = channelCount.get()
                        statusText = "Loaded ${channelCount.get()} channels in ${finalMap.size} groups"
                        isLoading = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText = "Error: ${e.message}"
                        isLoading = false
                    }
                }
            }
        }
    }

    LaunchedEffect(m3uUrl) {
        loadPlaylist(m3uUrl)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                modifier = Modifier.weight(1f),
                placeholder = { Text("M3U Playlist URL") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { loadPlaylist(m3uUrl) },
                enabled = m3uUrl.isNotBlank() && !isLoading,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Load")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (statusText.isNotBlank()) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = DesktopUi.TextMuted,
            )
        }

        if (groups.isNotEmpty() && selectedGroup == null) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search channels or groups...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            val filteredGroups = remember(groups, searchQuery) {
                if (searchQuery.isBlank()) groups
                else groups.filter { (groupName, channels) ->
                    groupName.contains(searchQuery, ignoreCase = true) ||
                        channels.any { it.name.contains(searchQuery, ignoreCase = true) }
                }
            }

            Text(
                "${filteredGroups.size} groups • $totalChannels channels",
                style = MaterialTheme.typography.titleSmall,
                color = DesktopUi.TextMuted,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val gridScale by AppearanceConfig.gridScale.collectAsState()
            val cardMinSize = when (gridScale) {
                "Compact" -> 280.dp
                "Large" -> 400.dp
                else -> 340.dp
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = cardMinSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filteredGroups.toList(), key = { "${it.first}|${it.second.size}" }) { (groupName, groupChannels) ->
                    GroupCard(
                        groupName = groupName,
                        channelCount = groupChannels.size,
                        channels = groupChannels,
                        onViewAll = { selectedGroup = groupName },
                        onPlay = { channel ->
                            playChannel(channel, playVideo)
                        },
                    )
                }
            }
        }

        selectedGroup?.let { groupName ->
            val channels = groups[groupName] ?: emptyList()
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        selectedGroup = null
                        searchQuery = ""
                    }) {
                        Text("< Back to groups", color = DesktopUi.Accent)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "$groupName (${
                            if (searchQuery.isBlank()) channels.size
                            else channels.count { it.name.contains(searchQuery, ignoreCase = true) }
                        })",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DesktopUi.TextPrimary,
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search in $groupName...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                val filteredChannels = remember(channels, searchQuery) {
                    if (searchQuery.isBlank()) channels
                    else channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }

                val gridScale by AppearanceConfig.gridScale.collectAsState()
                val cardMinSize = when (gridScale) {
                    "Compact" -> 280.dp
                    "Large" -> 400.dp
                    else -> 340.dp
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = cardMinSize),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredChannels, key = { "${it.url}|${it.name}" }) { channel ->
                        ChannelCard(
                            channel = channel,
                            onPlay = { playChannel(channel, playVideo) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    groupName: String,
    channelCount: Int,
    channels: List<IptvChannel>,
    onViewAll: () -> Unit,
    onPlay: (IptvChannel) -> Unit,
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        groupName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = DesktopUi.TextPrimary,
                    )
                    Text(
                        "$channelCount channel${if (channelCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopUi.TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onViewAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View All")
                }
            }

            if (channelCount <= 5) {
                Spacer(modifier = Modifier.height(8.dp))
                channels.take(5).forEach { channel ->
                    ChannelMiniRow(channel = channel, onPlay = { onPlay(channel) })
                    if (channel != channels.take(5).last()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelMiniRow(channel: IptvChannel, onPlay: () -> Unit) {
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = DesktopUi.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChannelCard(
    channel: IptvChannel,
    onPlay: () -> Unit,
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!channel.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .padding(end = 16.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(end = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = DesktopUi.TextMuted,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = DesktopUi.TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                channel.group?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopUi.Accent,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onPlay,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Play", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun playChannel(channel: IptvChannel, playVideo: (VideoLaunchData) -> Unit) {
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
}
