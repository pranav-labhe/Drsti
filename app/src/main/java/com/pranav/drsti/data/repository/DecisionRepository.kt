package com.pranav.drsti.data.repository

import com.pranav.drsti.ai.provider.AiAstrologyService
import com.pranav.drsti.database.dao.DecisionAnalysisDao
import com.pranav.drsti.database.dao.DecisionDao
import com.pranav.drsti.database.dao.DecisionOutcomeDao
import com.pranav.drsti.database.dao.OutcomeAnalysisDao
import com.pranav.drsti.database.entity.DecisionAnalysisEntity
import com.pranav.drsti.database.entity.DecisionEntity
import com.pranav.drsti.database.entity.DecisionOutcomeEntity
import com.pranav.drsti.database.entity.OutcomeAnalysisEntity
import com.pranav.drsti.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class DecisionRepository(
    private val decisionDao: DecisionDao,
    private val analysisDao: DecisionAnalysisDao,
    private val outcomeDao: DecisionOutcomeDao,
    private val outcomeAnalysisDao: OutcomeAnalysisDao,
    private val aiServiceProvider: () -> AiAstrologyService
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<DecisionEntity>> = decisionDao.observeAll()

    suspend fun getDecision(id: Long): DecisionEntity? = decisionDao.getById(id)

    fun observeDecision(id: Long): Flow<DecisionEntity?> = decisionDao.observeById(id)

    fun observeAnalyses(decisionId: Long): Flow<List<DecisionAnalysisEntity>> =
        analysisDao.observeForDecision(decisionId)

    suspend fun getOutcome(decisionId: Long): DecisionOutcomeEntity? =
        outcomeDao.getForDecision(decisionId)

    fun observeOutcome(decisionId: Long): Flow<DecisionOutcomeEntity?> =
        outcomeDao.observeForDecision(decisionId)

    suspend fun getOutcomeAnalysis(decisionId: Long): OutcomeAnalysisEntity? =
        outcomeAnalysisDao.getForDecision(decisionId)

    fun observeOutcomeAnalysis(decisionId: Long): Flow<OutcomeAnalysisEntity?> =
        outcomeAnalysisDao.observeForDecision(decisionId)

    suspend fun createDecision(
        personId: Long,
        question: String,
        options: List<DecisionOptionInput>,
        context: String?,
        desiredDate: String?,
        aiContext: AiRequestContext,
        conversationId: Long? = null
    ): Long {
        val now = Instant.now().toString()
        val existing = decisionDao.getByQuestion(personId, question)
        
        val id = if (existing != null) {
            // Update existing decision with potentially new data
            val updated = existing.copy(
                optionsJson = json.encodeToString(options),
                context = context,
                conversationId = conversationId ?: existing.conversationId,
                desiredDecisionDateIso = desiredDate ?: existing.desiredDecisionDateIso
            )
            decisionDao.update(updated)
            existing.id
        } else {
            val decision = DecisionEntity(
                personId = personId,
                conversationId = conversationId,
                question = question,
                optionsJson = json.encodeToString(options),
                context = context,
                desiredDecisionDateIso = desiredDate,
                status = "OPEN",
                createdAt = now
            )
            decisionDao.insert(decision)
        }

        // Run fresh analysis (consistency for Form, Chat Save, and Refresh)
        val analysis = aiServiceProvider().analyzeDecision(aiContext, DecisionRequest(question, options, context))
        
        val existingAnalysis = analysisDao.getLatestForDecision(id)
        val analysisEntity = DecisionAnalysisEntity(
            id = existingAnalysis?.id ?: 0L,
            decisionId = id,
            analysisJson = json.encodeToString(analysis),
            natalSnapshotJson = aiContext.kundali?.let { json.encodeToString(it) },
            dashaSnapshotJson = aiContext.dasha?.let { json.encodeToString(it) },
            panchangSnapshotJson = aiContext.panchang?.let { json.encodeToString(it) },
            planetarySnapshotJson = aiContext.planetaryPositions?.let { json.encodeToString(it) },
            promptVersion = analysis.provenance.promptVersion ?: "decision-v1",
            model = analysis.provenance.model ?: "unknown",
            inputHash = analysis.provenance.inputHash ?: "",
            outputHash = analysis.provenance.outputHash ?: "",
            analysisTimestamp = now
        )

        if (existingAnalysis != null) {
            analysisDao.update(analysisEntity)
        } else {
            analysisDao.insert(analysisEntity)
        }
        return id
    }

    suspend fun recordSelection(decisionId: Long, optionId: String) {
        val decision = decisionDao.getById(decisionId) ?: return
        decisionDao.update(decision.copy(
            selectedOptionId = optionId,
            selectionTimestamp = Instant.now().toString(),
            status = "DECIDED"
        ))
    }

    suspend fun recordOutcome(
        decisionId: Long,
        description: String,
        assessment: String,
        notes: String?
    ) {
        val decision = decisionDao.getById(decisionId) ?: return
        val now = Instant.now().toString()
        
        val existingOutcome = outcomeDao.getForDecision(decisionId)
        val outcome = DecisionOutcomeEntity(
            id = existingOutcome?.id ?: 0L,
            decisionId = decisionId,
            description = description,
            occurredAtIso = existingOutcome?.occurredAtIso ?: now,
            selectedOptionId = decision.selectedOptionId,
            userAssessment = assessment,
            notes = notes,
            recordedAt = now
        )
        
        if (existingOutcome != null) {
            outcomeDao.update(outcome)
        } else {
            outcomeDao.insert(outcome)
        }
        
        // Update decision status
        decisionDao.update(decision.copy(status = "COMPLETED"))

        // Perform retrospective analysis
        val latestAnalysisEntity = analysisDao.getLatestForDecision(decisionId)
        if (latestAnalysisEntity != null) {
            val originalAnalysis = json.decodeFromString(DecisionAnalysis.serializer(), latestAnalysisEntity.analysisJson)
            val outcomeInput = OutcomeInput(
                description = description,
                occurredAtIso = outcome.occurredAtIso,
                selectedOptionId = decision.selectedOptionId,
                userAssessment = assessment,
                notes = notes
            )
            val retrospective = aiServiceProvider().analyzeOutcome(originalAnalysis, outcomeInput)
            
            val existingOA = outcomeAnalysisDao.getForDecision(decisionId)
            val oaEntity = OutcomeAnalysisEntity(
                id = existingOA?.id ?: 0L,
                decisionId = decisionId,
                analysisJson = json.encodeToString(retrospective),
                generatedAt = now
            )
            
            if (existingOA != null) {
                outcomeAnalysisDao.update(oaEntity)
            } else {
                outcomeAnalysisDao.insert(oaEntity)
            }
        }
    }

    suspend fun deleteDecision(id: Long) {
        analysisDao.deleteByDecisionId(id)
        outcomeDao.deleteByDecisionId(id)
        outcomeAnalysisDao.deleteByDecisionId(id)
        decisionDao.deleteById(id)
    }
}
