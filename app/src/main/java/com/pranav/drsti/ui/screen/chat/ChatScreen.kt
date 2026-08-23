package com.pranav.drsti.ui.screen.chat

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
import androidx.compose.ui.graphics.Color
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
import com.pranav.drsti.ui.PreviewSamples
import com.pranav.drsti.ui.theme.DrshtiTheme
import com.pranav.drsti.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

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
            "\"Give me a clearer view of the path; let me make the choice.\"",
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
private fun TypingIndicator() {
    Row(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp
        ) {
            Text(
                "D\u1e5b\u1e63\u1e6di is thinking\u2026",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall
            )
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
            Text("Personal Vedic Companion", style = MaterialTheme.typography.labelSmall)
            
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


