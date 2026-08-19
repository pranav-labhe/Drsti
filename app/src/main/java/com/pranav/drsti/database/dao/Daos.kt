package com.pranav.drsti.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pranav.drsti.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM person ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<PersonEntity?>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun getById(id: Long): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PersonEntity): Long

    @Query("UPDATE person SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE person SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Delete
    suspend fun delete(entity: PersonEntity)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM place ORDER BY name ASC")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("""SELECT * FROM place WHERE name LIKE '%' || :query || '%'
        OR city LIKE '%' || :query || '%' OR aliases LIKE '%' || :query || '%'
        ORDER BY name ASC LIMIT 30""")
    suspend fun search(query: String): List<PlaceEntity>

    @Query("SELECT * FROM place WHERE id = :id")
    suspend fun getById(id: Long): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<PlaceEntity>)

    @Delete
    suspend fun delete(entity: PlaceEntity)

    @Query("SELECT COUNT(*) FROM place")
    suspend fun count(): Int
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationEntity): Long

    @Update
    suspend fun update(entity: ConversationEntity)
}

@Dao
interface ConversationMessageDao {
    /** Paginated / chunked loading — never load the whole history at once (spec §5). */
    @Query("""SELECT * FROM conversation_message WHERE conversationId = :conversationId
        ORDER BY id DESC LIMIT :pageSize OFFSET :offset""")
    suspend fun getPage(conversationId: Long, pageSize: Int, offset: Int): List<ConversationMessageEntity>

    @Query("""SELECT * FROM conversation_message WHERE conversationId = :conversationId
        ORDER BY id DESC LIMIT :limit""")
    fun observeRecent(conversationId: Long, limit: Int): Flow<List<ConversationMessageEntity>>

    @Insert
    suspend fun insert(entity: ConversationMessageEntity): Long

    @Query("SELECT COUNT(*) FROM conversation_message WHERE conversationId = :conversationId")
    suspend fun countForConversation(conversationId: Long): Int
}

@Dao
interface KundaliDao {
    @Query("SELECT * FROM kundali WHERE personId = :personId ORDER BY generatedAt DESC LIMIT 1")
    fun observeLatest(personId: Long): Flow<KundaliEntity?>

    @Query("SELECT * FROM kundali WHERE personId = :personId ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatest(personId: Long): KundaliEntity?

    @Insert
    suspend fun insert(entity: KundaliEntity): Long

    @Query("DELETE FROM kundali")
    suspend fun clearAll()
}

@Dao
interface DashaDao {
    @Query("SELECT * FROM dasha WHERE personId = :personId ORDER BY generatedAt DESC LIMIT 1")
    fun observeLatest(personId: Long): Flow<DashaEntity?>

    @Query("SELECT * FROM dasha WHERE personId = :personId ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatest(personId: Long): DashaEntity?

    @Insert
    suspend fun insert(entity: DashaEntity): Long

    @Query("DELETE FROM dasha")
    suspend fun clearAll()
}

@Dao
interface PanchangDao {
    @Query("SELECT * FROM panchang WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByCacheKey(cacheKey: String): PanchangEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PanchangEntity): Long

    @Query("SELECT * FROM panchang ORDER BY generatedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<PanchangEntity>>

    @Query("DELETE FROM panchang")
    suspend fun clearAll()
}

@Dao
interface PlanetaryPositionDao {
    @Query("SELECT * FROM planetary_position ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun getLatest(): PlanetaryPositionEntity?

    @Insert
    suspend fun insert(entity: PlanetaryPositionEntity): Long

    @Query("DELETE FROM planetary_position")
    suspend fun clearAll()
}

@Dao
interface TransitAnalysisDao {
    @Query("SELECT * FROM transit_analysis WHERE personId = :personId ORDER BY generatedAt DESC LIMIT 1")
    fun observeLatest(personId: Long): Flow<TransitAnalysisEntity?>

    @Insert
    suspend fun insert(entity: TransitAnalysisEntity): Long

    @Query("DELETE FROM transit_analysis")
    suspend fun clearAll()
}

@Dao
interface DecisionDao {
    @Query("SELECT * FROM decision ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DecisionEntity>>

    @Query("SELECT * FROM decision WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<DecisionEntity>>

    @Query("SELECT * FROM decision WHERE id = :id")
    suspend fun getById(id: Long): DecisionEntity?

    @Insert
    suspend fun insert(entity: DecisionEntity): Long

    @Update
    suspend fun update(entity: DecisionEntity)
}

@Dao
interface DecisionAnalysisDao {
    /** Immutable once written — no update() method is exposed on purpose (spec §33). */
    @Query("SELECT * FROM decision_analysis WHERE decisionId = :decisionId ORDER BY analysisTimestamp DESC")
    fun observeForDecision(decisionId: Long): Flow<List<DecisionAnalysisEntity>>

    @Query("SELECT * FROM decision_analysis WHERE decisionId = :decisionId ORDER BY analysisTimestamp DESC LIMIT 1")
    suspend fun getLatestForDecision(decisionId: Long): DecisionAnalysisEntity?

    @Insert
    suspend fun insert(entity: DecisionAnalysisEntity): Long
}

@Dao
interface DecisionOutcomeDao {
    @Query("SELECT * FROM decision_outcome WHERE decisionId = :decisionId LIMIT 1")
    suspend fun getForDecision(decisionId: Long): DecisionOutcomeEntity?

    @Insert
    suspend fun insert(entity: DecisionOutcomeEntity): Long
}

@Dao
interface OutcomeAnalysisDao {
    @Query("SELECT * FROM outcome_analysis WHERE decisionId = :decisionId ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getForDecision(decisionId: Long): OutcomeAnalysisEntity?

    @Insert
    suspend fun insert(entity: OutcomeAnalysisEntity): Long
}

@Dao
interface AIRequestLogDao {
    @Insert
    suspend fun insert(entity: AIRequestLogEntity): Long

    @Query("SELECT * FROM ai_request_log ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AIRequestLogEntity>>

    @Query("DELETE FROM ai_request_log")
    suspend fun clearAll()
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): AppSettingsEntity?

    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppSettingsEntity)
}
