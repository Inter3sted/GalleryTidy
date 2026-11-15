package com.example.gallerytidy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gallerytidy.data.Thresholds
import com.example.gallerytidy.util.formatBytes
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: UiState,
    partialAccess: Boolean,
    onGrantFull: () -> Unit,
    onOpen: (Category) -> Unit,
    onRescan: () -> Unit,
    onThresholds: (Thresholds) -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Gallery Tidy") }) }) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (partialAccess) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("You've shared only some photos with this app, so suggestions cover just those.")
                        TextButton(onClick = onGrantFull) { Text("Allow access to all photos") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Photos", state.totalPhotos.toString(), Modifier.weight(1f))
                Stat("Can free", formatBytes(state.reclaimableBytes), Modifier.weight(1f))
                Stat("Freed", formatBytes(state.freedBytes), Modifier.weight(1f))
            }

            if (state.progress.running) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Analysing ${state.progress.done} / ${state.progress.total}", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(
                        progress = { state.progress.done / state.progress.total.coerceAtLeast(1).toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            CategoryCard("Blurry photos", state.blurry.size, "Low sharpness") { onOpen(Category.BLURRY) }
            CategoryCard("Similar shots", state.duplicateGroups.sumOf { it.extras.size }, "${state.duplicateGroups.size} groups, best shot kept") { onOpen(Category.DUPLICATES) }
            CategoryCard("Too dark", state.dark.size, "Underexposed") { onOpen(Category.DARK) }
            CategoryCard("Overexposed", state.bright.size, "Blown-out highlights") { onOpen(Category.BRIGHT) }

            Text("Sensitivity", style = MaterialTheme.typography.titleMedium)
            val t = state.thresholds
            LabeledSlider("Blur: flag below ${t.blur.roundToInt()}", t.blur, 10f..300f) { onThresholds(t.copy(blur = it)) }
            LabeledSlider("Dark: flag below ${t.dark.roundToInt()}", t.dark, 5f..80f) { onThresholds(t.copy(dark = it)) }
            LabeledSlider("Bright: flag above ${t.bright.roundToInt()}", t.bright, 170f..250f) { onThresholds(t.copy(bright = it)) }
            LabeledSlider("Similarity: up to ${t.duplicateDistance} bits differ", t.duplicateDistance.toFloat(), 1f..14f) {
                onThresholds(t.copy(duplicateDistance = it.roundToInt()))
            }

            OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) { Text("Rescan library") }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CategoryCard(title: String, count: Int, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = count > 0, onClick = onClick)) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
