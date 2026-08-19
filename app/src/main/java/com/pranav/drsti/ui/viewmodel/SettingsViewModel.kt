package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.data.repository.SettingsRepository
import com.pranav.drsti.database.DrishtiDatabase
import com.pranav.drsti.di.ServiceLocator
import com.pranav.drsti.util.SecureCredentialStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val aiMode: String = "MOCK",
    val aiModel: String = "gpt-4o-mini",
    val hasApiKey: Boolean = false,
    val apiKey: String = "",
    val hasGeminiApiKey: Boolean = false,
    val geminiApiKey: String = "",
    val planetaryFreshnessMinutes: Int = 15,
    val diagnosticsEnabled: Boolean = true,
    val saveMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secureStorage: SecureCredentialStorage,
    private val serviceLocator: ServiceLocator
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            val openAiKey = secureStorage.getApiKey() ?: ""
            val geminiKey = secureStorage.getGeminiApiKey() ?: ""
            _state.value = SettingsUiState(
                aiMode = settingsRepository.getAiMode(),
                aiModel = settingsRepository.getAiModel(),
                hasApiKey = openAiKey.isNotBlank(),
                apiKey = openAiKey,
                hasGeminiApiKey = geminiKey.isNotBlank(),
                geminiApiKey = geminiKey,
                planetaryFreshnessMinutes = settingsRepository.getPlanetaryFreshnessMinutes(),
                diagnosticsEnabled = settingsRepository.getDiagnosticsEnabled()
            )
        }
    }

    fun setAiMode(mode: String) = viewModelScope.launch {
        settingsRepository.setAiMode(mode)
        serviceLocator.refreshAiProviderFromSettings()
        _state.value = _state.value.copy(aiMode = mode, saveMessage = "AI mode updated.")
    }

    fun setAiModel(model: String) = viewModelScope.launch {
        settingsRepository.setAiModel(model)
        serviceLocator.refreshAiProviderFromSettings()
        _state.value = _state.value.copy(aiModel = model)
    }

    fun saveApiKey(key: String) = viewModelScope.launch {
        if (key.isBlank()) secureStorage.clearApiKey() else secureStorage.saveApiKey(key)
        serviceLocator.refreshAiProviderFromSettings()
        _state.value = _state.value.copy(
            hasApiKey = key.isNotBlank(),
            apiKey = key,
            saveMessage = "OpenAI API key saved."
        )
    }

    fun saveGeminiApiKey(key: String) = viewModelScope.launch {
        if (key.isBlank()) secureStorage.clearGeminiApiKey() else secureStorage.saveGeminiApiKey(key)
        serviceLocator.refreshAiProviderFromSettings()
        _state.value = _state.value.copy(
            hasGeminiApiKey = key.isNotBlank(),
            geminiApiKey = key,
            saveMessage = "Gemini API key saved."
        )
    }

    fun clearMessage() {
        _state.value = _state.value.copy(saveMessage = null)
    }

    fun setPlanetaryFreshness(minutes: Int) = viewModelScope.launch {
        settingsRepository.setPlanetaryFreshnessMinutes(minutes)
        _state.value = _state.value.copy(planetaryFreshnessMinutes = minutes)
    }

    fun setDiagnosticsEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDiagnosticsEnabled(enabled)
        _state.value = _state.value.copy(diagnosticsEnabled = enabled)
    }

    fun clearCache(database: DrishtiDatabase) = viewModelScope.launch {
        database.runInTransaction {
            viewModelScope.launch {
                database.panchangDao().clearAll()
                database.kundaliDao().clearAll()
                database.dashaDao().clearAll()
                database.planetaryPositionDao().clearAll()
                database.transitAnalysisDao().clearAll()
                _state.update { it.copy(saveMessage = "Local astrological cache cleared.") }
            }
        }
    }

    companion object {
        fun factory(settingsRepository: SettingsRepository, secureStorage: SecureCredentialStorage, serviceLocator: ServiceLocator) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(settingsRepository, secureStorage, serviceLocator) as T
            }
    }
}
