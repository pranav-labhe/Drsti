package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.database.entity.PersonEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PersonUiState(
    val people: List<PersonEntity> = emptyList(),
    val active: PersonEntity? = null
)

class PersonViewModel(private val repository: PersonRepository) : ViewModel() {

    private val _state = MutableStateFlow(PersonUiState())
    val state: StateFlow<PersonUiState> = _state

    init {
        viewModelScope.launch {
            repository.observeAll().collect { list -> _state.value = _state.value.copy(people = list) }
        }
        viewModelScope.launch {
            repository.observeActive().collect { p -> _state.value = _state.value.copy(active = p) }
        }
    }

    fun save(entity: PersonEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.save(entity)
            if (entity.isActive) repository.setActive(id)
            onSaved(id)
        }
    }

    fun setActive(id: Long) = viewModelScope.launch { repository.setActive(id) }
    fun delete(entity: PersonEntity) = viewModelScope.launch { repository.delete(entity) }

    companion object {
        fun factory(repository: PersonRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PersonViewModel(repository) as T
        }
    }
}
