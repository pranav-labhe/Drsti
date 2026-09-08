package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.data.repository.PlaceRepository
import com.pranav.drsti.database.entity.PlaceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaceUiState(
    val allPlaces: List<PlaceEntity> = emptyList(),
    val searchResults: List<PlaceEntity> = emptyList(),
    val onlineResults: List<PlaceEntity> = emptyList(),
    val query: String = "",
    val isSearchingOnline: Boolean = false
)

class PlaceViewModel(private val repository: PlaceRepository) : ViewModel() {

    private val _state = MutableStateFlow(PlaceUiState())
    val state: StateFlow<PlaceUiState> = _state

    init {
        viewModelScope.launch {
            repository.observeAll().collect { list -> 
                _state.update { it.copy(allPlaces = list, searchResults = if (it.query.isBlank()) list else it.searchResults) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        viewModelScope.launch {
            val results = if (query.isBlank()) _state.value.allPlaces else repository.search(query)
            _state.update { it.copy(searchResults = results) }
        }
    }

    fun searchOnline() {
        val query = _state.value.query
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _state.update { it.copy(isSearchingOnline = true) }
            val results = repository.searchOnline(query)
            _state.update { it.copy(onlineResults = results, isSearchingOnline = false) }
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
