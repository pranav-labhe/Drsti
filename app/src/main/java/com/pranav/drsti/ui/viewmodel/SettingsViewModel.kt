package com.pranav.drsti.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pranav.drsti.data.repository.SettingsRepository
import com.pranav.drsti.database.DrishtiDatabase
import com.pranav.drsti.di.ServiceLocator
import com.pranav.drsti.util.SecureCredentialStorage
import com.pranav.drsti.ai.provider.GeminiAiProvider
import com.pranav.drsti.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SettingsUiState(
    val aiMode: String = "MOCK",
    val aiModel: String = "gpt-4o-mini",
    // List of available Gemini models fetched from the API
    val geminiModelList: List<String> = emptyList(),
    // Loading indicator for fetching Gemini models
    val geminiModelsLoading: Boolean = false,
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
            // If Gemini mode is active and we have an API key, fetch the model list
            val current = _state.value
            if (current.aiMode == "GEMINI" && current.geminiApiKey.isNotBlank()) {
                loadGeminiModels(current.geminiApiKey)
            }
        }
    }

    fun setAiMode(mode: String) = viewModelScope.launch {
        settingsRepository.setAiMode(mode)
        serviceLocator.refreshAiProviderFromSettings()
        _state.value = _state.value.copy(aiMode = mode, saveMessage = "AI mode updated.")
        // If switched to GEMINI and we have a key, fetch the model list
        if (mode == "GEMINI" && _state.value.geminiApiKey.isNotBlank()) {
            loadGeminiModels(_state.value.geminiApiKey)
        }
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
        // After saving the key, fetch the available models
        if (key.isNotBlank()) {
            loadGeminiModels(key)
        }
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

    suspend fun exportBackup(): String {
        val data = BackupData(
            people = serviceLocator.database.personDao().getAll(),
            places = serviceLocator.database.placeDao().getAll(),
            settings = serviceLocator.database.appSettingsDao().getAll(),
            conversations = serviceLocator.database.conversationDao().getAll(),
            messages = serviceLocator.database.conversationMessageDao().getAll(),
            decisions = serviceLocator.database.decisionDao().getAll(),
            decisionAnalyses = serviceLocator.database.decisionAnalysisDao().getAll(),
            decisionOutcomes = serviceLocator.database.decisionOutcomeDao().getAll(),
            outcomeAnalyses = serviceLocator.database.outcomeAnalysisDao().getAll()
        )
        return Json.encodeToString(data)
    }

    fun importBackup(jsonStr: String) = viewModelScope.launch {
        try {
            val data = Json.decodeFromString<BackupData>(jsonStr)
            serviceLocator.database.runInTransaction {
                viewModelScope.launch {
                    if (data.people.isNotEmpty()) serviceLocator.database.personDao().insertAll(data.people) // Need insertAll in PersonDao
                    if (data.places.isNotEmpty()) serviceLocator.database.placeDao().insertAll(data.places)
                    if (data.settings.isNotEmpty()) serviceLocator.database.appSettingsDao().insertAll(data.settings)
                    if (data.conversations.isNotEmpty()) serviceLocator.database.conversationDao().insertAll(data.conversations)
                    if (data.messages.isNotEmpty()) serviceLocator.database.conversationMessageDao().insertAll(data.messages)
                    if (data.decisions.isNotEmpty()) serviceLocator.database.decisionDao().insertAll(data.decisions)
                    if (data.decisionAnalyses.isNotEmpty()) serviceLocator.database.decisionAnalysisDao().insertAll(data.decisionAnalyses)
                    if (data.decisionOutcomes.isNotEmpty()) serviceLocator.database.decisionOutcomeDao().insertAll(data.decisionOutcomes)
                    if (data.outcomeAnalyses.isNotEmpty()) serviceLocator.database.outcomeAnalysisDao().insertAll(data.outcomeAnalyses)
                    _state.update { it.copy(saveMessage = "Backup restored successfully.") }
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(saveMessage = "Restore failed: ${e.message}") }
        }
    }

    /** Fetch Gemini model list using the provider and update UI state */
    private fun loadGeminiModels(apiKey: String) = viewModelScope.launch {
        // Update loading state
        _state.update { it.copy(geminiModelsLoading = true) }
        try {
            val provider = serviceLocator.aiService.value
            if (provider is com.pranav.drsti.ai.provider.GeminiAiProvider) {
                val models = provider.fetchAvailableModels()
                _state.update { it.copy(geminiModelList = models, geminiModelsLoading = false) }
            } else {
                // Not Gemini provider; clear list
                _state.update { it.copy(geminiModelList = emptyList(), geminiModelsLoading = false) }
            }
        } catch (e: Exception) {
            // On error, clear list and stop loading
            _state.update { it.copy(geminiModelList = emptyList(), geminiModelsLoading = false) }
        }
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
