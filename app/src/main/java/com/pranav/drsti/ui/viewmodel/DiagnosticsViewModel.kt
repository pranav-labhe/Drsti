package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.database.dao.AIRequestLogDao
import com.pranav.drsti.database.entity.AIRequestLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val logs: List<AIRequestLogEntity> = emptyList()
)

class DiagnosticsViewModel(private val dao: AIRequestLogDao) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state

    init {
        viewModelScope.launch {
            dao.observeRecent(100).collect { list ->
                _state.value = DiagnosticsUiState(logs = list)
            }
        }
    }

    fun clearLogs() = viewModelScope.launch {
        dao.clearAll()
    }

    companion object {
        fun factory(dao: AIRequestLogDao) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DiagnosticsViewModel(dao) as T
        }
    }
}
