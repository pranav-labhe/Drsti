package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.ai.provider.AiAstrologyService
import com.pranav.drsti.data.repository.DecisionRepository
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.database.dao.DashaDao
import com.pranav.drsti.database.dao.KundaliDao
import com.pranav.drsti.database.dao.PanchangDao
import com.pranav.drsti.database.dao.PlanetaryPositionDao
import com.pranav.drsti.database.entity.DecisionEntity
import com.pranav.drsti.database.entity.DecisionOutcomeEntity
import com.pranav.drsti.database.entity.PersonEntity
import com.pranav.drsti.model.*
import com.pranav.drsti.util.DateTimeUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

data class DecisionDetail(
    val decision: DecisionEntity,
    val analysis: DecisionAnalysis?,
    val outcome: DecisionOutcomeEntity?,
    val outcomeAnalysis: OutcomeAnalysis?
)

data class DecisionUiState(
    val decisions: List<DecisionEntity> = emptyList(),
    val selectedDetail: DecisionDetail? = null,
    val isBusy: Boolean = false,
    val error: String? = null
)

class DecisionViewModel(
    private val decisionRepository: DecisionRepository,
    private val personRepository: PersonRepository,
    private val panchangDao: PanchangDao,
    private val kundaliDao: KundaliDao,
    private val dashaDao: DashaDao,
    private val planetaryPositionDao: PlanetaryPositionDao,
    private val aiServiceProvider: () -> AiAstrologyService
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(DecisionUiState())
    val state: StateFlow<DecisionUiState> = _state

    private var activePerson: PersonEntity? = null

    private val _selectedDecisionId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDetail: StateFlow<DecisionDetail?> = _selectedDecisionId
        .flatMapLatest { id ->
            if (id == null) flowOf<DecisionDetail?>(null)
            else {
                combine(
                    decisionRepository.observeDecision(id),
                    decisionRepository.observeAnalyses(id),
                    decisionRepository.observeOutcome(id),
                    decisionRepository.observeOutcomeAnalysis(id)
                ) { decision, analyses, outcome, outcomeAnalysis ->
                    if (decision == null) null
                    else {
                        val latestAnalysis = analyses.firstOrNull()?.let {
                            json.decodeFromString(DecisionAnalysis.serializer(), it.analysisJson)
                        }
                        val parsedOA = outcomeAnalysis?.let {
                            json.decodeFromString(OutcomeAnalysis.serializer(), it.analysisJson)
                        }
                        DecisionDetail(decision, latestAnalysis, outcome, parsedOA)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            personRepository.observeActive().collect { person ->
                activePerson = person
            }
        }
        viewModelScope.launch {
            decisionRepository.observeAll().collect { list ->
                _state.update { it.copy(decisions = list) }
            }
        }
        
        // Sync selectedDetail into the state for UI compatibility
        viewModelScope.launch {
            selectedDetail.collect { detail ->
                _state.update { it.copy(selectedDetail = detail) }
            }
        }
    }

    fun openDecision(id: Long) {
        _selectedDecisionId.value = id
    }

    fun closeDetail() {
        _selectedDecisionId.value = null
    }

    fun createDecision(
        question: String,
        options: List<DecisionOptionInput>,
        context: String?,
        desiredDate: String?
    ) = viewModelScope.launch {
        val person = activePerson ?: return@launch _state.update { it.copy(error = "No active profile set") }
        _state.update { it.copy(isBusy = true, error = null) }
        try {
            val aiContext = buildAiContext(person)
            decisionRepository.createDecision(person.id, question, options, context, desiredDate, aiContext)
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message) }
        } finally {
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun recordSelection(decisionId: Long, optionId: String) = viewModelScope.launch {
        decisionRepository.recordSelection(decisionId, optionId)
    }

    private val _refreshEvent = MutableSharedFlow<Unit>(replay = 0)
    val refreshEvent: SharedFlow<Unit> = _refreshEvent

    fun refreshAnalysis(decisionId: Long) = viewModelScope.launch {
        val person = activePerson ?: return@launch _state.update { it.copy(error = "No active profile set") }
        val decision = decisionRepository.getDecision(decisionId) ?: return@launch
        val options: List<DecisionOptionInput> = json.decodeFromString(decision.optionsJson)

        _state.update { it.copy(isBusy = true, error = null) }
        try {
            val aiContext = buildAiContext(person)
            decisionRepository.createDecision(
                personId = person.id,
                question = decision.question,
                options = options,
                context = decision.context,
                desiredDate = decision.desiredDecisionDateIso,
                aiContext = aiContext
            )
            _refreshEvent.emit(Unit)
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message) }
        } finally {
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun recordOutcome(
        decisionId: Long,
        description: String,
        assessment: String,
        notes: String?
    ) = viewModelScope.launch {
        _state.update { it.copy(isBusy = true, error = null) }
        try {
            decisionRepository.recordOutcome(decisionId, description, assessment, notes)
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message) }
        } finally {
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun deleteDecision(id: Long) = viewModelScope.launch {
        decisionRepository.deleteDecision(id)
    }

    private suspend fun buildAiContext(person: PersonEntity): AiRequestContext {
        val utilCtx = DateTimeUtil.currentContext()
        val modelCtx = CurrentTimeContext(
            localDate = utilCtx.localDate,
            localTime = utilCtx.localTime,
            localTimestamp = utilCtx.localTimestamp,
            utcTimestamp = utilCtx.utcTimestamp,
            zoneId = utilCtx.zoneId,
            utcOffset = utilCtx.utcOffset
        )

        var panchang: PanchangData? = null
        var kundali: KundaliData? = null
        var dasha: DashaData? = null
        var positions: PlanetaryPositionResult? = null
        
        val date = LocalDate.now()
        val zoneId = person.timezone.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val cacheKey = "$date|${person.latitude}|${person.longitude}|$zoneId|VEDIC-SIDEREAL-LAHIRI|astrocalc-1.0"
        
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

        return AiRequestContext(
            currentTime = modelCtx,
            latitude = person.latitude,
            longitude = person.longitude,
            personName = person.name,
            kundali = kundali, dasha = dasha, panchang = panchang, planetaryPositions = positions
        )
    }

    companion object {
        fun factory(
            decisionRepository: DecisionRepository,
            personRepository: PersonRepository,
            panchangDao: PanchangDao,
            kundaliDao: KundaliDao,
            dashaDao: DashaDao,
            planetaryPositionDao: PlanetaryPositionDao,
            aiServiceProvider: () -> AiAstrologyService
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DecisionViewModel(decisionRepository, personRepository, panchangDao, kundaliDao, dashaDao, planetaryPositionDao, aiServiceProvider) as T
        }
    }
}
