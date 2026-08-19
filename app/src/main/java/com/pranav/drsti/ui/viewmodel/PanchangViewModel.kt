package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.ai.provider.AiAstrologyService
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.database.dao.PanchangDao
import com.pranav.drsti.database.entity.PanchangEntity
import com.pranav.drsti.database.entity.PersonEntity
import com.pranav.drsti.model.PanchangData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

data class PanchangUiState(
    val date: LocalDate = LocalDate.now(),
    val panchang: PanchangData? = null,
    val isLoading: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null,
    val locationLabel: String? = null
)

/**
 * Panchang cache keyed on date+location+timezone+system+calc version (spec §20-22, §40).
 * Never re-calls the calculator when a valid cached result already exists for that key.
 */
class PanchangViewModel(
    private val personRepository: PersonRepository,
    private val panchangDao: PanchangDao,
    private val aiServiceProvider: () -> AiAstrologyService
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(PanchangUiState())
    val state: StateFlow<PanchangUiState> = _state

    private var activePerson: PersonEntity? = null
    private val calculationVersion = "astrocalc-1.0"

    init {
        viewModelScope.launch {
            personRepository.observeActive().collect { person ->
                activePerson = person
                _state.value = _state.value.copy(locationLabel = person?.let { "lat ${it.latitude}, lon ${it.longitude}" })
                loadFor(_state.value.date)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
        viewModelScope.launch { loadFor(date) }
    }

    fun refresh() = viewModelScope.launch { loadFor(_state.value.date, forceRegenerate = true) }

    private suspend fun loadFor(date: LocalDate, forceRegenerate: Boolean = false) {
        val person = activePerson
        val latitude = person?.latitude ?: DEFAULT_LATITUDE
        val longitude = person?.longitude ?: DEFAULT_LONGITUDE
        val zoneId = person?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

        _state.value = _state.value.copy(isLoading = true, error = null)
        val cacheKey = "$date|$latitude|$longitude|$zoneId|VEDIC-SIDEREAL-LAHIRI|$calculationVersion"

        if (!forceRegenerate) {
            val cached = panchangDao.getByCacheKey(cacheKey)
            if (cached != null) {
                val data = json.decodeFromString(PanchangData.serializer(), cached.dataJson)
                _state.value = _state.value.copy(panchang = data, isLoading = false, fromCache = true)
                return
            }
        }

        try {
            val data = aiServiceProvider().generatePanchang(date, zoneId, latitude, longitude)
            panchangDao.upsert(
                PanchangEntity(
                    cacheKey = cacheKey, dateIso = date.toString(), latitude = latitude, longitude = longitude,
                    dataJson = json.encodeToString(data), generatedAt = data.provenance.generatedAt
                )
            )
            _state.value = _state.value.copy(panchang = data, isLoading = false, fromCache = false)
        } catch (t: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = t.message ?: "Could not calculate Panchang.")
        }
    }

    companion object {
        // Default to New Delhi if no profile/location is set yet, so the screen is never empty.
        private const val DEFAULT_LATITUDE = 28.6139
        private const val DEFAULT_LONGITUDE = 77.2090

        fun factory(personRepository: PersonRepository, panchangDao: PanchangDao, aiServiceProvider: () -> AiAstrologyService) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PanchangViewModel(personRepository, panchangDao, aiServiceProvider) as T
            }
    }
}
