package com.pranav.drsti.ui.screen.kundali

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.drsti.model.DashaData
import com.pranav.drsti.model.DashaPeriod
import com.pranav.drsti.model.KundaliData
import com.pranav.drsti.model.KundaliPlanet
import com.pranav.drsti.ui.screen.transit.TransitScreen
import com.pranav.drsti.ui.viewmodel.KundaliViewModel
import com.pranav.drsti.ui.viewmodel.TransitViewModel

/** North Indian style Kundali (spec §18, §53), drawn locally from structured data — never AI-drawn. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KundaliScreen(
    viewModel: KundaliViewModel,
    transitViewModel: TransitViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Birth Chart", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { if (selectedTab == 0) viewModel.recompute() else transitViewModel.refresh(true) }) { 
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh") 
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Chart") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Dasha") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Transits") })
            }

            when {
                state.activePerson == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Add birth details in Profile to generate Kundali.", textAlign = TextAlign.Center)
                }
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.error}", textAlign = TextAlign.Center)
                }
                state.kundali != null -> {
                    when (selectedTab) {
                        0 -> KundaliContent(state.kundali!!, dashaSummary = state.dasha?.let {
                            "${it.currentMahadasha?.planet?.displayName()} \u203a ${it.currentAntardasha?.planet?.displayName()}"
                        })
                        1 -> DashaTimelineView(state.dasha)
                        2 -> TransitScreen(transitViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun KundaliContent(kundali: KundaliData, dashaSummary: String?) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Ascendant: ${kundali.ascendant.sign.displayName} \u2022 ${kundali.ascendant.nakshatra.displayName} pada ${kundali.ascendant.pada}",
            style = MaterialTheme.typography.titleMedium
        )
        dashaSummary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))

        NorthIndianChart(kundali, modifier = Modifier.fillMaxWidth().aspectRatio(1f))

        Spacer(Modifier.height(16.dp))
        Text("Planetary Positions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(kundali.planets, key = { it.planet }) { planet -> PlanetRow(planet) }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "System: ${kundali.zodiac} \u2022 ${kundali.ayanamsha} Ayanamsha \u2022 ${kundali.houseSystem} houses. " +
                    "Positions computed locally (${kundali.provenance.source}), generated ${kundali.provenance.generatedAt.take(19)}.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanetRow(planet: KundaliPlanet) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(planet.planet.displayName(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${planet.sign.displayName} ${"%.1f".format(planet.degreeInSign)}\u00B0" + if (planet.retrograde) " (R)" else "",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.width(8.dp))
            Text("House ${planet.house}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * A simple, honest North-Indian diamond chart: the 12 fixed diamond/triangle
 * cells (house 1 always top-center diamond) each list the sign number and
 * any planets whose *house* placement (relative to the Ascendant) falls there.
 */
@Composable
private fun NorthIndianChart(kundali: KundaliData, modifier: Modifier = Modifier) {
    val planetsByHouse = kundali.planets.groupBy { it.house }
    val ascendantSignIndex = kundali.ascendant.sign.index

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2f)
        val lineColor = Color(0xFF8B7A9E)

        // Outer square
        drawRect(color = lineColor, topLeft = Offset.Zero, size = size, style = stroke)
        // Diagonals
        drawLine(lineColor, Offset(0f, 0f), Offset(w, h), strokeWidth = 2f)
        drawLine(lineColor, Offset(w, 0f), Offset(0f, h), strokeWidth = 2f)
        // Inner diamond (connecting edge midpoints)
        drawLine(lineColor, Offset(w / 2, 0f), Offset(w, h / 2), strokeWidth = 2f)
        drawLine(lineColor, Offset(w, h / 2), Offset(w / 2, h), strokeWidth = 2f)
        drawLine(lineColor, Offset(w / 2, h), Offset(0f, h / 2), strokeWidth = 2f)
        drawLine(lineColor, Offset(0f, h / 2), Offset(w / 2, 0f), strokeWidth = 2f)

        // House label anchor points (approximate centers for the 12 classic North-Indian cells),
        // starting at house 1 (top diamond) and proceeding clockwise.
        val anchors = listOf(
            Offset(w * 0.5f, h * 0.22f),   // 1 top diamond
            Offset(w * 0.25f, h * 0.12f),  // 2
            Offset(w * 0.12f, h * 0.25f),  // 3
            Offset(w * 0.22f, h * 0.5f),   // 4 left diamond
            Offset(w * 0.12f, h * 0.75f),  // 5
            Offset(w * 0.25f, h * 0.88f),  // 6
            Offset(w * 0.5f, h * 0.78f),   // 7 bottom diamond
            Offset(w * 0.75f, h * 0.88f),  // 8
            Offset(w * 0.88f, h * 0.75f),  // 9
            Offset(w * 0.78f, h * 0.5f),   // 10 right diamond
            Offset(w * 0.88f, h * 0.25f),  // 11
            Offset(w * 0.75f, h * 0.12f)   // 12
        )

        anchors.forEachIndexed { idx, anchor ->
            val houseNumber = idx + 1
            val signIndex = (ascendantSignIndex + idx) % 12
            val planetsHere = planetsByHouse[houseNumber].orEmpty()
            val label = buildString {
                append(signIndex + 1)
                if (planetsHere.isNotEmpty()) {
                    append("\n")
                    append(planetsHere.joinToString(" ") { p -> abbreviate(p.planet.name) })
                }
            }
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(34, 25, 51)
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 26f
                    isAntiAlias = true
                }
                label.split("\n").forEachIndexed { lineIdx, line ->
                    drawText(line, anchor.x, anchor.y + lineIdx * 28f, paint)
                }
            }
        }
    }
}

@Composable
private fun DashaTimelineView(dasha: DashaData?) {
    if (dasha == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Dasha periods not calculated.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Vimshottari Dasha Timeline",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(dasha.mahadashas) { period ->
            val isCurrent = period.planet == dasha.currentMahadasha?.planet
            DashaPeriodCard(period, isCurrent)
        }
        
        item {
            Spacer(Modifier.height(24.dp))
            Text(
                "Dashas are calculated from the Moon's sidereal position at birth.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashaPeriodCard(period: DashaPeriod, isCurrent: Boolean) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = period.planet.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${period.startDateIso} to ${period.endDateIso}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isCurrent) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("Active") }
                )
            }
        }
    }
}

private fun abbreviate(planetName: String): String = when (planetName) {
    "SUN" -> "Su"; "MOON" -> "Mo"; "MARS" -> "Ma"; "MERCURY" -> "Me"; "JUPITER" -> "Ju"
    "VENUS" -> "Ve"; "SATURN" -> "Sa"; "RAHU" -> "Ra"; "KETU" -> "Ke"; else -> planetName.take(2)
}
