package com.lagradost.server.utils

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.player.impl.proxy.LocalStreamProxy

/**
 * Shared state access between desktop and server modules
 */
object ServerAppState {
    /**
     * Get all available sources
     */
    fun getSources() = APIHolder.allProviders.toList()

    /**
     * Get source by name
     */
    fun getSource(name: String) = APIHolder.getApiFromNameNull(name)

    /**
     * Get source by URL
     */
    fun getSourceByUrl(url: String) = APIHolder.getApiFromUrlNull(url)

    /**
     * Get proxy port
     */
    fun getProxyPort() = LocalStreamProxy.port

    /**
     * Get library data
     */
    fun getLibrary(): String {
        return DesktopDataStore.getKey<String>("sync_store") ?: "{}"
    }

    /**
     * Update library data
     */
    fun updateLibrary(json: String) {
        DesktopDataStore.setKey("sync_store", json)
    }

    /**
     * Get settings
     */
    fun getSetting(key: String): String? {
        return DesktopDataStore.getKey(key)
    }

    /**
     * Update settings
     */
    fun setSetting(key: String, value: String) {
        DesktopDataStore.setKey(key, value)
    }
}