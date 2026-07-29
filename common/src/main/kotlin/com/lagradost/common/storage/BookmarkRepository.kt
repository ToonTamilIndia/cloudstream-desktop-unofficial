package com.lagradost.common.storage

object BookmarkRepository {
    private const val KEY = "user_bookmarks"

    fun getAll(): List<DesktopBookmark> =
        DesktopDataStore.getKey<List<DesktopBookmark>>(KEY) ?: emptyList()

    fun add(bookmark: DesktopBookmark) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == bookmark.id }
        current.add(bookmark)
        DesktopDataStore.setKey(KEY, current)
    }

    fun remove(id: String) {
        val current = getAll().toMutableList()
        if (current.removeAll { it.id == id }) {
            DesktopDataStore.setKey(KEY, current)
        }
    }

    fun isBookmarked(id: String): Boolean =
        getAll().any { it.id == id }
}
