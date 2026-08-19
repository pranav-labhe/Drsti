package com.pranav.drsti.data.repository

import com.pranav.drsti.database.dao.PersonDao
import com.pranav.drsti.database.entity.PersonEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Person profile CRUD (spec §14). Birth data stays local (spec §69) unless explicitly sent for a calculation. */
class PersonRepository(private val dao: PersonDao) {

    fun observeAll(): Flow<List<PersonEntity>> = dao.observeAll()
    fun observeActive(): Flow<PersonEntity?> = dao.observeActive()
    suspend fun getById(id: Long): PersonEntity? = dao.getById(id)

    suspend fun save(entity: PersonEntity): Long {
        val now = Instant.now().toString()
        val toSave = entity.copy(updatedAt = now, createdAt = entity.createdAt.ifBlank { now })
        return dao.upsert(toSave)
    }

    suspend fun setActive(id: Long) {
        dao.clearActiveFlag()
        dao.setActive(id)
    }

    suspend fun delete(entity: PersonEntity) = dao.delete(entity)
}
