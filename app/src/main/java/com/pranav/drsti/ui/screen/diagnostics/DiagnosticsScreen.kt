package com.pranav.drsti.ui.screen.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.drsti.database.entity.AIRequestLogEntity
import com.pranav.drsti.ui.PreviewSamples
import com.pranav.drsti.ui.theme.DrshtiTheme
import com.pranav.drsti.ui.viewmodel.DiagnosticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Diagnostics Log", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                    }
                }
            )
        }
    ) { padding ->
        if (state.logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No AI logs recorded yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            SelectionContainer(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.logs, key = { it.id }) { log ->
                        LogItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItem(log: AIRequestLogEntity) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (log.success) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = log.requestType.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (log.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${log.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                text = "Model: ${log.model} (${log.promptVersion})",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            
            Text(
                text = "Time: ${log.timestamp.take(19).replace("T", " ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!log.success && log.error != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    shape = ShapeDefaults.Small
                ) {
                    Text(
                        text = log.error,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DiagnosticsScreenPreview() {
    DrshtiTheme {
        Scaffold { padding ->
            SelectionContainer(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PreviewSamples.logs) { log ->
                        LogItem(log)
                    }
                }
            }
        }
    }
}

