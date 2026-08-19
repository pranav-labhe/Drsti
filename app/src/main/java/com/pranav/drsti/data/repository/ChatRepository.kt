package com.pranav.drsti.data.repository

import com.pranav.drsti.database.dao.ConversationDao
import com.pranav.drsti.database.dao.ConversationMessageDao
import com.pranav.drsti.database.entity.ConversationEntity
import com.pranav.drsti.database.entity.ConversationMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Local-only chat history with chunked/paginated loading (spec §5).
 * Never loads a whole conversation into memory at once.
 */
class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: ConversationMessageDao
) {
    val defaultPageSize = 50

    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()

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
}
