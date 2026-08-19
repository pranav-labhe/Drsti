package com.pranav.drsti.ui.screen.panchang

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pranav.drsti.model.PanchangData
import com.pranav.drsti.ui.viewmodel.PanchangViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanchangScreen(viewModel: PanchangViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Panchang") },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Regenerate") }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.selectDate(state.date.minusDays(1)) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
            }
            Text(state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { viewModel.selectDate(state.date.plusDays(1)) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Could not calculate Panchang: ${state.error}", textAlign = TextAlign.Center)
            }
            state.panchang != null -> PanchangContent(state.panchang!!, fromCache = state.fromCache)
        }
    }
}

@Composable
private fun PanchangContent(data: PanchangData, fromCache: Boolean) {
    val rows = listOfNotNull(
        "Vara" to data.vara,
        "Tithi" to "${data.tithiName} (${data.paksha})",
        "Nakshatra" to data.nakshatra.displayName,
        "Yoga" to data.yogaName,
        "Karana" to data.karanaName,
        data.sunriseLocal?.let { "Sunrise" to it },
        data.sunsetLocal?.let { "Sunset" to it },
        data.rahuKalam?.let { "Rahu Kalam" to it },
        data.yamaganda?.let { "Yamaganda" to it },
        data.gulikaKalam?.let { "Gulika Kalam" to it },
        data.abhijitMuhurta?.let { "Abhijit Muhurta" to it }
    )

    Column(Modifier.fillMaxSize()) {
        if (fromCache) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text("Showing cached result for this date.", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        data.calculationNotes?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(rows) { (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "AI calculation \u2022 generated ${data.provenance.generatedAt.take(19)} \u2022 ${data.provenance.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
