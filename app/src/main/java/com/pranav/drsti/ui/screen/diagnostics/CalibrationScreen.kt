package com.pranav.drsti.ui.screen.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.drsti.data.repository.DecisionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    decisionRepository: DecisionRepository,
    onBack: () -> Unit
) {
    val stats by decisionRepository.observeCalibrationStats().collectAsState(initial = null)
    
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Calibration Insights") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "D\u1e5b\u1e63\u1e6di calibrates its offline Vedic reasoning by comparing its initial support scores against the outcomes you record.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            stats?.let { s ->
                CalibrationCard(
                    title = "Reasoning Accuracy",
                    value = if (s.outcomesRecorded > 0) "${(s.directionallyCorrect.toFloat() / s.outcomesRecorded * 100).toInt()}%" else "N/A",
                    subtitle = "Law-Alignment Score",
                    icon = Icons.Default.Timeline
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatBox(
                        label = "Analyzed",
                        value = s.decisionsAnalyzed.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        label = "Recorded",
                        value = s.outcomesRecorded.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }

                CalibrationCard(
                    title = "Confidence Tuning",
                    value = when {
                        s.overconfidenceCount > s.underconfidenceCount -> "Cautionary"
                        s.underconfidenceCount > s.overconfidenceCount -> "Supportive"
                        else -> "Balanced"
                    },
                    subtitle = "Bias Indicator",
                    icon = Icons.Default.Straighten
                )
            } ?: Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No calibration data yet. Start recording outcomes to see insights.")
            }

            Spacer(Modifier.height(32.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Calibration scores are private and calculated locally on your device.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationCard(title: String, value: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp).alpha(0.2f))
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
