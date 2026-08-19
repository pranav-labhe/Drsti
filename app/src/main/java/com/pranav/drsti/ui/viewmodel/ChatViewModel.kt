package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.ai.provider.AiAstrologyService
import com.pranav.drsti.data.repository.ChatRepository
import com.pranav.drsti.data.repository.PersonRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
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
    val activePerson: PersonEntity? = null
)

/**
 * Backs the Chat / Home screen (spec §4-6). Builds only the minimum
 * relevant AiRequestContext for each message (ContextBuilder responsibility,
 * spec §6) rather than shipping the whole database to the AI.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val personRepository: PersonRepository,
    private val panchangDao: PanchangDao,
    private val kundaliDao: KundaliDao,
    private val dashaDao: DashaDao,
    private val planetaryPositionDao: PlanetaryPositionDao,
    private val aiServiceProvider: () -> AiAstrologyService
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

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
        // Load default conversation and observe its messages
        viewModelScope.launch {
            val conversation = chatRepository.getOrCreateDefaultConversation()
            _state.update { it.copy(conversationId = conversation.id) }
            chatRepository.observeRecent(conversation.id).collect { msgs ->
                _state.update { it.copy(messages = msgs.reversed()) }
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

    /** Create a new conversation (no person association needed). */
    fun createConversation(title: String = "Conversation ${System.currentTimeMillis()}") {
        viewModelScope.launch {
            val now = java.time.Instant.now().toString()
            val entity = ConversationEntity(title = title, createdAt = now, updatedAt = now)
            val newId = chatRepository.createConversation(entity)
            _state.update { it.copy(conversationId = newId) }
        }
    }

    /** Select an existing conversation by its string ID. */
    fun selectConversation(conversationId: String) {
        viewModelScope.launch {
            conversationId.toLongOrNull()?.let { id ->
                _state.update { it.copy(conversationId = id) }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val conversationId = _state.value.conversationId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            _error.value = null
            chatRepository.appendMessage(conversationId, "user", text)

            runCatching {
                val context = buildContext()
                aiServiceProvider().chat(context, text)
            }.onSuccess { reply ->
                chatRepository.appendMessage(conversationId, "assistant", reply.text, reply.intent)
            }.onFailure { t ->
                _error.value = "Failed to get a response: ${t.message}"
            }

            _state.update { it.copy(isSending = false) }
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
            aiServiceProvider: () -> AiAstrologyService
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(chatRepository, personRepository, panchangDao, kundaliDao, dashaDao, planetaryPositionDao, aiServiceProvider) as T
        }
    }
}

// Removed duplicate companion object and incorrect factory that referenced undefined AiProvider.
