package com.pranav.drsti.ui.screen.decisions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AddChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.pranav.drsti.database.entity.DecisionEntity
import com.pranav.drsti.model.DecisionOptionAnalysis
import com.pranav.drsti.model.DecisionOptionInput
import com.pranav.drsti.ui.PreviewSamples
import com.pranav.drsti.ui.theme.DrshtiTheme
import com.pranav.drsti.ui.viewmodel.DecisionUiState
import com.pranav.drsti.ui.viewmodel.DecisionViewModel

/**
 * Decision Journal (spec §27-36) — the heart of Dṛṣṭi. New decisions get an
 * immutable pre-decision analysis; users record what they chose and later
 * what happened, without ever rewriting the original analysis.
 */
@Composable
fun DecisionScreen(viewModel: DecisionViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var showNewDecision by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refreshEvent.collect {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar("Analysis updated with latest planetary data.")
        }
    }

    LaunchedEffect(state.isBusy) {
        if (!state.isBusy && !state.error.isNullOrBlank()) {
            // Optional: short buzz for error?
        } else if (!state.isBusy && state.selectedDetail != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.selectedDetail != null -> DecisionDetailView(
                state = state,
                snackbarHostState = snackbarHostState,
                onBack = viewModel::closeDetail,
                onRefresh = { viewModel.refreshAnalysis(state.selectedDetail!!.decision.id) },
                onRecordSelection = viewModel::recordSelection,
                onRecordOutcome = viewModel::recordOutcome
            )
            showNewDecision -> NewDecisionForm(
                isBusy = state.isBusy,
                error = state.error,
                onCancel = { showNewDecision = false },
                onSubmit = { q, opts, ctx, date ->
                    viewModel.createDecision(q, opts, ctx, date)
                    showNewDecision = false
                }
            )
            else -> DecisionListView(
                decisions = state.decisions,
                error = state.error,
                snackbarHostState = snackbarHostState,
                onOpen = viewModel::openDecision,
                onDelete = viewModel::deleteDecision,
                onNew = { showNewDecision = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecisionListView(
    decisions: List<DecisionEntity>, 
    error: String?,
    snackbarHostState: SnackbarHostState,
    onOpen: (Long) -> Unit, 
    onDelete: (Long) -> Unit,
    onNew: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decision Journal", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "New decision") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (decisions.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No decisions yet. Tap + to compare your first set of options \u2014 D\u1e5b\u1e63\u1e6di will lay out astrological and timing support for each path, side by side.",
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(decisions, key = { it.id }) { decision ->
                        ElevatedCard(
                            onClick = { onOpen(decision.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ListItem(
                                    headlineContent = { Text(decision.question, fontWeight = FontWeight.SemiBold, maxLines = 2) },
                                    supportingContent = { Text(statusLabel(decision.status)) },
                                    modifier = Modifier.weight(1f),
                                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                                )
                                IconButton(
                                    onClick = { onDelete(decision.id) },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = "Vedic decision analysis is an interpretive tool for reflection. It is not a prediction of certainty and does not substitute for professional advice.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "OPEN" -> "Awaiting your decision"
    "DECIDED" -> "Decided \u2014 outcome not yet recorded"
    "COMPLETED" -> "Completed with recorded outcome"
    else -> status
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewDecisionForm(
    isBusy: Boolean, error: String?,
    onCancel: () -> Unit,
    onSubmit: (String, List<DecisionOptionInput>, String?, String?) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var context by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Decision") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = question, onValueChange = { question = it },
                label = { Text("What are you deciding?") }, 
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeDefaults.Medium
            )
            
            Text("Options to Compare", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            options.forEachIndexed { index, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { new -> options = options.toMutableList().also { it[index] = new } },
                    label = { Text("Option ${('A' + index)}") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeDefaults.Medium,
                    singleLine = true
                )
            }
            
            if (options.size < 5) {
                TextButton(onClick = { options = options + "" }) { 
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add another option") 
                }
            }

            OutlinedTextField(
                value = context, onValueChange = { context = it },
                label = { Text("Background Context (optional)") }, 
                modifier = Modifier.fillMaxWidth(), 
                minLines = 3,
                shape = ShapeDefaults.Medium
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val optionInputs = options.filter { it.isNotBlank() }
                        .mapIndexed { i, desc -> DecisionOptionInput(id = ('A' + i).toString(), description = desc) }
                    onSubmit(question, optionInputs, context.ifBlank { null }, null)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy && question.isNotBlank() && options.count { it.isNotBlank() } >= 2,
                shape = ShapeDefaults.Medium
            ) { 
                if (isBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Run Vedic Analysis") 
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecisionDetailView(
    state: DecisionUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRecordSelection: (Long, String) -> Unit,
    onRecordOutcome: (Long, String, String, String?) -> Unit
) {
    val detail = state.selectedDetail ?: return
    var showOutcomeForm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Detail") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (detail.decision.status == "OPEN" || detail.decision.status == "DECIDED") {
                        IconButton(onClick = onRefresh, enabled = !state.isBusy) {
                            if (state.isBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = "Refresh Analysis")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.error?.let { err ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Text(detail.decision.question, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                detail.decision.context?.let { 
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) 
                }
            }

            detail.analysis?.let { analysis ->
                item { 
                    Text(analysis.analysisSummary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) 
                }
                
                items(analysis.options) { option ->
                    OptionCard(
                        option = option,
                        isPreferred = option.id == analysis.preferredOptionId,
                        isSelected = option.id == detail.decision.selectedOptionId,
                        canSelect = detail.decision.status == "OPEN",
                        onSelect = { onRecordSelection(detail.decision.id, option.id) }
                    )
                }
                
                if (analysis.caveats.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))) {
                            Column(Modifier.padding(12.dp)) {
                                analysis.caveats.forEach { Text("\u2022 $it", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
                
                item {
                    Text(
                        "Analyzed ${analysis.provenance.generatedAt.take(19).replace("T", " ")} \u2022 confidence: ${analysis.confidence}. " +
                                "This snapshot is preserved immutably per D\u1e5b\u1e63\u1e6di spec.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (detail.decision.status == "DECIDED" && detail.outcome == null) {
                item {
                    if (!showOutcomeForm) {
                        Button(onClick = { showOutcomeForm = true }, modifier = Modifier.fillMaxWidth()) { 
                            Text("Record Outcome") 
                        }
                    } else {
                        OutcomeForm(onSubmit = { desc, assessment, notes ->
                            onRecordOutcome(detail.decision.id, desc, assessment, notes)
                            showOutcomeForm = false
                        })
                    }
                }
            }

            detail.outcome?.let { outcome ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("The Reality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(outcome.description, style = MaterialTheme.typography.bodyMedium)
                            Text("Assessment: ${outcome.userAssessment}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            
            detail.outcomeAnalysis?.let { oa ->
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Retrospective Alignment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(oa.scoreCalibrationNote, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            oa.alignedIndicators.forEach { Text("\u2713 $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) }
                            oa.misalignedIndicators.forEach { Text("\u2717 $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCard(option: DecisionOptionAnalysis, isPreferred: Boolean, isSelected: Boolean, canSelect: Boolean, onSelect: () -> Unit) {
    val displayName = if (option.id.length == 1 && option.id[0].isLetter()) "Option ${option.id}" else option.id

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                             else if (isPreferred) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            // Option Title
            Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            // Status/Score Row - Aligned Right
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPreferred) {
                    Text("\u2b50", modifier = Modifier.padding(end = 8.dp), fontSize = 18.sp)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = ShapeDefaults.Small
                ) {
                    Text(
                        "${option.astrologicalSupport}% Support", 
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text(option.explanation, style = MaterialTheme.typography.bodyMedium)
            
            if (option.strengths.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                option.strengths.forEach { Text("+ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
            
            if (option.concerns.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                option.concerns.forEach { Text("\u26A0 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            
            if (canSelect) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) { Text("I chose this path") }
            } else if (isSelected) {
                Spacer(Modifier.height(12.dp))
                AssistChip(onClick = {}, label = { Text("Your actual choice") }, leadingIcon = { Icon(Icons.Default.AddChart, null, modifier = Modifier.size(16.dp)) })
            }
        }
    }
}

@Composable
private fun OutcomeForm(onSubmit: (String, String, String?) -> Unit) {
    var description by remember { mutableStateOf("") }
    var assessment by remember { mutableStateOf("As expected") }
    var notes by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Record what actually happened:", style = MaterialTheme.typography.labelLarge)
        
        OutlinedTextField(
            value = description, onValueChange = { description = it }, 
            label = { Text("Outcome Summary") }, 
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeDefaults.Medium
        )
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Better", "As expected", "Worse").forEach { option ->
                FilterChip(
                    selected = assessment.contains(option), 
                    onClick = { assessment = option },
                    label = { Text(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        OutlinedTextField(
            value = notes, onValueChange = { notes = it }, 
            label = { Text("Reflections (optional)") }, 
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeDefaults.Medium
        )
        
        Button(
            onClick = { onSubmit(description, assessment, notes.ifBlank { null }) }, 
            enabled = description.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Outcome")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DecisionListViewPreview() {
    DrshtiTheme {
        DecisionListView(
            decisions = listOf(PreviewSamples.decision),
            error = null,
            snackbarHostState = SnackbarHostState(),
            onOpen = {},
            onDelete = {},
            onNew = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionCardPreview() {
    DrshtiTheme {
        Column(Modifier.padding(16.dp)) {
            OptionCard(
                option = PreviewSamples.decisionAnalysis.options.first(),
                isPreferred = true,
                isSelected = false,
                canSelect = true,
                onSelect = {}
            )
        }
    }
}
