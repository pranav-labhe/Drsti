package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.ai.provider.AiAstrologyService
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.data.repository.SettingsRepository
import com.pranav.drsti.database.dao.KundaliDao
import com.pranav.drsti.database.dao.PlanetaryPositionDao
import com.pranav.drsti.database.dao.TransitAnalysisDao
import com.pranav.drsti.database.entity.PersonEntity
import com.pranav.drsti.database.entity.PlanetaryPositionEntity
import com.pranav.drsti.database.entity.TransitAnalysisEntity
import com.pranav.drsti.model.*
import com.pranav.drsti.util.DateTimeUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class TransitUiState(
    val activePerson: PersonEntity? = null,
    val positions: PlanetaryPositionResult? = null,
    val analysis: TransitAnalysisResult? = null,
    val isLoading: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null
)

/**
 * Current planetary state + AI transit interpretation (spec §25-26).
 * Positions and analysis are cached according to freshness settings.
 */
class TransitViewModel(
    private val personRepository: PersonRepository,
    private val kundaliDao: KundaliDao,
    private val planetaryPositionDao: PlanetaryPositionDao,
    private val transitAnalysisDao: TransitAnalysisDao,
    private val settingsRepository: SettingsRepository,
    private val aiServiceProvider: () -> AiAstrologyService
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(TransitUiState())
    val state: StateFlow<TransitUiState> = _state

    init {
        viewModelScope.launch {
            personRepository.observeActive().collect { person ->
                _state.update { it.copy(activePerson = person) }
                if (person != null) refresh()
            }
        }
    }

    fun refresh(forceFetch: Boolean = false) = viewModelScope.launch {
        val person = _state.value.activePerson ?: return@launch
        _state.update { it.copy(isLoading = true, error = null) }
        
        try {
            val latitude = person.latitude
            val longitude = person.longitude
            val zoneId = person.timezone.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

            val freshnessMinutes = settingsRepository.getPlanetaryFreshnessMinutes()
            val latestPosEntity = planetaryPositionDao.getLatest()
            val isPosFresh = !forceFetch && latestPosEntity != null &&
                    Duration.between(Instant.parse(latestPosEntity.fetchedAt), Instant.now()).toMinutes() < freshnessMinutes

            val positions = if (isPosFresh) {
                json.decodeFromString(PlanetaryPositionResult.serializer(), latestPosEntity!!.dataJson)
            } else {
                val fresh = aiServiceProvider().calculatePlanetaryPositions(LocalDateTime.now(zoneId), zoneId, latitude, longitude)
                planetaryPositionDao.insert(
                    PlanetaryPositionEntity(
                        timestampUtc = fresh.timestampUtc, latitude = latitude, longitude = longitude,
                        dataJson = json.encodeToString(fresh), fetchedAt = Instant.now().toString()
                    )
                )
                fresh
            }

            // Check for cached analysis for this person
            val cachedAnalysis = if (!forceFetch) transitAnalysisDao.observeLatest(person.id).first() else null
            
            val analysis = if (cachedAnalysis != null && isPosFresh) {
                json.decodeFromString(TransitAnalysisResult.serializer(), cachedAnalysis.dataJson)
            } else {
                val kundaliEntity = kundaliDao.getLatest(person.id)
                val kundali = kundaliEntity?.let { json.decodeFromString(KundaliData.serializer(), it.dataJson) }
                
                val context = AiRequestContext(
                    currentTime = DateTimeUtil.currentContext(zoneId).let {
                        CurrentTimeContext(it.localDate, it.localTime, it.localTimestamp, it.utcTimestamp, it.zoneId, it.utcOffset)
                    },
                    latitude = latitude, longitude = longitude, personName = person.name,
                    kundali = kundali, dasha = null, panchang = null, planetaryPositions = positions
                )
                val freshAnalysis = aiServiceProvider().analyzeTransits(context)
                transitAnalysisDao.insert(
                    TransitAnalysisEntity(
                        personId = person.id,
                        dataJson = json.encodeToString(freshAnalysis),
                        generatedAt = Instant.now().toString()
                    )
                )
                freshAnalysis
            }

            _state.update { it.copy(positions = positions, analysis = analysis, isLoading = false, fromCache = isPosFresh) }
        } catch (t: Throwable) {
            _state.update { it.copy(isLoading = false, error = t.message ?: "Could not load transits.") }
        }
    }

    companion object {
        fun factory(
            personRepository: PersonRepository,
            kundaliDao: KundaliDao,
            planetaryPositionDao: PlanetaryPositionDao,
            transitAnalysisDao: TransitAnalysisDao,
            settingsRepository: SettingsRepository,
            aiServiceProvider: () -> AiAstrologyService
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TransitViewModel(personRepository, kundaliDao, planetaryPositionDao, transitAnalysisDao, settingsRepository, aiServiceProvider) as T
        }
    }
}
