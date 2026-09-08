package com.pranav.drsti.di

import android.content.Context
import com.pranav.drsti.ai.provider.AiAstrologyService
import com.pranav.drsti.ai.provider.AiMode
import com.pranav.drsti.ai.provider.AiProviderFactory
import com.pranav.drsti.data.repository.ChatRepository
import com.pranav.drsti.data.repository.DecisionRepository
import com.pranav.drsti.data.repository.PersonRepository
import com.pranav.drsti.data.repository.PlaceRepository
import com.pranav.drsti.data.repository.SettingsRepository
import com.pranav.drsti.database.DrishtiDatabase
import com.pranav.drsti.util.SecureCredentialStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Lightweight, dependency-free service locator (no Hilt/Dagger) so the
 * project stays simple to open and build. DrishtiApplication creates one
 * instance and hands it down through composition locals / constructor
 * params — see ui/app/DrishtiApp.kt.
 */
class ServiceLocator(context: Context) {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: DrishtiDatabase = DrishtiDatabase.getInstance(context)
    val secureStorage: SecureCredentialStorage = SecureCredentialStorage(context)

    val settingsRepository: SettingsRepository = SettingsRepository(database.appSettingsDao())
    val personRepository: PersonRepository = PersonRepository(database.personDao())
    val placeRepository: PlaceRepository = PlaceRepository(context, database.placeDao()).also {
        applicationScope.launch { it.seedDefaultPlacesIfEmpty() }
    }
    val chatRepository: ChatRepository = ChatRepository(database.conversationDao(), database.conversationMessageDao())
    val decisionRepository: DecisionRepository = DecisionRepository(
        database.decisionDao(),
        database.decisionAnalysisDao(),
        database.decisionOutcomeDao(),
        database.outcomeAnalysisDao(),
        { aiService.value }
    )

    private val _aiService = MutableStateFlow<AiAstrologyService>(
        AiProviderFactory.create(AiMode.MOCK, apiKey = null, model = "gpt-4o-mini", logDao = database.aiRequestLogDao())
    )
    val aiService: StateFlow<AiAstrologyService> = _aiService

    init {
        applicationScope.launch { refreshAiProviderFromSettings() }
    }

    suspend fun refreshAiProviderFromSettings() {
        val modeStr = settingsRepository.getAiMode()
        val mode = when (modeStr) {
            "LIVE" -> AiMode.LIVE
            "GEMINI" -> AiMode.GEMINI
            else -> AiMode.MOCK
        }
        val model = settingsRepository.getAiModel()
        val apiKey = secureStorage.getApiKey()
        val geminiApiKey = secureStorage.getGeminiApiKey()
        val logDao = database.aiRequestLogDao()
        _aiService.value = AiProviderFactory.create(mode, apiKey, model, geminiApiKey, logDao)
    }

    companion object {
        @Volatile private var instance: ServiceLocator? = null

        fun getInstance(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context.applicationContext).also { instance = it }
            }
    }
}
