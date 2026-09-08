package com.pranav.drsti.data.repository

import com.pranav.drsti.database.dao.ConversationDao
import com.pranav.drsti.database.dao.ConversationMessageDao
import com.pranav.drsti.database.dao.ConversationStateDao
import com.pranav.drsti.database.entity.ConversationEntity
import com.pranav.drsti.database.entity.ConversationMessageEntity
import com.pranav.drsti.database.entity.ConversationStateEntity
import com.pranav.drsti.model.ConversationState
import com.pranav.drsti.model.DetailLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Local-only chat history with chunked/paginated loading (spec §5).
 * Also tracks conversation state for offline context continuity.
 */
class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: ConversationMessageDao,
    private val stateDao: ConversationStateDao
) {
    val defaultPageSize = 50

    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()

    fun observeState(conversationId: Long): Flow<ConversationState?> =
        stateDao.observeByConversationId(conversationId).map { it?.toModel() }

    suspend fun getState(conversationId: Long): ConversationState? =
        stateDao.getByConversationId(conversationId)?.toModel()

    suspend fun updateState(state: ConversationState) {
        stateDao.upsert(state.toEntity())
    }

    private fun ConversationStateEntity.toModel() = ConversationState(
        conversationId = conversationId,
        activeTopic = activeTopic,
        activeDecisionId = activeDecisionId,
        languagePreference = languagePreference,
        detailLevel = DetailLevel.valueOf(detailLevel),
        lastIntent = lastIntent,
        lastActiveEntityId = lastActiveEntityId,
        lastFactsSnapshot = lastFactsSnapshot
    )

    private fun ConversationState.toEntity() = ConversationStateEntity(
        conversationId = conversationId,
        activeTopic = activeTopic,
        activeDecisionId = activeDecisionId,
        languagePreference = languagePreference,
        detailLevel = detailLevel.name,
        lastIntent = lastIntent,
        lastActiveEntityId = lastActiveEntityId,
        lastFactsSnapshot = lastFactsSnapshot,
        updatedAt = Instant.now().toString()
    )

    suspend fun getOrCreateDefaultConversation(): ConversationEntity {
        // Simple approach: keep a single ongoing "Home Chat" conversation for V1 (matches spec's
        // chat-first single-user home screen); multiple conversations remain supported by the schema.
        val all = conversationDao.observeAll().first()
        if (all.isNotEmpty()) return all.first()
        val now = Instant.now().toString()
        val id = conversationDao.upsert(ConversationEntity(title = "Home Chat", createdAt = now, updatedAt = now))
        return conversationDao.getById(id)!!
    }

    suspend fun loadPage(conversationId: Long, page: Int): List<ConversationMessageEntity> =
        messageDao.getPage(conversationId, defaultPageSize, page * defaultPageSize)

    fun observeRecent(conversationId: Long, limit: Int = defaultPageSize): Flow<List<ConversationMessageEntity>> =
        messageDao.observeRecent(conversationId, limit)

    suspend fun appendMessage(conversationId: Long, role: String, content: String, intent: String? = null): Long {
        conversationDao.getById(conversationId)?.let {
            conversationDao.update(it.copy(updatedAt = Instant.now().toString()))
        }
        return messageDao.insert(
            ConversationMessageEntity(
                conversationId = conversationId, role = role, content = content,
                intent = intent, timestamp = Instant.now().toString()
            )
        )
    }

    /** Insert a new conversation and return its generated ID. */
    suspend fun createConversation(entity: ConversationEntity): Long {
        // Upsert returns the row ID; if the entity already exists, it updates and returns the same ID.
        return conversationDao.upsert(entity)
    }

    suspend fun deleteConversation(id: Long) {
        stateDao.deleteByConversationId(id)
        messageDao.deleteAllForConversation(id)
        conversationDao.deleteById(id)
    }

    suspend fun updateConversationTitle(id: Long, title: String) {
        conversationDao.getById(id)?.let {
            conversationDao.update(it.copy(title = title, updatedAt = Instant.now().toString()))
        }
    }
}
