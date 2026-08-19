package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.ai.provider.AiAstrologyService
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.database.dao.DashaDao
import com.pranav.drsti.database.dao.KundaliDao
import com.pranav.drsti.database.entity.DashaEntity
import com.pranav.drsti.database.entity.KundaliEntity
import com.pranav.drsti.database.entity.PersonEntity
import com.pranav.drsti.model.DashaData
import com.pranav.drsti.model.KundaliData
import com.pranav.drsti.model.PlanetName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class KundaliUiState(
    val activePerson: PersonEntity? = null,
    val kundali: KundaliData? = null,
    val dasha: DashaData? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/** Persisted Lagna Kundali (spec §18): calculated once, then read from Room, never recomputed on every screen open. */
class KundaliViewModel(
    private val personRepository: PersonRepository,
    private val kundaliDao: KundaliDao,
    private val dashaDao: DashaDao,
    private val aiServiceProvider: () -> AiAstrologyService
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(KundaliUiState())
    val state: StateFlow<KundaliUiState> = _state

    init {
        viewModelScope.launch {
            personRepository.observeActive().collect { person ->
                _state.value = _state.value.copy(activePerson = person)
                if (person != null) loadOrCompute(person)
            }
        }
    }

    private suspend fun loadOrCompute(person: PersonEntity) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val cachedKundali = kundaliDao.getLatest(person.id)
            val cachedDasha = dashaDao.getLatest(person.id)
            val kundali = if (cachedKundali != null) {
                json.decodeFromString(KundaliData.serializer(), cachedKundali.dataJson)
            } else {
                computeAndStoreKundali(person)
            }
            val moonSidereal = kundali.planets.firstOrNull { it.planet == PlanetName.MOON }?.let {
                it.sign.index * 30.0 + it.degreeInSign
            } ?: 0.0
            val dasha = if (cachedDasha != null) {
                json.decodeFromString(DashaData.serializer(), cachedDasha.dataJson)
            } else {
                computeAndStoreDasha(person, moonSidereal)
            }
            _state.value = _state.value.copy(kundali = kundali, dasha = dasha, isLoading = false)
        } catch (t: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = t.message ?: "Failed to compute Kundali.")
        }
    }

    private suspend fun computeAndStoreKundali(person: PersonEntity): KundaliData {
        val birthDate = LocalDate.parse(person.dateOfBirthIso)
        val birthTime = LocalTime.parse(person.timeOfBirthIso)
        val zoneId = ZoneId.of(person.timezone)
        val kundali = aiServiceProvider().calculateKundali(birthDate, birthTime, zoneId, person.latitude, person.longitude)
        kundaliDao.insert(
            KundaliEntity(
                personId = person.id,
                dataJson = json.encodeToString(kundali),
                calculationVersion = kundali.provenance.calculationVersion,
                generatedAt = kundali.provenance.generatedAt
            )
        )
        return kundali
    }

    private suspend fun computeAndStoreDasha(person: PersonEntity, moonSidereal: Double): DashaData {
        val birthDate = LocalDate.parse(person.dateOfBirthIso)
        val birthTime = LocalTime.parse(person.timeOfBirthIso)
        val zoneId = ZoneId.of(person.timezone)
        val dasha = aiServiceProvider().calculateDasha(birthDate, birthTime, zoneId, moonSidereal)
        dashaDao.insert(
            DashaEntity(
                personId = person.id,
                dataJson = json.encodeToString(dasha),
                calculationVersion = dasha.provenance.calculationVersion,
                generatedAt = dasha.provenance.generatedAt
            )
        )
        return dasha
    }

    fun recompute() = viewModelScope.launch {
        _state.value.activePerson?.let {
            _state.value = _state.value.copy(kundali = null, dasha = null)
            val kundali = computeAndStoreKundali(it)
            val moonSidereal = kundali.planets.firstOrNull { p -> p.planet == PlanetName.MOON }?.let { p ->
                p.sign.index * 30.0 + p.degreeInSign
            } ?: 0.0
            val dasha = computeAndStoreDasha(it, moonSidereal)
            _state.value = _state.value.copy(kundali = kundali, dasha = dasha)
        }
    }

    companion object {
        fun factory(
            personRepository: PersonRepository, kundaliDao: KundaliDao, dashaDao: DashaDao,
            aiServiceProvider: () -> AiAstrologyService
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                KundaliViewModel(personRepository, kundaliDao, dashaDao, aiServiceProvider) as T
        }
    }
}
