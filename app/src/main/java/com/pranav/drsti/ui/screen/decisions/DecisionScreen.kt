package com.pranav.drsti.ui.screen.decisions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AddChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pranav.drsti.database.entity.DecisionEntity
import com.pranav.drsti.model.DecisionOptionAnalysis
import com.pranav.drsti.model.DecisionOptionInput
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

    Column(modifier = modifier.fillMaxSize()) {
        when {
            state.selectedDetail != null -> DecisionDetailView(
                state = state,
                onBack = viewModel::closeDetail,
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
                onOpen = viewModel::openDecision,
                onNew = { showNewDecision = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecisionListView(decisions: List<DecisionEntity>, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Decision Journal") },
            actions = { IconButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "New decision") } }
        )
        if (decisions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No decisions yet. Tap + to compare your first set of options \u2014 D\u1e5b\u1e63\u1e6di will lay out astrological and timing support for each path, side by side.",
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn {
                items(decisions, key = { it.id }) { decision ->
                    ListItem(
                        headlineContent = { Text(decision.question) },
                        supportingContent = { Text(statusLabel(decision.status)) },
                        modifier = Modifier.clickable { onOpen(decision.id) }
                    )
                    HorizontalDivider()
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

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("New decision") },
            navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } }
        )
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = question, onValueChange = { question = it },
                label = { Text("What are you deciding?") }, modifier = Modifier.fillMaxWidth()
            )
            Text("Options", style = MaterialTheme.typography.titleSmall)
            options.forEachIndexed { index, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { new -> options = options.toMutableList().also { it[index] = new } },
                    label = { Text("Option ${('A' + index)}") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(onClick = { options = options + "" }) { Text("+ Add another option") }

            OutlinedTextField(
                value = context, onValueChange = { context = it },
                label = { Text("Context (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = {
                    val optionInputs = options.filter { it.isNotBlank() }
                        .mapIndexed { i, desc -> DecisionOptionInput(id = ('A' + i).toString(), description = desc) }
                    onSubmit(question, optionInputs, context.ifBlank { null }, null)
                },
                enabled = !isBusy && question.isNotBlank() && options.count { it.isNotBlank() } >= 2
            ) { Text(if (isBusy) "Analyzing\u2026" else "Compare paths") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecisionDetailView(
    state: DecisionUiState,
    onBack: () -> Unit,
    onRecordSelection: (Long, String) -> Unit,
    onRecordOutcome: (Long, String, String, String?) -> Unit
) {
    val detail = state.selectedDetail ?: return
    var showOutcomeForm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(detail.decision.question, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
        )

        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            detail.decision.context?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }

            detail.analysis?.let { analysis ->
                item { Text(analysis.analysisSummary, style = MaterialTheme.typography.bodyMedium) }
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
                        Column {
                            analysis.caveats.forEach { Text("\u2022 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                item {
                    Text(
                        "Analyzed ${analysis.provenance.generatedAt.take(19)} \u2022 confidence: ${analysis.confidence}. " +
                                "This snapshot is preserved as-is and will never be rewritten, even after you record an outcome.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (detail.decision.status == "DECIDED" && detail.outcome == null) {
                item {
                    if (!showOutcomeForm) {
                        OutlinedButton(onClick = { showOutcomeForm = true }) { Text("Record what happened") }
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
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("What happened", style = MaterialTheme.typography.titleSmall)
                            Text(outcome.description, style = MaterialTheme.typography.bodySmall)
                            Text("Assessment: ${outcome.userAssessment}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            detail.outcomeAnalysis?.let { oa ->
                item {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("Retrospective analysis", style = MaterialTheme.typography.titleSmall)
                            Text(oa.scoreCalibrationNote, style = MaterialTheme.typography.bodySmall)
                            oa.alignedIndicators.forEach { Text("\u2713 $it", style = MaterialTheme.typography.labelSmall) }
                            oa.misalignedIndicators.forEach { Text("\u2717 $it", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCard(option: DecisionOptionAnalysis, isPreferred: Boolean, isSelected: Boolean, canSelect: Boolean, onSelect: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Option ${option.id}" + if (isPreferred) " \u2b50" else "", style = MaterialTheme.typography.titleSmall)
                Text("${option.astrologicalSupport}% astrological \u2022 ${option.timingSupport}% timing", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(option.explanation, style = MaterialTheme.typography.bodySmall)
            option.strengths.forEach { Text("+ $it", style = MaterialTheme.typography.labelSmall) }
            option.concerns.forEach { Text("\u26A0 $it", style = MaterialTheme.typography.labelSmall) }
            if (canSelect) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSelect) { Text("I chose this") }
            } else if (isSelected) {
                Spacer(Modifier.height(8.dp))
                AssistChip(onClick = {}, label = { Text("Your choice") })
            }
        }
    }
}

@Composable
private fun OutcomeForm(onSubmit: (String, String, String?) -> Unit) {
    var description by remember { mutableStateOf("") }
    var assessment by remember { mutableStateOf("As expected") }
    var notes by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("What happened?") }, modifier = Modifier.fillMaxWidth())
        Row {
            listOf("Better than expected", "As expected", "Worse than expected").forEach { option ->
                FilterChip(
                    selected = assessment == option, onClick = { assessment = option },
                    label = { Text(option) }, modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSubmit(description, assessment, notes.ifBlank { null }) }, enabled = description.isNotBlank()) {
            Text("Save outcome")
        }
    }
}

private fun Modifier.clickable(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
