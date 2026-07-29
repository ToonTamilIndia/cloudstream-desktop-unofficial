package com.lagradost.common.storage

object PluginUpdateRepository {
    private const val HISTORY_KEY = "plugin_updates_history_v2"
    private const val UNREAD_KEY = "unread_plugin_updates"

    fun getAll(): List<PluginUpdateRecord> =
        DesktopDataStore.getKey<List<PluginUpdateRecord>>(HISTORY_KEY) ?: emptyList()

    fun addAll(history: List<PluginUpdateRecord>) {
        if (history.isEmpty()) return
        val consolidated = history.sortedByDescending { it.timestamp }
            .distinctBy { it.pluginName }
        val current = getAll().toMutableList()
        val incomingNames = consolidated.map { it.pluginName }.toSet()
        current.removeAll { it.pluginName in incomingNames }
        current.addAll(0, consolidated)
        current.sortByDescending { it.timestamp }
        if (current.size > 50) current.subList(50, current.size).clear()
        DesktopDataStore.setKey(HISTORY_KEY, current)
    }

    fun hasUnread(): Boolean = DesktopDataStore.getKey<Boolean>(UNREAD_KEY) ?: false

    fun setUnread(value: Boolean) = DesktopDataStore.setKey(UNREAD_KEY, value)
}
