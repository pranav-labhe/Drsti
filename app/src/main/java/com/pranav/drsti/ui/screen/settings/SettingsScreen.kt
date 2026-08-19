package com.pranav.drsti.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pranav.drsti.database.DrishtiDatabase
import com.pranav.drsti.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    database: DrishtiDatabase,
    onOpenProfile: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var tempApiKey by remember { mutableStateOf("") }
    var showApiKeyField by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            SettingsGroup(title = "Account & Personalization") {
                OutlinedListItem(
                    headline = "Birth Profile",
                    supporting = "Manage your birth details for accurate analysis",
                    onClick = onOpenProfile
                )
                OutlinedListItem(
                    headline = "Places",
                    supporting = "Manage saved locations for Panchang and transits",
                    onClick = onOpenPlaces
                )
            }

            SettingsGroup(title = "Astrology Engine") {
                InfoItem("Zodiac System", "Sidereal (Lahiri)")
                InfoItem("House System", "Whole Sign")
                InfoItem("Dasha System", "Vimshottari (120 Years)")
                Text(
                    "Calculation engine is locked to traditional D\u1e5b\u1e63\u1e6di standards (V1).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            SettingsGroup(title = "AI Configuration") {
                Text("AI Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("MOCK", "LIVE", "GEMINI").forEach { mode ->
                        FilterChip(
                            selected = state.aiMode == mode,
                            onClick = { 
                                viewModel.setAiMode(mode)
                                showApiKeyField = false
                            },
                            label = { Text(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Text(
                    when (state.aiMode) {
                        "MOCK" -> "MOCK: Fully offline, zero cost. Uses rule-based Jyotish interpretation."
                        "LIVE" -> "LIVE (OpenAI): Advanced interpretive capabilities using OpenAI GPT models."
                        "GEMINI" -> "GEMINI (Google): High-performance interpretive analysis via Google Gemini."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = state.aiModel,
                    onValueChange = viewModel::setAiModel,
                    label = { Text("Model Identifier") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = ShapeDefaults.Medium
                )

                if (state.aiMode != "MOCK") {
                    Spacer(Modifier.height(16.dp))
                    val providerName = if (state.aiMode == "LIVE") "OpenAI" else "Gemini"
                    val currentHasKey = if (state.aiMode == "LIVE") state.hasApiKey else state.hasGeminiApiKey
                    val currentKey = if (state.aiMode == "LIVE") state.apiKey else state.geminiApiKey

                    Text("$providerName API Key", style = MaterialTheme.typography.labelLarge)
                    
                    if (!showApiKeyField) {
                        Button(
                            onClick = { 
                                showApiKeyField = true
                                tempApiKey = currentKey
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = ShapeDefaults.Medium
                        ) {
                            Text(if (currentHasKey) "Update $providerName Key" else "Configure $providerName Key")
                        }
                    } else {
                        OutlinedTextField(
                            value = tempApiKey,
                            onValueChange = { tempApiKey = it },
                            label = { Text("$providerName Key") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = ShapeDefaults.Medium
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (state.aiMode == "LIVE") viewModel.saveApiKey(tempApiKey)
                                    else viewModel.saveGeminiApiKey(tempApiKey)
                                    showApiKeyField = false
                                },
                                modifier = Modifier.weight(1f),
                                enabled = tempApiKey.isNotEmpty()
                            ) {
                                Text("Save")
                            }
                            OutlinedButton(
                                onClick = {
                                    showApiKeyField = false
                                    tempApiKey = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                state.saveMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
            }

            SettingsGroup(title = "Diagnostics & Privacy") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Planetary Freshness", style = MaterialTheme.typography.bodyLarge)
                        Text("Minutes before recalculating cache", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = state.planetaryFreshnessMinutes.toString(),
                        onValueChange = { it.toIntOrNull()?.let(viewModel::setPlanetaryFreshness) },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        shape = ShapeDefaults.Small
                    )
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                OutlinedListItem(
                    headline = "AI Diagnostics Log",
                    supporting = "View recent AI request history and error details",
                    onClick = onOpenDiagnostics
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                OutlinedListItem(
                    headline = "Export/Backup Data",
                    supporting = "Download your local database as a backup file",
                    onClick = { /* Placeholder for §49 file export logic */ }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                OutlinedListItem(
                    headline = "Clear Local Cache",
                    supporting = "Reset all cached astrological calculations",
                    onClick = { viewModel.clearCache(database) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Diagnostic Logging", style = MaterialTheme.typography.bodyLarge)
                        Text("Record errors to improve calculations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.diagnosticsEnabled, onCheckedChange = viewModel::setDiagnosticsEnabled)
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "D\u1e5b\u1e63\u1e6di \u2022 Version 1.0.0\n\u201CGive me a clearer view of the path; let me make the choice.\u201D",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun OutlinedListItem(headline: String, supporting: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ShapeDefaults.Medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}
