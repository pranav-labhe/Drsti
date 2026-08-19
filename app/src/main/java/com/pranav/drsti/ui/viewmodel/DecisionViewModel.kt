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
    }

    fun openDecision(id: Long) = viewModelScope.launch {
        _state.update { it.copy(isBusy = true, error = null) }
        try {
            val decision = decisionRepository.getDecision(id) ?: error("Decision not found")
            val analyses = decisionRepository.observeAnalyses(id).first()
            val latestAnalysis = analyses.firstOrNull()?.let { 
                json.decodeFromString(DecisionAnalysis.serializer(), it.analysisJson)
            }
            val outcome = decisionRepository.getOutcome(id)
            val outcomeAnalysis = decisionRepository.getOutcomeAnalysis(id)?.let {
                json.decodeFromString(OutcomeAnalysis.serializer(), it.analysisJson)
            }
            
            _state.update { it.copy(
                selectedDetail = DecisionDetail(decision, latestAnalysis, outcome, outcomeAnalysis),
                isBusy = false
            )}
        } catch (t: Throwable) {
            _state.update { it.copy(isBusy = false, error = t.message) }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(selectedDetail = null) }
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
            _state.update { it.copy(isBusy = false) }
        } catch (t: Throwable) {
            _state.update { it.copy(isBusy = false, error = t.message) }
        }
    }

    fun recordSelection(decisionId: Long, optionId: String) = viewModelScope.launch {
        decisionRepository.recordSelection(decisionId, optionId)
        // Refresh detail
        openDecision(decisionId)
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
            openDecision(decisionId)
        } catch (t: Throwable) {
            _state.update { it.copy(isBusy = false, error = t.message) }
        }
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
