package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.common.storage.PluginSettingsSchemaRegistry
import com.lagradost.common.storage.PluginSettingSchema

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSettingsDialog(
    pluginName: String,
    prefName: String,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val schemaUpdates by PluginSettingsSchemaRegistry.schemaUpdates.collectAsState()
    var showAddToggle by remember { mutableStateOf(false) }

    val settings = remember(schemaUpdates) {
        PluginSettingsSchemaRegistry.getSettingsForPlugin(prefName).sortedBy { it.key }
    }

    if (showAddToggle) {
        AddToggleDialog(
            prefName = prefName,
            onDismiss = { showAddToggle = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$pluginName Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (settings.isEmpty()) {
                    Text("No settings found for this plugin. You can add custom toggles to enable/disable specific sources or features.")
                } else {
                    settings.forEach { schema ->
                        SettingRow(schema = schema, prefName = prefName)
                    }
                }

                // Add custom toggle button
                OutlinedButton(
                    onClick = { showAddToggle = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("+ Add Custom Toggle")
                }
            }
        },
        confirmButton = {
            Button(onClick = onReload) {
                Text("Apply & Reload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SettingRow(schema: PluginSettingSchema, prefName: String) {
    val fullKey = if (schema.isGlobal) schema.key else schema.pluginPrefName + schema.key
    var currentValue by remember(schema, prefName) {
        mutableStateOf(
            if (schema.isGlobal) {
                com.lagradost.cloudstream3.utils.DataStore.getKey<Any>(fullKey) ?: schema.defaultValue
            } else {
                com.lagradost.common.storage.DesktopDataStore.getKey<Any>(fullKey) ?: schema.defaultValue
            }
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (schema.type) {
            "Boolean" -> {
                Text(
                    text = schema.key
                        .replace("_", " ")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = (currentValue as? Boolean) == true,
                    onCheckedChange = { newValue ->
                        currentValue = newValue
                        if (schema.isGlobal) {
                            if (newValue == null) com.lagradost.cloudstream3.utils.DataStore.removeKey(fullKey)
                            else com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, newValue)
                        } else {
                            if (newValue == null) com.lagradost.common.storage.DesktopDataStore.removeKey(fullKey)
                            else com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, newValue)
                        }
                    },
                )
            }
            "Int", "Long", "Float" -> {
                OutlinedTextField(
                    value = currentValue?.toString() ?: "",
                    onValueChange = { newValue ->
                        val parsed = when (schema.type) {
                            "Int" -> newValue.toIntOrNull()
                            "Long" -> newValue.toLongOrNull()
                            "Float" -> newValue.toFloatOrNull()
                            else -> newValue
                        }
                        if (parsed != null || newValue.isEmpty()) {
                            currentValue = parsed
                            if (schema.isGlobal) {
                                if (parsed == null) com.lagradost.cloudstream3.utils.DataStore.removeKey(fullKey)
                                else com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, parsed)
                            } else {
                                if (parsed == null) com.lagradost.common.storage.DesktopDataStore.removeKey(fullKey)
                                else com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, parsed)
                            }
                        }
                    },
                    label = { Text(schema.key) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                OutlinedTextField(
                    value = currentValue?.toString() ?: "",
                    onValueChange = { newValue ->
                        currentValue = newValue
                        if (schema.isGlobal) {
                            if (newValue == null) com.lagradost.cloudstream3.utils.DataStore.removeKey(fullKey)
                            else com.lagradost.cloudstream3.utils.DataStore.setKey(fullKey, newValue)
                        } else {
                            if (newValue == null) com.lagradost.common.storage.DesktopDataStore.removeKey(fullKey)
                            else com.lagradost.common.storage.DesktopDataStore.setKey(fullKey, newValue)
                        }
                    },
                    label = { Text(schema.key) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AddToggleDialog(
    prefName: String,
    onDismiss: () -> Unit,
) {
    var toggleName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Toggle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter a name for the toggle. This will create a switch that stores a boolean value.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = toggleName,
                    onValueChange = {
                        toggleName = it
                        error = null
                    },
                    label = { Text("Toggle Name (e.g. jio_sources)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
                Text(
                    "Tip: Use names that match what the plugin might check (e.g. the provider name)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanName = toggleName.trim()
                    if (cleanName.isEmpty()) {
                        error = "Name cannot be empty"
                        return@TextButton
                    }
                    if (!cleanName.matches(Regex("^[a-zA-Z0-9_ ]+$"))) {
                        error = "Only letters, numbers, spaces, and underscores allowed"
                        return@TextButton
                    }
                    val keyName = cleanName.replace(" ", "_")
                    // Register the custom toggle as a Boolean setting
                    PluginSettingsSchemaRegistry.register(
                        pluginPrefName = prefName,
                        key = keyName,
                        type = "Boolean",
                        defaultValue = false,
                        isGlobal = false,
                    )
                    onDismiss()
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
