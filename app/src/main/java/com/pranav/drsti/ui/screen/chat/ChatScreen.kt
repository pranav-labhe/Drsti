package com.pranav.drsti.ui.screen.chat

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.drsti.database.entity.ConversationMessageEntity
import com.pranav.drsti.model.Conversation
import com.pranav.drsti.model.DecisionAnalysis
import com.pranav.drsti.model.DecisionOptionAnalysis
import com.pranav.drsti.model.Provenance
import com.pranav.drsti.ui.PreviewSamples
import com.pranav.drsti.ui.theme.DrshtiTheme
import com.pranav.drsti.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToKundali: () -> Unit = {},
    onNavigateToPanchang: () -> Unit = {},
    onNavigateToDecisions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val error by viewModel.error.collectAsState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawerContent(
                conversations = conversations,
                currentConversationId = state.conversationId?.toString(),
                onConversationSelected = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                },
                onNewConversation = {
                    viewModel.createConversation()
                    scope.launch { drawerState.close() }
                },
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToKundali = onNavigateToKundali,
                onNavigateToPanchang = onNavigateToPanchang
            )
        },
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("D\u1e5b\u1e63\u1e6di", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToDecisions) {
                            Icon(Icons.Default.AddChart, contentDescription = "New Decision")
                        }
                        IconButton(onClick = { viewModel.createConversation() }) {
                            Icon(Icons.Default.Add, contentDescription = "New Chat")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (state.activePerson == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Add your birth details in Settings \u2192 Profile to unlock personalised analysis.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (state.messages.isEmpty()) {
                        EmptyChatPrompt(
                            onNewDecision = onNavigateToDecisions,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        SelectionContainer(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.messages, key = { it.id }) { message ->
                                    ChatBubble(message)
                                }
                                if (state.isSending) {
                                    item { TypingIndicator() }
                                }
                            }
                        }
                    }
                }

                ChatInputBar(
                    value = input,
                    onValueChange = { input = it },
                    onSend = {
                        if (input.isNotBlank()) {
                            viewModel.sendMessage(input)
                            input = ""
                        }
                    },
                    enabled = !state.isSending,
                    modifier = Modifier.imePadding()
                )
            }
        }
    }
}

