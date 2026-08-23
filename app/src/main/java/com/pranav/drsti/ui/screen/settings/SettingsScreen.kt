package com.pranav.drsti.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pranav.drsti.ui.viewmodel.SettingsUiState
import kotlinx.coroutines.launch

import com.pranav.drsti.database.DrishtiDatabase
import com.pranav.drsti.ui.theme.DrshtiTheme
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
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
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

                Spacer(Modifier.height(8.dp))

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

                if (state.aiMode != "MOCK") {
                    Spacer(Modifier.height(16.dp))
                    
                    if (state.aiMode == "GEMINI") {
                        if (!showApiKeyField) {
                            Button(
                                onClick = { 
                                    showApiKeyField = true
                                    tempApiKey = state.geminiApiKey
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                shape = ShapeDefaults.Medium
                            ) {
                                Text(if (state.hasGeminiApiKey) "Update Gemini Key" else "Configure Gemini Key")
                            }
                        } else {
                            GeminiGuidedFlow(
                                tempApiKey = tempApiKey,
                                onTempApiKeyChange = { tempApiKey = it },
                                onSaveKey = {
                                    viewModel.saveGeminiApiKey(tempApiKey)
                                    showApiKeyField = false
                                },
                                onCancel = {
                                    showApiKeyField = false
                                    tempApiKey = ""
                                }
                            )
                        }
                    } else {
                        val providerName = "OpenAI"
                        val currentHasKey = state.hasApiKey
                        val currentKey = state.apiKey

                        Text("$providerName API Key", style = MaterialTheme.typography.labelLarge)

                        if (!showApiKeyField) {
                            Button(
                                onClick = {
                                    showApiKeyField = true
                                    tempApiKey = currentKey
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = ShapeDefaults.Medium
                            ) {
                                Text(if (currentHasKey) "Update $providerName Key" else "Configure $providerName Key")
                            }
                        } else {
                            OutlinedTextField(
                                value = tempApiKey,
                                onValueChange = { tempApiKey = it },
                                label = { Text("$providerName Key") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = ShapeDefaults.Medium
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.saveApiKey(tempApiKey)
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
                }

                // Model selection: either a free text field or a dropdown when in GEMINI mode
                if (state.aiMode == "GEMINI") {
                    Spacer(Modifier.height(16.dp))
                    // Show dropdown of fetched Gemini models
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.aiModel,
                            onValueChange = { },
                            label = { Text("Gemini Model") },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            // Show loading indicator if models are being fetched
                            if (state.geminiModelsLoading) {
                                DropdownMenuItem(
                                    text = { Text("Loading models…") },
                                    onClick = { },
                                    enabled = false
                                )
                            } else {
                                val modelList = state.geminiModelList.ifEmpty {
                                    listOf("gemini-1.5-flash", "gemini-1.0-pro")
                                }
                                modelList.forEach { modelName ->
                                    DropdownMenuItem(
                                        text = { Text(modelName) },
                                        onClick = {
                                            viewModel.setAiModel(modelName)
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // For MOCK or LIVE, keep the free‑form text field
                    OutlinedTextField(
                        value = state.aiModel,
                        onValueChange = viewModel::setAiModel,
                        label = { Text("Model Identifier") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = ShapeDefaults.Medium
                    )
                }

                state.saveMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Legal & Safety Disclaimer",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "D\u1e5b\u1e63\u1e6di is an offline-first app. Your birth details (date, time, place) are stored ONLY on your device and are never sent to our servers. " +
                           "AI analysis is for reflection and entertainment only. The responsibility for all data shared in chat and maintained on this device stands wholly with the user, not with us. " +
                           "Caution is recommended when sharing sensitive personal information with external AI providers (OpenAI/Google).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    DrshtiTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsGroup(title = "Account & Personalization") {
                OutlinedListItem(headline = "Birth Profile", supporting = "Manage your birth details", onClick = {})
            }
            SettingsGroup(title = "AI Configuration") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("MOCK") })
                    FilterChip(selected = false, onClick = {}, label = { Text("LIVE") })
                }
            }
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

@Composable
fun ClipboardAutoPasteEffect(
    isFieldEmpty: Boolean,
    onKeyDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val coroutineScope = rememberCoroutineScope()
    
    var lastProcessedValue by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    delay(300)
                    if (clipboardManager.hasPrimaryClip()) {
                        val clipData = clipboardManager.primaryClip
                        val description = clipboardManager.primaryClipDescription
                        
                        if (clipData != null && clipData.itemCount > 0 && 
                            description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                            
                            val text = clipData.getItemAt(0).text?.toString()?.trim() ?: ""
                            val keyRegex = Regex("AIza[0-9A-Za-z_-]{30,}")
                            val match = keyRegex.find(text)
                            
                            if (match != null) {
                                val detectedKey = match.value
                                if (detectedKey != lastProcessedValue || isFieldEmpty) {
                                    lastProcessedValue = detectedKey
                                    onKeyDetected(detectedKey)
                                }
                            }
                        }
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun GeminiGuidedFlow(
    tempApiKey: String,
    onTempApiKeyChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var isKeyDetected by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    ClipboardAutoPasteEffect(isFieldEmpty = tempApiKey.isEmpty()) { detectedKey ->
        onTempApiKeyChange(detectedKey)
        isKeyDetected = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gemini Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Text(
                "D\u1e5b\u1e63\u1e6di uses your personal Gemini key to provide AI analysis. This key is stored only on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!showInstructions) {
                Button(
                    onClick = { showInstructions = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeDefaults.Medium
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Help me create a key")
                }
            }

            if (showInstructions) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = ShapeDefaults.Medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Instructions:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("1. Tap the button below to open Google AI Studio.", style = MaterialTheme.typography.bodySmall)
                        Text("2. Click \"Create API key\" (sign in if needed).", style = MaterialTheme.typography.bodySmall)
                        Text("3. Copy the key and return to D\u1e5b\u1e63\u1e6di.", style = MaterialTheme.typography.bodySmall)
                        
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ShapeDefaults.Small
                        ) {
                            Text("\uD83D\uDE80 Open AI Studio")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = tempApiKey,
                onValueChange = { 
                    onTempApiKeyChange(it)
                    isKeyDetected = false
                },
                label = { Text("Gemini API Key") },
                placeholder = { Text("AIzaSy...") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = ShapeDefaults.Medium,
                supportingText = {
                    if (isKeyDetected) {
                        Text("\u2713 Gemini key detected from clipboard", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Paste your key here or use the guided flow above.")
                    }
                },
                trailingIcon = {
                    if (isKeyDetected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        IconButton(onClick = {
                            val clipData = clipboardManager.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text?.toString()?.trim() ?: ""
                                onTempApiKeyChange(text)
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                        }
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSaveKey,
                modifier = Modifier.weight(1f),
                enabled = tempApiKey.isNotEmpty()
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
        }
    }
}



