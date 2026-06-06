package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(availableQualities: List<String>, selectedQuality: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Quality", style = MaterialTheme.typography.labelLarge, color = DesktopUi.TextMuted)
        Spacer(modifier = Modifier.width(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedQuality == null,
                    onClick = { onSelect(null) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DesktopUi.AccentSoft,
                        selectedLabelColor = DesktopUi.Accent,
                    ),
                )
            }
            items(availableQualities) { q ->
                FilterChip(
                    selected = selectedQuality == q,
                    onClick = { onSelect(q) },
                    label = { Text(q) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DesktopUi.AccentSoft,
                        selectedLabelColor = DesktopUi.Accent,
                    ),
                )
            }
        }
    }
}