@Composable
private fun EmptyChatPrompt(
    onNewDecision: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\u0926\u0943\u0937\u094d\u091f\u093f", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "\"See beyond the obvious; discover different perspectives.\"",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = onNewDecision,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.AddChart, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New Decision")
        }

        Spacer(Modifier.height(32.dp))
        Text("Suggestions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        listOf(
            "Should I change jobs this month?",
            "What's today's Panchang?",
            "Should I call now or around 7 PM?",
            "Analyze my current Dasha"
        ).forEach { suggestion ->
            Card(
                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Text(
                    "\u201C$suggestion\u201D",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Legal Disclaimer: AI interpretations are for reflection and entertainment only. D\u1e5b\u1e63\u1e6di is an offline-first app. The responsibility for all data shared in this chat and maintained on this device stands wholly with the user, not with us. Be cautious about sharing sensitive secrets, as external AI providers (if enabled) will process your chat content.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun ChatBubble(message: ConversationMessageEntity) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp),
            tonalElevation = if (isUser) 0.dp else 2.dp
        ) {
            if (isUser) {
                Text(
                    message.content,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val content = message.content
                
                // Check for markers with or without quotes
                val markers = listOf(
                    "DECISION_ANALYSIS_START" to "DECISION_ANALYSIS_END",
                    "\"DECISION_ANALYSIS_START\"" to "\"DECISION_ANALYSIS_END\""
                )
                
                var foundStart = -1
                var foundEnd = -1
                var activeStartMarker = ""
                var activeEndMarker = ""

                for ((start, end) in markers) {
                    val s = content.indexOf(start)
                    val e = content.indexOf(end)
                    if (s != -1 && e != -1 && e > s) {
                        foundStart = s
                        foundEnd = e
                        activeStartMarker = start
                        activeEndMarker = end
                        break
                    }
                }

                if (foundStart != -1) {
                    val preText = content.substring(0, foundStart).trim()
                    var rawJson = content.substring(foundStart + activeStartMarker.length, foundEnd).trim()
                    val postText = content.substring(foundEnd + activeEndMarker.length).trim()

                    // Sanitize JSON by removing markdown code blocks if present
                    if (rawJson.startsWith("```")) {
                        // Remove opening block like ```json or ```
                        rawJson = rawJson.substringAfter("\n").substringBeforeLast("```").trim()
                    }
                    
                    val analysis = remember(rawJson) {
                        try {
                            val element = Json.parseToJsonElement(rawJson).jsonObject
                            
                            val optionsList = element["options"]?.jsonArray?.map { opt ->
                                val o = opt.jsonObject
                                DecisionOptionAnalysis(
                                    id = o["id"]?.jsonPrimitive?.content ?: "",
                                    astrologicalSupport = o["astrologicalSupport"]?.jsonPrimitive?.intOrNull 
                                        ?: o["astrological_support"]?.jsonPrimitive?.intOrNull 
                                        ?: o["astrologicalScore"]?.jsonPrimitive?.intOrNull ?: -1,
                                    timingSupport = o["timingSupport"]?.jsonPrimitive?.intOrNull 
                                        ?: o["timing_support"]?.jsonPrimitive?.intOrNull 
                                        ?: o["timingScore"]?.jsonPrimitive?.intOrNull ?: -1,
                                    strength = o["strength"]?.jsonPrimitive?.content 
                                        ?: o["result"]?.jsonPrimitive?.content ?: "moderate",
                                    explanation = o["explanation"]?.jsonPrimitive?.content ?: "",
                                    strengths = o["strengths"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                                    concerns = o["concerns"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                                    supportingFactors = o["supportingFactors"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                                    contradictingFactors = o["contradictingFactors"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                                )
                            } ?: emptyList()

                            DecisionAnalysis(
                                analysisSummary = element["summary"]?.jsonPrimitive?.content 
                                    ?: element["analysisSummary"]?.jsonPrimitive?.content ?: "",
                                options = optionsList,
                                preferredOptionId = element["preferredOptionId"]?.jsonPrimitive?.content 
                                    ?: element["preferred_option_id"]?.jsonPrimitive?.content,
                                confidence = element["confidence"]?.jsonPrimitive?.let { 
                                    val content = it.content
                                    val doubleVal = it.doubleOrNull
                                    if (doubleVal != null && doubleVal > 0 && doubleVal <= 1.0) {
                                        "${(doubleVal * 100).toInt()}%"
                                    } else {
                                        content
                                    }
                                } ?: "medium",
                                provenance = Provenance(
                                    calculationVersion = "1.0",
                                    generatedAt = java.time.Instant.now().toString(),
                                    source = "AI",
                                    sourceVersion = "1.0",
                                    inputHash = "",
                                    outputHash = ""
                                )
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (analysis != null) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            if (preText.isNotEmpty()) {
                                MarkdownText(
                                    preText,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                            
                            // Wrapped Analysis Block
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ),
                                shadowElevation = 1.dp
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Section Header
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Insights, 
                                            contentDescription = null, 
                                            tint = MaterialTheme.colorScheme.primary, 
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "DECISION ANALYSIS", 
                                            style = MaterialTheme.typography.labelLarge, 
                                            fontWeight = FontWeight.Black, 
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    
                                    DecisionAnalysisView(analysis)
                                }
                            }
                            
                            if (postText.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                MarkdownText(
                                    postText,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        MarkdownText(
                            message.content,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    MarkdownText(
                        message.content,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionAnalysisView(analysis: DecisionAnalysis) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary text at the top - Styled Bold for impact
        if (analysis.analysisSummary.isNotEmpty()) {
            Text(
                text = analysis.analysisSummary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Each option as a "Pathway" card
        analysis.options.forEach { option ->
            val isPreferred = option.id == analysis.preferredOptionId
            PathwayCard(option, isPreferred)
        }

        // Final Verdict / Lead Conclusion
        if (analysis.preferredOptionId != null) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Lead Conclusion",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Option ${analysis.preferredOptionId} is the recommended pathway with ${analysis.confidence} confidence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }
        }
        
        if (analysis.caveats.isNotEmpty()) {
            Text(
                text = "Caveats: " + analysis.caveats.joinToString("; "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun PathwayCard(option: DecisionOptionAnalysis, isPreferred: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPreferred) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isPreferred) 
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) 
        else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Name and Strength
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Path: ${option.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPreferred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                
                Surface(
                    color = getStrengthColor(option.strength).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = option.strength.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = getStrengthColor(option.strength),
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Support Metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SupportMetric(
                    label = "Stellar Alignment",
                    icon = Icons.Default.Star,
                    percentage = option.astrologicalSupport,
                    modifier = Modifier.weight(1f)
                )
                SupportMetric(
                    label = "Precision Timing",
                    icon = Icons.Default.Schedule,
                    percentage = option.timingSupport,
                    modifier = Modifier.weight(1f)
                )
            }

            if (option.explanation.isNotEmpty() || option.strengths.isNotEmpty() || option.concerns.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                
                if (option.explanation.isNotEmpty()) {
                    Text(
                        text = option.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (option.strengths.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    option.strengths.forEach { s ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 1.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(s, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (option.concerns.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    option.concerns.forEach { c ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 1.dp)) {
                            Icon(Icons.Default.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(c, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportMetric(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, percentage: Int, modifier: Modifier = Modifier) {
    if (percentage == -1) return
    
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = getPercentageColor(percentage)
            LinearProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier.weight(1f).height(6.dp),
                color = color,
                trackColor = color.copy(alpha = 0.1f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

private fun getStrengthColor(strength: String): Color {
    return when (strength.lowercase()) {
        "exceptional", "very strong" -> Color(0xFF2E7D32)
        "strong" -> Color(0xFF4CAF50)
        "moderate" -> Color(0xFFF9A825)
        "weak" -> Color(0xFFEF6C00)
        "very weak" -> Color(0xFFC62828)
        else -> Color(0xFF757575)
    }
}

private fun getPercentageColor(percentage: Int): Color {
    return when {
        percentage >= 75 -> Color(0xFF4CAF50)
        percentage >= 50 -> Color(0xFF8BC34A)
        percentage >= 25 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
}

@Composable
private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    val lines = text.split('\n')
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("#### ") -> Text(
                    text = renderInlineMarkdown(trimmedLine.removePrefix("#### ")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                trimmedLine.startsWith("### ") -> Text(
                    text = renderInlineMarkdown(trimmedLine.removePrefix("### ")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                trimmedLine.startsWith("## ") -> Text(
                    text = renderInlineMarkdown(trimmedLine.removePrefix("## ")),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                trimmedLine.startsWith("# ") -> Text(
                    text = renderInlineMarkdown(trimmedLine.removePrefix("# ")),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                trimmedLine == "---" || trimmedLine == "***" -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                    Row {
                        Text("• ", style = style, color = color)
                        Text(text = renderInlineMarkdown(trimmedLine.substring(2)), style = style, color = color)
                    }
                }
                trimmedLine.firstOrNull()?.isDigit() == true && trimmedLine.contains(". ") -> {
                    val parts = trimmedLine.split(". ", limit = 2)
                    if (parts.size == 2 && parts[0].all { it.isDigit() }) {
                        Row {
                            Text("${parts[0]}. ", style = style, color = color)
                            Text(text = renderInlineMarkdown(parts[1]), style = style, color = color)
                        }
                    } else {
                        Text(text = renderInlineMarkdown(line), style = style, color = color)
                    }
                }
                trimmedLine.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> Text(text = renderInlineMarkdown(line), style = style, color = color)
            }
        }
    }
}

private fun renderInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0
        // Match bold (**text**), italic (*text*), and inline code (`code`)
        val regex = Regex("(\\*\\*.*?\\*\\*)|(\\*.*?\\*)|(`.*?`)")
        regex.findAll(text).forEach { match ->
            append(text.substring(lastIndex, match.range.first))
            val matchText = match.value
            when {
                matchText.startsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(matchText.substring(2, matchText.length - 2))
                    }
                }
                matchText.startsWith("*") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(matchText.substring(1, matchText.length - 1))
                    }
                }
                matchText.startsWith("`") -> {
                    withStyle(SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = Color.Black.copy(alpha = 0.08f)
                    )) {
                        append(matchText.substring(1, matchText.length - 1))
                    }
                }
            }
            lastIndex = match.range.last + 1
        }
        append(text.substring(lastIndex))
    }
}

@Composable
fun BlinkingDrishtiIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "blinking")
    
    // Pulse alpha for "thinking" state
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Periodic blink effect (flattening the eye shape)
    val blinkScaleY by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                1f at 0
                1f at 3200
                0.05f at 3350 // Fast close
                1f at 3500 // Open
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    // Horizontal scanning movement for the diamond
    val diamondScanX by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.alpha(alpha)) {
        val w = size.width
        val h = size.height
        val s = w / 108f
        val centerYOffset = (h - (108f * s)) / 2f
        val centerXOffset = (w - (108f * s)) / 2f

        // 1. The Logo Eye Shape (Outline)
        val eyeCenterY = 52f * s + centerYOffset
        val eyePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(30f * s + centerXOffset, eyeCenterY)
            cubicTo(
                40f * s + centerXOffset, (52f - (52f - 44f) * blinkScaleY) * s + centerYOffset,
                68f * s + centerXOffset, (52f - (52f - 44f) * blinkScaleY) * s + centerYOffset,
                78f * s + centerXOffset, eyeCenterY
            )
            lineTo(78f * s + centerXOffset, (52f + (56f - 52f) * blinkScaleY) * s + centerYOffset)
            cubicTo(
                68f * s + centerXOffset, (52f + (52f - 50f) * blinkScaleY) * s + centerYOffset,
                40f * s + centerXOffset, (52f + (52f - 50f) * blinkScaleY) * s + centerYOffset,
                30f * s + centerXOffset, (52f + (56f - 52f) * blinkScaleY) * s + centerYOffset
            )
            close()
        }
        drawPath(path = eyePath, color = primaryColor.copy(alpha = 0.4f))
        
        // 2. The Logo Pupil
        if (blinkScaleY > 0.3f) {
            drawCircle(
                color = primaryColor.copy(alpha = 0.7f),
                radius = 7f * s * blinkScaleY,
                center = Offset(48f * s + centerXOffset, 58f * s + centerYOffset)
            )
        }

        // 3. The Scanning Focus Diamond (Moving horizontally)
        val dx = diamondScanX * s
        val diamondPath = androidx.compose.ui.graphics.Path().apply {
            // Main diamond body
            moveTo((54f + dx) * s + centerXOffset, 24f * s + centerYOffset)
            lineTo((62f + dx) * s + centerXOffset, 58f * s + centerYOffset)
            lineTo((54f + dx) * s + centerXOffset, 90f * s + centerYOffset)
            lineTo((46f + dx) * s + centerXOffset, 58f * s + centerYOffset)
            close()

            // Top corner detail
            moveTo((54f + dx) * s + centerXOffset, 24f * s + centerYOffset)
            lineTo((56f + dx) * s + centerXOffset, 30f * s + centerYOffset)
            lineTo((54f + dx) * s + centerXOffset, 33f * s + centerYOffset)
            lineTo((52f + dx) * s + centerXOffset, 30f * s + centerYOffset)
            close()

            // Bottom corner detail
            moveTo((54f + dx) * s + centerXOffset, 90f * s + centerYOffset)
            lineTo((56f + dx) * s + centerXOffset, 84f * s + centerYOffset)
            lineTo((54f + dx) * s + centerXOffset, 81f * s + centerYOffset)
            lineTo((52f + dx) * s + centerXOffset, 84f * s + centerYOffset)
            close()
        }
        
        drawPath(
            path = diamondPath,
            color = primaryColor,
            style = Stroke(width = 2f * s)
        )
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Increased size from 16.dp to 24.dp to match visual weight of the text
                BlinkingDrishtiIcon(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "D\u1e5b\u1e63\u1e6di is looking beyond\u2026",
                    style = MaterialTheme.typography.labelSmall // Slightly larger typography for balance
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask D\u1e5b\u1e63\u1e6di anything\u2026") },
                enabled = enabled,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(
                onClick = { if (enabled && value.isNotBlank()) onSend() },
                containerColor = if (enabled && value.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (enabled && value.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ChatDrawerContent(
    conversations: List<Conversation>,
    currentConversationId: String?,
    onConversationSelected: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToKundali: () -> Unit,
    onNavigateToPanchang: () -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("D\u1e5b\u1e63\u1e6di", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("See beyond the obvious", style = MaterialTheme.typography.labelSmall)
            
            Spacer(Modifier.height(24.dp))
            
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                label = { Text("New Conversation") },
                selected = false,
                onClick = onNewConversation,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            
            Spacer(Modifier.height(16.dp))
            Text("Recent History", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            
            conversations.take(20).forEach { conv ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavigationDrawerItem(
                        label = { Text(conv.title ?: "Untitled Chat", maxLines = 1) },
                        selected = conv.id == currentConversationId,
                        onClick = { onConversationSelected(conv.id) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onDeleteConversation(conv.id) },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Spacer(Modifier.weight(1f))
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                label = { Text("Birth Chart (Kundali)") },
                selected = false,
                onClick = onNavigateToKundali
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                label = { Text("Panchang") },
                selected = false,
                onClick = onNavigateToPanchang
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                label = { Text("Profile") },
                selected = false,
                onClick = onNavigateToProfile
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = false,
                onClick = onNavigateToSettings
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    DrshtiTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                        label = { Text("Chat") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { },
                        icon = { Icon(Icons.Default.AddChart, contentDescription = "Decisions") },
                        label = { Text("Decisions") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Kundali") },
                        label = { Text("Kundali") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Panchang") },
                        label = { Text("Panchang") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(PreviewSamples.messages) { message ->
                            ChatBubble(message)
                        }
                    }
                }

                ChatInputBar(
                    value = "",
                    onValueChange = {},
                    onSend = {},
                    enabled = true
                )
            }
        }
    }
}


