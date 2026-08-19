package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.data.repository.PlaceRepository
import com.pranav.drsti.database.entity.PlaceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlaceUiState(
    val allPlaces: List<PlaceEntity> = emptyList(),
    val searchResults: List<PlaceEntity> = emptyList(),
    val query: String = ""
)

class PlaceViewModel(private val repository: PlaceRepository) : ViewModel() {

    private val _state = MutableStateFlow(PlaceUiState())
    val state: StateFlow<PlaceUiState> = _state

    init {
        viewModelScope.launch {
            repository.observeAll().collect { list -> _state.value = _state.value.copy(allPlaces = list, searchResults = list) }
        }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        viewModelScope.launch {
            val results = if (query.isBlank()) _state.value.allPlaces else repository.search(query)
            _state.value = _state.value.copy(searchResults = results)
        }
    }

    fun save(entity: PlaceEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch { onSaved(repository.save(entity)) }
    }

    fun delete(entity: PlaceEntity) = viewModelScope.launch { repository.delete(entity) }

    companion object {
        fun factory(repository: PlaceRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaceViewModel(repository) as T
        }
    }
}
