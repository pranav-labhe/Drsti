package com.pranav.drsti.ui.screen.transit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pranav.drsti.model.PlanetPosition
import com.pranav.drsti.model.TransitHighlight
import com.pranav.drsti.ui.PreviewSamples
import com.pranav.drsti.ui.theme.DrshtiTheme
import com.pranav.drsti.ui.viewmodel.TransitViewModel

@Composable
fun TransitScreen(viewModel: TransitViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Right Now", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { viewModel.refresh(forceFetch = true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        when {
            state.isLoading && state.positions == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Could not load transits: ${state.error}", textAlign = TextAlign.Center)
            }
            state.positions != null -> TransitContent(
                positions = state.positions!!.positions,
                highlights = state.analysis?.highlights.orEmpty(),
                favorable = state.analysis?.favorableThemes.orEmpty(),
                caution = state.analysis?.cautionThemes.orEmpty(),
                summary = state.analysis?.summary,
                fromCache = state.fromCache
            )
        }
    }
}

@Composable
private fun TransitContent(
    positions: List<PlanetPosition>,
    highlights: List<TransitHighlight>,
    favorable: List<String>,
    caution: List<String>,
    summary: String?,
    fromCache: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (fromCache) {
                Text(
                    "Using recently cached positions.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            summary?.let { 
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) 
            }
            Spacer(Modifier.height(4.dp))
        }

        if (highlights.isNotEmpty()) {
            item { Text("Notable Transits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(highlights) { h ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Text(h.note, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (favorable.isNotEmpty()) {
            item { Text("Favorable Themes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            items(favorable) { Text("\u2022 $it", style = MaterialTheme.typography.bodySmall) }
        }

        if (caution.isNotEmpty()) {
            item { Text("Worth Noting", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
            items(caution) { Text("\u2022 $it", style = MaterialTheme.typography.bodySmall) }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Current Positions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        items(positions, key = { it.planet }) { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(p.planet.displayName(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${p.sign.displayName} ${"%.1f".format(p.degreeInSign)}\u00B0" + if (p.isRetrograde) " (R)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun TransitScreenPreview() {
    DrshtiTheme {
        TransitContent(
            positions = PreviewSamples.planetPositions,
            highlights = PreviewSamples.transitHighlights,
            favorable = listOf("New beginnings", "Communication"),
            caution = listOf("Travel delays"),
            summary = "The stars are aligning for a productive week of deep focus.",
            fromCache = false
        )
    }
}

