package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.details.*
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeDetailsScreen(navController: NavController, provider: MainAPI, url: String, preloadedName: String? = null, preloadedPoster: String? = null, preloadedBg: String? = null) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember(url) { DetailsViewModel(coroutineScope, provider, url, preloadedName, preloadedPoster, preloadedBg) }

    val response by viewModel.response.collectAsState()
    val fakeData by viewModel.fakeData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val activeLinkData by viewModel.activeLinkData.collectAsState()
    val isPanelOpen by viewModel.isPanelOpen.collectAsState()
    val enrichmentTrigger by viewModel.enrichmentTrigger.collectAsState()

        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Details Content
                if (isLoading) {
                    if (fakeData != null) {
                        DetailsContent(navController, provider, fakeData!!, enrichmentTrigger, isLoading = true, onPlay = viewModel::openLinksPanel)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (response != null) {
                    DetailsContent(navController, provider, response!!, enrichmentTrigger, isLoading = false, onPlay = viewModel::openLinksPanel)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (errorMessage != null) "Error: $errorMessage" else "Failed to load details.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { navController.goBack() }) {
                                Text("Go Back")
                            }
                        }
                    }
                }

                // 2. Dim Overlay
                AnimatedVisibility(
                    visible = isPanelOpen,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { viewModel.closeLinksPanel() },
                    )
                }

                // 3. Side Panel with Links
                if (activeLinkData != null) {
                    val offsetX by animateDpAsState(
                        targetValue = if (isPanelOpen) 0.dp else 450.dp,
                        animationSpec = tween(300),
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = offsetX),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                                .clickable { if (isPanelOpen) viewModel.closeLinksPanel() else viewModel.openLinksPanel(activeLinkData!!) }
                                .padding(16.dp),
                        ) {
                            Icon(
                                if (isPanelOpen) Icons.Default.Close else Icons.Default.Menu,
                                contentDescription = "Toggle links",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(450.dp)
                                .shadow(24.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF0C0C14).copy(alpha = 0.75f),
                                            Color(0xFF1A1A24).copy(alpha = 0.85f),
                                        ),
                                    ),
                                ),
                        ) {
                            activeLinkData?.let { (linkProvider, linkUrl, linkHistory) ->
                                LinksSidePanel(
                                    provider = linkProvider,
                                    dataUrl = linkUrl,
                                    history = linkHistory,
                                    onClose = { viewModel.closeLinksPanel() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun DetailsContent(navController: NavController, provider: MainAPI, data: LoadResponse, enrichmentTrigger: Int, isLoading: Boolean = false, onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hazeState = remember { HazeState() }

    val historyUpdatesVal = com.lagradost.common.storage.DesktopDataStore.historyUpdates.collectAsState().value
    val latestHistory = remember(data.url, historyUpdatesVal) {
        com.lagradost.common.storage.DesktopDataStore.getLatestWatchHistoryForShow(data.url)
    }

    val dubStatuses = remember(data) { if (data is AnimeLoadResponse) data.episodes.keys.toList() else emptyList() }
    var selectedDub by remember(latestHistory?.episodeId, data) {
        mutableStateOf(
            if (data is AnimeLoadResponse) {
                if (latestHistory != null) {
                    dubStatuses.find { dub -> data.episodes[dub]?.any { it.data == latestHistory.episodeId } == true } ?: dubStatuses.firstOrNull()
                } else {
                    dubStatuses.firstOrNull()
                }
            } else {
                null
            },
        )
    }

    val seasons = remember(data) { if (data is TvSeriesLoadResponse) data.episodes.mapNotNull { it.season }.distinct().sorted() else emptyList() }
    var selectedSeason by remember(latestHistory?.season, data) {
        mutableStateOf(if (data is TvSeriesLoadResponse) latestHistory?.season ?: seasons.firstOrNull() ?: 1 else 1)
    }

    val showHistory = remember(data.url, historyUpdatesVal) {
        com.lagradost.common.storage.DesktopDataStore.getAllWatchHistory()
            .filter { it.showUrl == data.url }
            .associateBy { it.episodeId ?: it.parentId }
    }

    var isSortAscending by remember(data.url) { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    DetailsBackdrop(provider = provider, data = data, scrollState = scrollState, hazeState = hazeState)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 2.0f))
                        DetailsMetadata(provider = provider, data = data, hazeState = hazeState)
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(modifier = Modifier.widthIn(max = 850.dp).padding(horizontal = 16.dp)) {
                            when (data) {
                                is MovieLoadResponse, is TorrentLoadResponse -> {
                                    if (latestHistory != null && latestHistory.duration > 0) {
                                        com.lagradost.cloudstream3.desktop.ui.components.WatchProgressIndicator(
                                            position = latestHistory.position,
                                            duration = latestHistory.duration,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    Button(
                                        onClick = {
                                            val url = if (data is TorrentLoadResponse) (data.torrent ?: data.magnet ?: "") else (data as MovieLoadResponse).dataUrl
                                            val ep = provider.newEpisode(url) {
                                                name = data.name
                                                posterUrl = data.posterUrl
                                            }
                                            navigateToPlay(provider, data, ep, onPlay)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val canResume = latestHistory != null &&
                                            PlayerLinkHandler.resumeStartSeconds(latestHistory.position, latestHistory.duration) > 0
                                        val typeName = if (data is TorrentLoadResponse) "Torrent" else "Movie"
                                        Text(if (canResume) "Resume $typeName" else "Play $typeName", fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                                is LiveStreamLoadResponse -> {
                                    Button(
                                        onClick = {
                                            val ep = provider.newEpisode(data.dataUrl) {
                                                name = data.name
                                                posterUrl = data.posterUrl
                                            }
                                            navigateToPlay(provider, data, ep, onPlay)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Watch Live", fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                                is TvSeriesLoadResponse -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (seasons.isNotEmpty()) {
                                                ScrollableTabRow(
                                                    selectedTabIndex = seasons.indexOf(selectedSeason).coerceAtLeast(0),
                                                    containerColor = Color.Transparent,
                                                    edgePadding = 0.dp,
                                                    divider = {},
                                                ) {
                                                    seasons.forEach { season ->
                                                        Tab(
                                                            selected = selectedSeason == season,
                                                            onClick = { selectedSeason = season },
                                                            text = { Text("Season $season", fontWeight = FontWeight.Bold) },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        TextButton(onClick = { isSortAscending = !isSortAscending }) {
                                            Text(
                                                text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                is AnimeLoadResponse -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (dubStatuses.size > 1) {
                                                TabRow(
                                                    selectedTabIndex = dubStatuses.indexOf(selectedDub).coerceAtLeast(0),
                                                    containerColor = Color.Transparent,
                                                    divider = {},
                                                ) {
                                                    dubStatuses.forEach { dub ->
                                                        Tab(
                                                            selected = selectedDub == dub,
                                                            onClick = { selectedDub = dub },
                                                            text = { Text(dub.name, fontWeight = FontWeight.Bold) },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        TextButton(onClick = { isSortAscending = !isSortAscending }) {
                                            Text(
                                                text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }

                if (data is TvSeriesLoadResponse) {
                    val filteredEpisodes = data.episodes
                        .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        .let { list ->
                            if (isSortAscending) list.sortedBy { it.episode ?: Int.MAX_VALUE }
                            else list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                        }
                        
                    if (filteredEpisodes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Coming Soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Episodes are not available yet. Please check back later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(filteredEpisodes) { ep ->
                            val isLatest = latestHistory != null && latestHistory.episodeId == ep.data
                            val history = showHistory.values.find { it.episodeId == ep.data }
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                Box(modifier = Modifier.widthIn(max = 850.dp).padding(horizontal = 16.dp)) {
                                    EpisodeCard(ep, isLatest, history, provider, data, onPlay)
                                }
                            }
                        }
                    }
                } else if (data is AnimeLoadResponse) {
                    val filteredEpisodes = (selectedDub?.let { data.episodes[it] } ?: emptyList())
                        .let { list ->
                            if (isSortAscending) list.sortedBy { it.episode ?: Int.MAX_VALUE }
                            else list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                        }
                        
                    if (filteredEpisodes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Coming Soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Episodes are not available yet. Please check back later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(filteredEpisodes) { ep ->
                            val isLatest = latestHistory != null && latestHistory.episodeId == ep.data
                            val history = showHistory.values.find { it.episodeId == ep.data }
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                Box(modifier = Modifier.widthIn(max = 850.dp).padding(horizontal = 16.dp)) {
                                    EpisodeCard(ep, isLatest, history, provider, data, onPlay)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── Back button ──
        IconButton(
            onClick = { navController.goBack() },
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

