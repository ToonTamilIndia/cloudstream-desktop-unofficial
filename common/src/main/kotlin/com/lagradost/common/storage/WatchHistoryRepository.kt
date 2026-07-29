package com.lagradost.common.storage

object WatchHistoryRepository {
    private const val KEY = "user_watch_history"

    fun getAll(): List<WatchHistory> =
        DesktopDataStore.getKey<List<WatchHistory>>(KEY) ?: emptyList()

    fun setLastWatched(history: WatchHistory) {
        val normalizedDuration = history.duration.coerceAtLeast(0)
        val normalizedPosition = if (normalizedDuration > 0) {
            history.position.coerceIn(0, normalizedDuration)
        } else {
            history.position.coerceAtLeast(0)
        }
        val newHistory = history.copy(
            position = normalizedPosition,
            duration = normalizedDuration,
            updateTime = System.currentTimeMillis(),
        )
        val current = getAll().toMutableList()
        current.removeAll { it.parentId == newHistory.parentId && it.episodeId == newHistory.episodeId }
        current.add(newHistory)
        DesktopDataStore.setKey(KEY, current)
    }

    fun getEpisodeWatched(parentId: String, episodeId: String?): WatchHistory? =
        getAll().find { it.parentId == parentId && it.episodeId == episodeId }

    fun removeByParent(parentId: String) {
        val current = getAll().toMutableList()
        if (current.removeAll { it.parentId == parentId }) {
            DesktopDataStore.setKey(KEY, current)
        }
    }

    fun clearAll() {
        DesktopDataStore.removeKey(KEY)
    }
}
