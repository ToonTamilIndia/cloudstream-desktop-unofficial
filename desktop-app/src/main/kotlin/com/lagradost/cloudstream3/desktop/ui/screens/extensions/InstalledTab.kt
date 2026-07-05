package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.components.ExtensionCard
import com.lagradost.cloudstream3.desktop.ui.components.FileDropHandler
import com.lagradost.cloudstream3.desktop.ui.screens.PluginSettingsDialog
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.runtime.loader.ExtensionLoader

@Composable
fun InstalledTab(viewModel: ExtensionsViewModel, syncGeneration: Int) {
    val installedPlugins by viewModel.installedPlugins.collectAsState()
    var selectedPlugins by remember { mutableStateOf(setOf<LocalPlugin>()) }
    val remoteIcons by DesktopRepositoryManager.remotePluginIcons.collectAsState()
    val settingsGeneration by com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemaUpdates.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(syncGeneration) {
        viewModel.refreshInstalled()
    }

    val localPluginBypass by viewModel.localPluginRequiringBypass.collectAsState()
    if (localPluginBypass != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLocalPluginBypass() },
            title = { Text("Security Sandbox Warning") },
            text = {
                Text("The plugin ${localPluginBypass!!.name} uses reflection (java.lang.reflect.Method.invoke) which is blocked by the security sandbox.\n\nBypass security and load it anyway? Only do this for plugins you trust.")
            },
            confirmButton = {
                Button(onClick = { viewModel.bypassSecurityAndLoadLocalPlugin() }) {
                    Text("Bypass & Load")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLocalPluginBypass() }) {
                    Text("Cancel (Delete File)")
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm Uninstall") },
            text = { Text("Are you sure you want to uninstall ${selectedPlugins.size} plugins? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        val toDelete = selectedPlugins.toList()
                        if (toDelete.isNotEmpty()) {
                            viewModel.uninstallPlugins(toDelete)
                            selectedPlugins = emptySet()
                        }
                    },
                ) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FileDropHandler(
            enabled = true,
            onFilesDropped = { files ->
                val pluginFiles = files.filter { it.extension.lowercase() in listOf("jar", "cs3") }
                if (pluginFiles.isNotEmpty()) {
                    pluginFiles.forEach { viewModel.loadLocalPlugin(it) }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Installed Plugins (${installedPlugins.size})", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // Use Swing JFileChooser instead of AWT FileDialog to avoid GTK/libfreetype rendering bugs on Linux
                        val chooser = javax.swing.JFileChooser()
                        chooser.dialogTitle = "Load Local Plugin (.cs3 / .jar)"
                        chooser.fileFilter = object : javax.swing.filechooser.FileFilter() {
                            override fun accept(f: java.io.File): Boolean =
                                f.isDirectory || f.name.lowercase().endsWith(".cs3") || f.name.lowercase().endsWith(".jar")
                            override fun getDescription(): String = "CloudStream Plugins (*.cs3, *.jar)"
                        }
                        val result = chooser.showOpenDialog(null)
                        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                            val sourceFile = chooser.selectedFile
                            viewModel.loadLocalPlugin(sourceFile)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    Text("Load Local Plugin")
                }

                Button(
                    onClick = { showDeleteConfirm = true },
                    enabled = selectedPlugins.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Uninstall Selected (${selectedPlugins.size})")
                }
            }
        }

        val gridScale by AppearanceConfig.gridScale.collectAsState()
        val extMinSize = when (gridScale) {
            "Compact" -> 280.dp
            "Large" -> 400.dp
            else -> 340.dp
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = extMinSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(installedPlugins, key = { it.file.absolutePath }) { plugin ->
                val finalIcon = plugin.iconUrl
                    ?: remoteIcons[plugin.internalName]
                    ?: remoteIcons[plugin.name]

                val prefName = plugin.internalName + "_"
                var showDynamicSettings by remember { mutableStateOf(false) }

                val instance = remember(plugin) {
                    ExtensionLoader.getPlugin(plugin.file.absolutePath) as? com.lagradost.cloudstream3.plugins.Plugin
                }

                // Find the correct prefName - plugins using SharedPreferences may register
                // under a different name than internalName (e.g. "Cricify_" vs "CricifyProvider_")
                val jarNameNoExt = plugin.file.nameWithoutExtension.removeSuffix("-jvm")
                val resolvedPrefName = remember(plugin, settingsGeneration) {
                    val registry = com.lagradost.common.storage.PluginSettingsSchemaRegistry
                    if (registry.hasSettings(prefName)) {
                        prefName
                    } else {
                        registry.findPrefNameForPlugin(plugin.internalName, jarNameNoExt) ?: prefName
                    }
                }
                val hasSchemaSettings = com.lagradost.common.storage.PluginSettingsSchemaRegistry.hasSettings(resolvedPrefName)
                val showSettings = instance?.openSettings != null || hasSchemaSettings

                ExtensionCard(
                    name = plugin.name,
                    internalName = plugin.internalName,
                    version = plugin.version,
                    repoName = plugin.repoName,
                    language = plugin.language,
                    tvTypes = plugin.tvTypes,
                    iconUrl = finalIcon,
                    isInstalled = true,
                    installStatus = "Installed",
                    isInstalling = false,
                    onInstallClick = { },
                    showCheckbox = true,
                    isChecked = selectedPlugins.contains(plugin),
                    onCheckedChange = { isChecked ->
                        selectedPlugins = if (isChecked) {
                            selectedPlugins + plugin
                        } else {
                            selectedPlugins - plugin
                        }
                    },
                    showSettings = showSettings,
                    onSettingsClick = {
                        if (hasSchemaSettings) {
                            showDynamicSettings = true
                        } else if (instance?.openSettings != null) {
                            // Invoke the plugin's own settings handler with desktop context
                            try {
                                instance.openSettings?.invoke(android.content.DesktopContextProvider.context)
                            } catch (t: Throwable) {
                                com.lagradost.common.logging.AppLogger.e("Failed to invoke plugin settings", t)
                                // Fall back to dynamic settings dialog even if no settings were auto-discovered
                                showDynamicSettings = true
                            }
                        } else {
                            // No settings at all - still show the dynamic dialog so users can
                            // create custom toggles for this plugin
                            showDynamicSettings = true
                        }
                    }
                )

                if (showDynamicSettings) {
                    PluginSettingsDialog(
                        pluginName = plugin.name,
                        prefName = resolvedPrefName,
                        onReload = {
                            showDynamicSettings = false
                            viewModel.reloadPlugin(plugin) { }
                        },
                        onDismiss = { showDynamicSettings = false },
                    )
                }
            }
        }
    }
}
