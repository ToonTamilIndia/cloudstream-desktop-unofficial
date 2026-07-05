package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap

object GlobalDetailsCache {
    // Size-limited LRU Cache for the last 50 visited pages to prevent OutOfMemory errors
    val cache: MutableMap<String, LoadResponse> = Collections.synchronizedMap(
        object : LinkedHashMap<String, LoadResponse>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoadResponse>?): Boolean {
                return size > 50
            }
        }
    )

    suspend fun fetchRaw(provider: MainAPI, url: String): LoadResponse? {
        val existing = cache[url]
        if (existing != null) return existing

        val loaded = withContext(Dispatchers.IO) {
            try {
                provider.load(url)
            } catch (e: Exception) {
                null
            }
        }
        
        if (loaded != null) {
            cache[url] = loaded
        }
        return loaded
    }

    suspend fun enrich(loaded: LoadResponse, url: String) {
        withContext(Dispatchers.IO) {
            TitleMetadataEnricher.enrich(loaded)
        }
        cache[url] = loaded
    }
}

class DetailsViewModel(
    private val viewModelScope: CoroutineScope,
    private val provider: MainAPI,
    private val url: String,
    private val preloadedName: String? = null,
    private val preloadedPoster: String? = null,
    private val preloadedBg: String? = null,
) {

    private val _response = MutableStateFlow<LoadResponse?>(GlobalDetailsCache.cache[url])
    val response: StateFlow<LoadResponse?> = _response.asStateFlow()

    private val _enrichmentTrigger = MutableStateFlow(0)
    val enrichmentTrigger: StateFlow<Int> = _enrichmentTrigger.asStateFlow()

    private val _fakeData = MutableStateFlow<LoadResponse?>(null)
    val fakeData: StateFlow<LoadResponse?> = _fakeData.asStateFlow()

    private val _isLoading = MutableStateFlow(_response.value == null)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activeLinkData = MutableStateFlow<Triple<MainAPI, String, WatchHistory>?>(null)
    val activeLinkData: StateFlow<Triple<MainAPI, String, WatchHistory>?> = _activeLinkData.asStateFlow()

    private val _isPanelOpen = MutableStateFlow(false)
    val isPanelOpen: StateFlow<Boolean> = _isPanelOpen.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        if (_response.value != null) return

        viewModelScope.launch {
            if (preloadedName != null) {
                _fakeData.value = provider.newMovieLoadResponse(
                    name = preloadedName,
                    url = url,
                    type = TvType.Movie,
                    dataUrl = url,
                ) {
                    this.posterUrl = preloadedPoster
                    this.backgroundPosterUrl = preloadedBg
                }
            }

            try {
                val rawData = GlobalDetailsCache.fetchRaw(provider, url)
                _response.value = rawData
                _isLoading.value = false
                
                if (rawData != null) {
                    viewModelScope.launch {
                        GlobalDetailsCache.enrich(rawData, url)
                        // Trigger a recomposition since the rawData object is mutated in-place
                        _enrichmentTrigger.value += 1
                    }
                }
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading details", e)
                _errorMessage.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun openLinksPanel(data: Triple<MainAPI, String, WatchHistory>) {
        _activeLinkData.value = data
        _isPanelOpen.value = true
    }

    fun closeLinksPanel() {
        _isPanelOpen.value = false
    }
}
