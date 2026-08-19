package com.pranav.drsti.ui.screen.panchang

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pranav.drsti.model.PanchangData
import com.pranav.drsti.ui.viewmodel.PanchangViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanchangScreen(viewModel: PanchangViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panchang", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Regenerate") }
                }
            )
        }
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            // Date Selector
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.selectDate(state.date.minusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    Text(
                        text = state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { viewModel.selectDate(state.date.plusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Could not calculate Panchang: ${state.error}", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                }
                state.panchang != null -> PanchangContent(state.panchang!!, fromCache = state.fromCache)
            }
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
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Showing cached result for this date.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        data.calculationNotes?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows) { (label, value) ->
                PanchangRow(label, value)
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Calculation: ${data.provenance.source}\nGenerated: ${data.provenance.generatedAt.take(19).replace("T", " ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PanchangRow(label: String, value: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeDefaults.Medium
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
