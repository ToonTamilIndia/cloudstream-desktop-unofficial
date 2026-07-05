package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.lagradost.cloudstream3.desktop.ui.LocalAwtWindow
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File

@Composable
fun FileDropHandler(
    enabled: Boolean = true,
    onFilesDropped: (List<File>) -> Unit,
) {
    val awtWindow = LocalAwtWindow.current

    DisposableEffect(awtWindow, enabled) {
        if (!enabled || awtWindow == null) return@DisposableEffect onDispose {}

        val dropTargetListener = object : DropTargetAdapter() {
            override fun drop(event: DropTargetDropEvent) {
                event.acceptDrop(DnDConstants.ACTION_COPY)
                try {
                    val transferable = event.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        if (files.isNotEmpty()) {
                            onFilesDropped(files)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore invalid drops
                } finally {
                    event.dropComplete(true)
                }
            }
        }

        val dropTarget = DropTarget(
            awtWindow,
            DnDConstants.ACTION_COPY,
            dropTargetListener,
            true,
        )

        onDispose {
            awtWindow.dropTarget = null
        }
    }
}
