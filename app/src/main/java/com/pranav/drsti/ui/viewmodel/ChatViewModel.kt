package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.ai.provider.AiAstrologyService
import com.pranav.drsti.data.repository.ChatRepository
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.data.repository.DecisionRepository
import com.pranav.drsti.database.dao.DashaDao
import com.pranav.drsti.database.dao.KundaliDao
import com.pranav.drsti.database.dao.PanchangDao
import com.pranav.drsti.database.dao.PlanetaryPositionDao
import com.pranav.drsti.database.entity.ConversationMessageEntity
import com.pranav.drsti.database.entity.PersonEntity
import com.pranav.drsti.database.entity.ConversationEntity
import com.pranav.drsti.model.*
import com.pranav.drsti.util.DateTimeUtil
import com.pranav.drsti.model.CurrentTimeContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant

data class ChatUiState(
    val messages: List<ConversationMessageEntity> = emptyList(),
    val isSending: Boolean = false,
    val conversationId: Long? = null,
    val activePerson: PersonEntity? = null,
    val conversationState: ConversationState? = null
)

/**
 * Backs the Chat / Home screen (spec §4-6). Builds only the minimum
 * relevant AiRequestContext for each message (ContextBuilder responsibility,
 * spec §6) rather than shipping the whole database to the AI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val personRepository: PersonRepository,
    private val panchangDao: PanchangDao,
    private val kundaliDao: KundaliDao,
    private val dashaDao: DashaDao,
    private val planetaryPositionDao: PlanetaryPositionDao,
    private val decisionRepository: DecisionRepository,
    private val aiServiceProvider: () -> AiAstrologyService
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    private val _savedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    val savedMessageIds: StateFlow<Set<Long>> = _savedMessageIds

    fun isMessageSaved(id: Long): Boolean = _savedMessageIds.value.contains(id)

    // Additional public flows expected by the UI layer (HomeScreen)
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    // Expose UI‑friendly StateFlows using stateIn so the UI can collect them directly.
    val currentConversationId: StateFlow<String?> = _state.map { it.conversationId?.toString() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val messages: StateFlow<List<ChatMessage>> = _state.map { ui ->
        ui.messages.map { msg ->
            ChatMessage(
                id = msg.id,
                conversationId = "${msg.conversationId}",
                role = msg.role,
                content = msg.content,
                timestamp = msg.timestamp,
                intent = msg.intent,
                contextSnapshotRef = msg.contextSnapshotRef
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isLoadingMessages: StateFlow<Boolean> = _state.map { it.isSending }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Placeholder for pagination loading state – not implemented yet
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Load default conversation
        viewModelScope.launch {
            val conversation = chatRepository.getOrCreateDefaultConversation()
            // Only set if not already set by a deep-link/shared prompt
            _state.update { 
                if (it.conversationId == null) it.copy(conversationId = conversation.id) else it 
            }
        }

        // Observe messages for the current conversation
        viewModelScope.launch {
            _state.map { it.conversationId }
                .flatMapLatest { id ->
                    if (id != null) chatRepository.observeRecent(id) else emptyFlow()
                }.collect { msgs ->
                    _state.update { it.copy(messages = msgs.reversed()) }
                }
        }

        // Observe conversation state
        viewModelScope.launch {
            _state.map { it.conversationId }
                .flatMapLatest { id ->
                    if (id != null) chatRepository.observeState(id) else emptyFlow()
                }.collect { convState ->
                    _state.update { it.copy(conversationState = convState) }
                }
        }

        // Observe active person
        viewModelScope.launch {
            personRepository.observeActive().collect { person ->
                _state.update { it.copy(activePerson = person) }
            }
        }
        // Observe conversation list
        viewModelScope.launch {
            chatRepository.observeConversations().collect { convs ->
                _conversations.value = convs.map { conv ->
                    Conversation(
                        id = conv.id.toString(),
                        personId = null,
                        title = conv.title,
                        createdAt = conv.createdAt,
                        updatedAt = conv.updatedAt
                    )
                }
            }
        }
    }

    /** Load conversations (currently loads all as placeholder). */
    fun loadConversations() {
        // No additional action needed; conversations are already observed in init.
    }

    /** Create a new conversation. */
    fun createConversation(title: String = "New Chat") {
        viewModelScope.launch {
            val now = java.time.Instant.now().toString()
            val entity = ConversationEntity(title = title, createdAt = now, updatedAt = now)
            val newId = chatRepository.createConversation(entity)
            _state.update { it.copy(conversationId = newId, messages = emptyList()) }
        }
    }

    /** Select an existing conversation by its string ID. */
    fun selectConversation(conversationId: String) {
        conversationId.toLongOrNull()?.let { id ->
            _state.update { it.copy(conversationId = id) }
        }
    }

    /** Handle deep-linked or shared prompt from other screens. */
    fun onSharedPrompt(text: String, title: String = "Insight") {
        viewModelScope.launch {
            // 1. Create the new conversation synchronously
            val now = java.time.Instant.now().toString()
            val newId = chatRepository.createConversation(
                ConversationEntity(title = title, createdAt = now, updatedAt = now)
            )
            
            // 2. Clear state and lock in the new ID
            _state.update { it.copy(conversationId = newId, messages = emptyList()) }
            
            // 3. Trigger the message flow (sendMessage will use the newId from state)
            sendMessage(text)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            id.toLongOrNull()?.let { longId ->
                chatRepository.deleteConversation(longId)
                if (_state.value.conversationId == longId) {
                    val next = chatRepository.getOrCreateDefaultConversation()
                    _state.update { it.copy(conversationId = next.id) }
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val conversationId = _state.value.conversationId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            _error.value = null
            
            val person = _state.value.activePerson

            val result = runCatching {
                // Perform database operations and AI call on IO thread
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // If this is the first user message, update the conversation title
                    if (chatRepository.observeRecent(conversationId, 1).first().none { it.role == "user" }) {
                        val title = if (text.length > 30) text.take(27) + "..." else text
                        chatRepository.updateConversationTitle(conversationId, title)
                    }

                    chatRepository.appendMessage(conversationId, "user", text)
                    
                    val context = buildContext()
                    val reply = aiServiceProvider().chat(context, text, conversationId)
                    
                    chatRepository.appendMessage(conversationId, "assistant", reply.text, reply.intent)
                    
                    // Handle structured decision if present
                    reply.decisionAnalysis?.let { analysis ->
                        val options = analysis.options.map { DecisionOptionInput(it.id, it.explanation) }
                        
                        decisionRepository.createDecision(
                            personId = person?.id ?: 0L,
                            question = text,
                            options = options,
                            context = analysis.analysisSummary,
                            desiredDate = null,
                            aiContext = context,
                            conversationId = conversationId
                        )
                    }
                    reply
                }
            }

            result.onFailure { t ->
                _error.value = "Failed to get a response: ${t.localizedMessage ?: t.message}"
            }

            _state.update { it.copy(isSending = false) }
        }
    }

    fun saveDecisionFromChat(messageId: Long, analysis: DecisionAnalysis, question: String) {
        if (_savedMessageIds.value.contains(messageId)) return
        val conversationId = _state.value.conversationId ?: return
        val person = _state.value.activePerson ?: return
        
        viewModelScope.launch {
            val aiContext = buildContext()
            val options = analysis.options.map { DecisionOptionInput(it.id, it.explanation) }
            
            decisionRepository.createDecision(
                personId = person.id,
                question = question,
                options = options,
                context = analysis.analysisSummary,
                desiredDate = null,
                aiContext = aiContext,
                conversationId = conversationId
            )
            
            _savedMessageIds.update { it + messageId }
        }
    }

    /** ContextBuilder (spec §6): only what's relevant for general chat — active person + a little recent history. */
    private suspend fun buildContext(): AiRequestContext {
        val person = _state.value.activePerson
        val recent = _state.value.messages.takeLast(10).map { "${it.role}: ${it.content}" }
        // Convert the util's CurrentTimeContext to the model's type expected by AiRequestContext.
        val utilCtx = DateTimeUtil.currentContext()
        val modelCtx = CurrentTimeContext(
            localDate = utilCtx.localDate,
            localTime = utilCtx.localTime,
            localTimestamp = utilCtx.localTimestamp,
            utcTimestamp = utilCtx.utcTimestamp,
            zoneId = utilCtx.zoneId,
            utcOffset = utilCtx.utcOffset
        )

        // Try to fetch today's Panchang, latest Kundali, Dasha, and Planets from cache for context
        var panchang: PanchangData? = null
        var kundali: KundaliData? = null
        var dasha: DashaData? = null
        var positions: PlanetaryPositionResult? = null

        if (person != null) {
            val date = LocalDate.now()
            val latitude = person.latitude
            val longitude = person.longitude
            val zoneId = person.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            val cacheKey = "$date|$latitude|$longitude|$zoneId|VEDIC-SIDEREAL-LAHIRI|astrocalc-1.0"
            
            val cachedPanchang = panchangDao.getByCacheKey(cacheKey)
            if (cachedPanchang != null) {
                panchang = runCatching { json.decodeFromString(PanchangData.serializer(), cachedPanchang.dataJson) }.getOrNull()
            }

            val cachedKundali = kundaliDao.getLatest(person.id)
            if (cachedKundali != null) {
                kundali = runCatching { json.decodeFromString(KundaliData.serializer(), cachedKundali.dataJson) }.getOrNull()
            }

            val cachedDasha = dashaDao.getLatest(person.id)
            if (cachedDasha != null) {
                dasha = runCatching { json.decodeFromString(DashaData.serializer(), cachedDasha.dataJson) }.getOrNull()
            }

            val cachedPlanets = planetaryPositionDao.getLatest()
            if (cachedPlanets != null) {
                positions = runCatching { json.decodeFromString(PlanetaryPositionResult.serializer(), cachedPlanets.dataJson) }.getOrNull()
            }
        }

        return AiRequestContext(
            currentTime = modelCtx,
            latitude = person?.latitude,
            longitude = person?.longitude,
            personName = person?.name,
            kundali = kundali, dasha = dasha, panchang = panchang, planetaryPositions = positions,
            recentMessages = recent
        )
    }

    companion object {
        fun factory(
            chatRepository: ChatRepository,
            personRepository: PersonRepository,
            panchangDao: PanchangDao,
            kundaliDao: KundaliDao,
            dashaDao: DashaDao,
            planetaryPositionDao: PlanetaryPositionDao,
            decisionRepository: DecisionRepository,
            aiServiceProvider: () -> AiAstrologyService
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(
                    chatRepository, personRepository, panchangDao, kundaliDao, dashaDao, 
                    planetaryPositionDao, decisionRepository, aiServiceProvider
                ) as T
        }
    }
}

// Removed duplicate companion object and incorrect factory that referenced undefined AiProvider.
