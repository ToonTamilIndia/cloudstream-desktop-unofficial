package com.lagradost.common.storage

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

data class PluginSettingSchema(
    val pluginPrefName: String,
    val key: String,
    val type: String, // "Boolean", "String", "Int", "Long", "Float", "StringSet"
    val defaultValue: Any?,
    val isGlobal: Boolean = false,
)

object PluginSettingsSchemaRegistry {
    // Map of pluginPrefName (e.g. "CineStream_") to a map of keys and their schemas
    val schemas = ConcurrentHashMap<String, ConcurrentHashMap<String, PluginSettingSchema>>()

    // Store mapping from SharedPreferences names to plugin internalNames
    // Key: SharedPreferences name (e.g. "Cricify"), Value: plugin internalName (e.g. "CricifyProvider")
    val sharedPrefNameMapping = ConcurrentHashMap<String, MutableSet<String>>()

    // Observable flow to trigger UI updates when new settings are detected
    val schemaUpdates = MutableStateFlow(0)

    fun register(pluginPrefName: String, key: String, type: String, defaultValue: Any?, isGlobal: Boolean = false) {
        val pluginMap = schemas.getOrPut(pluginPrefName) { ConcurrentHashMap() }

        // If the key is already registered with the same type, we don't need to do anything.
        // We only update if it's genuinely new to trigger a flow emission.
        val existing = pluginMap[key]
        if (existing == null || existing.type != type) {
            pluginMap[key] = PluginSettingSchema(pluginPrefName, key, type, defaultValue, isGlobal)
            schemaUpdates.value++
        }
    }

    fun getSettingsForPlugin(pluginPrefName: String): List<PluginSettingSchema> {
        return schemas[pluginPrefName]?.values?.toList() ?: emptyList()
    }

    fun hasSettings(pluginPrefName: String): Boolean {
        return schemas.containsKey(pluginPrefName) && schemas[pluginPrefName]!!.isNotEmpty()
    }

    /**
     * Find the correct pluginPrefName for a plugin by checking multiple candidates.
     * SharedPreferences-based plugins register with the SharedPreferences name (e.g. "Cricify_")
     * while the installed tab uses internalName (e.g. "CricifyProvider_"). This function
     * bridges that gap by checking all registered pref names against the plugin identity.
     */
    fun findPrefNameForPlugin(internalName: String, jarNameWithoutExt: String): String? {
        val candidates = mutableSetOf(
            "${internalName}_",
            "${jarNameWithoutExt}_",
        )

        // Also check known SharedPreferences name mappings
        for ((spName, pluginSet) in sharedPrefNameMapping) {
            if (internalName in pluginSet || jarNameWithoutExt in pluginSet) {
                candidates.add("${spName}_")
            }
        }

        // For each candidate, check if we have settings
        for (candidate in candidates) {
            if (hasSettings(candidate)) return candidate
        }

        // Broader search: iterate all registered prefNames and see if any
        // shares a common prefix with the plugin identity
        val searchTokens = setOf(internalName, jarNameWithoutExt)
        for (registeredPrefName in schemas.keys) {
            val cleanName = registeredPrefName.removeSuffix("_")
            if (searchTokens.any { token ->
                cleanName.contains(token, ignoreCase = true) || token.contains(cleanName, ignoreCase = true)
            }) {
                return registeredPrefName
            }
        }

        return null
    }

    /**
     * Record a mapping between a SharedPreferences name and a plugin identifier.
     * This bridges the gap between SharedPreferences-based settings and the internalName-based settings UI.
     */
    fun recordSharedPrefMapping(prefName: String, pluginIdentifier: String) {
        sharedPrefNameMapping.getOrPut(prefName) { ConcurrentHashMap.newKeySet() }.add(pluginIdentifier)
    }

    /**
     * Remove all settings for a given pluginPrefName
     */
    fun removePlugin(prefName: String) {
        schemas.remove(prefName)
        schemaUpdates.value++
    }
}
