package com.pranav.drsti.data.repository

import com.pranav.drsti.database.dao.AppSettingsDao
import com.pranav.drsti.database.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Non-sensitive settings (astrology system, AI mode/model, cache config) — spec §51. */
class SettingsRepository(private val dao: AppSettingsDao) {

    fun observeAll(): Flow<Map<String, String>> = dao.observeAll().map { list -> list.associate { it.key to it.value } }

    suspend fun get(key: String, default: String): String = dao.get(key)?.value ?: default
    suspend fun set(key: String, value: String) = dao.upsert(AppSettingsEntity(key, value))

    suspend fun getAiMode(): String = get(KEY_AI_MODE, "MOCK")
    suspend fun setAiMode(mode: String) = set(KEY_AI_MODE, mode)

    suspend fun getAiModel(): String = get(KEY_AI_MODEL, "gpt-4o-mini")
    suspend fun setAiModel(model: String) = set(KEY_AI_MODEL, model)

    suspend fun getPlanetaryFreshnessMinutes(): Int = get(KEY_PLANETARY_FRESHNESS_MIN, "15").toIntOrNull() ?: 15
    suspend fun setPlanetaryFreshnessMinutes(minutes: Int) = set(KEY_PLANETARY_FRESHNESS_MIN, minutes.toString())

    suspend fun getDiagnosticsEnabled(): Boolean = get(KEY_DIAGNOSTICS_ENABLED, "true").toBoolean()
    suspend fun setDiagnosticsEnabled(enabled: Boolean) = set(KEY_DIAGNOSTICS_ENABLED, enabled.toString())

    suspend fun getActivePersonId(): Long? = get(KEY_ACTIVE_PERSON_ID, "").toLongOrNull()
    suspend fun setActivePersonId(id: Long) = set(KEY_ACTIVE_PERSON_ID, id.toString())

    companion object {
        const val KEY_AI_MODE = "ai_mode"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_PLANETARY_FRESHNESS_MIN = "planetary_freshness_min"
        const val KEY_DIAGNOSTICS_ENABLED = "diagnostics_enabled"
        const val KEY_ACTIVE_PERSON_ID = "active_person_id"
    }
}
