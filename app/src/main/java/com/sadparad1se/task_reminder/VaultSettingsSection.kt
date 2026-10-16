package com.sadparad1se.task_reminder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Adds the selected vault list section to the settings screen. */
internal fun LazyListScope.vaultSection(
    selectedVaults: List<VaultSelection>,
    onRemoveVault: (String) -> Unit
) {
    item { SettingsSectionTitle("Obsidian vaults") }

    if (selectedVaults.isEmpty()) {
        item {
            Text(
                text = "No vaults selected yet.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        items(selectedVaults) { vault ->
            VaultRow(vault, onRemoveVault)
        }
    }
}

/** Displays one selected vault and lets the user remove its stored data. */
@Composable
private fun VaultRow(
    vault: VaultSelection,
    onRemoveVault: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = vault.displayName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    onRemoveVault(vault.uri.toString())
                }
            ) {
                Text("Remove")
            }
        }
    }
}

/** Displays vault-level actions side by side. */
@Composable
internal fun VaultActionRow(
    scanEnabled: Boolean,
    onAddVault: () -> Unit,
    onScanNow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onAddVault,
            modifier = Modifier.weight(1f)
        ) {
            Text("Add Vault")
        }
        Button(
            onClick = onScanNow,
            enabled = scanEnabled,
            modifier = Modifier.weight(1f)
        ) {
            Text("Scan now")
        }
    }
}

/** Adds current scan statuses to the settings screen. */
internal fun LazyListScope.scanStatusSection(
    selectedVaults: List<VaultSelection>,
    scanStates: List<VaultScanState>
) {
    item {
        ScanStatusList(
            vaults = selectedVaults,
            scanStates = scanStates
        )
    }
}

/** Displays per-vault scan status messages. */
@Composable
private fun ScanStatusList(
    vaults: List<VaultSelection>,
    scanStates: List<VaultScanState>
) {
    if (vaults.isEmpty()) return

    val scanStateByVault = scanStates.associateBy { it.vaultUri }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        vaults.forEach { vault ->
            val scanState = scanStateByVault[vault.uri.toString()]
            Text(
                text = "${vault.displayName}: ${formatScanStatus(scanState)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Adds scan frequency choices to the settings screen. */
internal fun LazyListScope.scanFrequencySection(
    selectedFrequency: ScanFrequency,
    onFrequencySelected: (ScanFrequency) -> Unit
) {
    val frequencies = ScanFrequency.entries.toList()
    item { SettingsSectionTitle("Scan frequency") }
    item {
        ScanFrequencySlider(
            frequencies = frequencies,
            selectedFrequency = selectedFrequency,
            onFrequencySelected = onFrequencySelected
        )
    }
}

/** Displays scan frequency choices as a slider and persists only after release. */
@Composable
private fun ScanFrequencySlider(
    frequencies: List<ScanFrequency>,
    selectedFrequency: ScanFrequency,
    onFrequencySelected: (ScanFrequency) -> Unit
) {
    val selectedIndex = frequencies.indexOf(selectedFrequency).coerceAtLeast(0)
    var sliderValue by remember(selectedFrequency) {
        mutableFloatStateOf(selectedIndex.toFloat())
    }
    val selectedSliderIndex = sliderValue
        .roundToInt()
        .coerceIn(0, frequencies.lastIndex)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Scan every: ${frequencies[selectedSliderIndex].label}",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = 0f..frequencies.lastIndex.toFloat(),
            steps = frequencies.size - 2,
            onValueChangeFinished = {
                onFrequencySelected(frequencies[selectedSliderIndex])
            }
        )
    }
}
