package com.pranav.drsti.ai.provider

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.pranav.drsti.ai.util.AstroModelManager
import com.pranav.drsti.ai.util.AstroTranslationService
import com.pranav.drsti.database.dao.AIRequestLogDao
import com.pranav.drsti.database.entity.AIRequestLogEntity
import com.pranav.drsti.model.*
import com.pranav.drsti.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MOCK mode provider powered by a local LLM (SmolLM2-135M-Instruct).
 * Preserves deterministic astronomical calculations while adding local reasoning.
 */
class LocalAiProvider(
    private val context: Context,
    private val logDao: AIRequestLogDao? = null
) : AiAstrologyService {

    private val TAG = "LocalAiProvider"
    private val mockFallback = MockAiProvider()
    private val translationService = AstroTranslationService()
    
    private var llmInference: LlmInference? = null
    private val isInitializing = AtomicBoolean(false)

    init {
        initializeLlm()
    }

    private fun initializeLlm() {
        if (!isInitializing.compareAndSet(false, true)) return
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting Local LLM initialization...")
                val modelPath = AstroModelManager.getOrExtractModel(context)
                if (modelPath != null && java.io.File(modelPath).exists()) {
                    try {
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelPath)
                            .setMaxTokens(1024)
                            .build()
                        llmInference = LlmInference.createFromOptions(context, options)
                        Log.i(TAG, "Local LLM initialized successfully from $modelPath")
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaPipe rejected the model file ($modelPath). Deleting...", e)
                        java.io.File(modelPath).delete() 
                        throw e
                    }
                } else {
                    Log.e(TAG, "Model file extraction failed or file not found in assets")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Local LLM", e)
            } finally {
                isInitializing.set(false)
            }
        }
    }

    override suspend fun calculatePlanetaryPositions(dateTime: LocalDateTime, zoneId: ZoneId, latitude: Double, longitude: Double) =
        mockFallback.calculatePlanetaryPositions(dateTime, zoneId, latitude, longitude)

    override suspend fun calculateKundali(birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, latitude: Double, longitude: Double) =
        mockFallback.calculateKundali(birthDate, birthTime, zoneId, latitude, longitude)

    override suspend fun calculateDasha(birthDate: LocalDate, birthTime: LocalTime, zoneId: ZoneId, moonSiderealLongitude: Double) =
        mockFallback.calculateDasha(birthDate, birthTime, zoneId, moonSiderealLongitude)

    override suspend fun generatePanchang(date: LocalDate, zoneId: ZoneId, latitude: Double, longitude: Double) =
        mockFallback.generatePanchang(date, zoneId, latitude, longitude)

    override suspend fun analyzeTransits(context: AiRequestContext): TransitAnalysisResult {
        return mockFallback.analyzeTransits(context) // Transit logic is already quite deterministic in MockAiProvider
    }

    override suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest): DecisionAnalysis {
        return mockFallback.analyzeDecision(context, request)
    }

    override suspend fun analyzeOutcome(originalAnalysis: DecisionAnalysis, outcome: OutcomeInput): OutcomeAnalysis {
        return mockFallback.analyzeOutcome(originalAnalysis, outcome)
    }

    override suspend fun chat(context: AiRequestContext, userMessage: String, conversationId: Long?): ChatReply {
        val llm = llmInference
        
        Log.d(TAG, "Chat request received. LLM ready: ${llm != null}, Initializing: ${isInitializing.get()}")

        // Lazy retry if not initialized yet
        if (llm == null && !isInitializing.get()) {
            Log.d(TAG, "LLM null on chat. Retrying initialization...")
            initializeLlm()
        }

        if (llm == null) {
            Log.w(TAG, "Falling back to MockAiProvider")
            return mockFallback.chat(context, userMessage, conversationId)
        }

        val startTime = System.currentTimeMillis()
        val responseId = UUID.randomUUID().toString()
        var previousId: String? = null
        var output: String? = null
        var success = false
        var errorMsg: String? = null

        try {
            if (conversationId != null && logDao != null) {
                previousId = logDao.getLastInteraction(conversationId)?.interactionId
            }

            // STAGE 1: CONTEXT CONDENSER (Memory)
            var intent = userMessage
            if (context.recentMessages.isNotEmpty()) {
                val stage1Prompt = """
                    <|im_start|>user
                    Summarize history and current message into a 5-word intent.
                    History: ${context.recentMessages.takeLast(2).joinToString("; ")}
                    Message: $userMessage
                    Intent: The user wants to<|im_end|>
                    <|im_start|>assistant
                """.trimIndent()
                val stage1Raw = withContext(Dispatchers.Default) { llm.generateResponse(stage1Prompt) }
                val cleanedIntent = stage1Raw.split("<|im_end|>")[0].trim()
                if (cleanedIntent.isNotBlank() && !cleanedIntent.contains("?")) {
                    intent = "The user wants to $cleanedIntent"
                }
                Log.d(TAG, "Stage 1 (Intent): $intent")
            }

            // STAGE 2: LOGIC DISPATCHER (The "Understanding" Phase)
            val stage2Prompt = """
                <|im_start|>user
                Intent: $intent
                Pick ONE: IDENTITY, CAREER, ROMANCE, TIMING, VIBE, NONE.
                Choice:<|im_end|>
                <|im_start|>assistant
            """.trimIndent()
            
            val stage2Raw = withContext(Dispatchers.Default) { llm.generateResponse(stage2Prompt) }
            val tags = stage2Raw.split("<|im_end|>")[0].trim().uppercase().replace(Regex("[^A-Z]"), "")
            Log.d(TAG, "Stage 2 (Area Tags): $tags")

            // KOTLIN INTEGRATION: Fetch precise interpretation
            val rawInterpretation = AstroInterpretationRenderer.renderFactsFromTags(tags, context)
            Log.d(TAG, "Kotlin Logic Result: $rawInterpretation")

            // STAGE 3: VOICE SYNTHESIZER (The "Synonym" Phase)
            val stage3Prompt = """
                <|im_start|>user
                Facts: $rawInterpretation
                User said: "$userMessage"
                Friendly AI response:<|im_end|>
                <|im_start|>assistant
            """.trimIndent()
            
            val stage3Raw = withContext(Dispatchers.Default) { llm.generateResponse(stage3Prompt) }
            var finalOutput = stage3Raw.split("<|im_end|>")[0].split("<|im_start|>")[0].trim()
            
            // If the model gave us nothing or just a prefix, use the raw interpretation directly
            if (finalOutput.isBlank() || finalOutput.length < 5) {
                finalOutput = rawInterpretation
            }
            
            if (finalOutput.startsWith("assistant:", ignoreCase = true)) finalOutput = finalOutput.substringAfter(":").trim()
            
            output = finalOutput
            Log.d(TAG, "AI Result: $finalOutput")

            // Last resort safety: If still empty, fall back to mock
            if (output.isNullOrBlank()) {
                Log.w(TAG, "AI result is empty after all stages. Falling back to Mock.")
                return mockFallback.chat(context, userMessage, conversationId)
            }

            // Handle language translation
            val userLang = detectLanguage(userMessage)
            if (userLang != "en") {
                output = translationService.translate(output!!, userLang)
            }

            success = true

            return ChatReply(
                text = output!!,
                intent = "LOCAL_PIPELINE_V4",
                provenance = Provenance(
                    calculationVersion = "astrocalc-1.0", promptVersion = "v4-pipeline",
                    model = "SmolLM2-135M-Instruct", generatedAt = Instant.now().toString(),
                    source = "LocalAiProvider", sourceVersion = "mediapipe-llm-1.0",
                    inputHash = HashUtil.sha256(stage3Prompt), outputHash = HashUtil.sha256(output!!)
                )
            )
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
            Log.e(TAG, "Pipeline error - FALLING BACK", e)
            val reply = mockFallback.chat(context, userMessage, conversationId)
            return reply.copy(text = "[Fallback] ${reply.text}")
        } finally {
            logDao?.let { dao ->
                val log = AIRequestLogEntity(
                    conversationId = conversationId, interactionId = responseId,
                    previousInteractionId = previousId, requestType = "v4-pipeline",
                    timestamp = Instant.now().toString(), model = "SmolLM2-135M-Instruct",
                    promptVersion = "v4-pipeline", inputHash = HashUtil.sha256(userMessage),
                    outputHash = output?.let { HashUtil.sha256(it) }, success = success, error = errorMsg,
                    latencyMs = System.currentTimeMillis() - startTime
                )
                GlobalScope.launch(Dispatchers.IO) { dao.insert(log) }
            }
        }
    }

    override fun close() {
        llmInference?.close()
        translationService.close()
    }

    private fun detectLanguage(text: String): String {
        val m = text.lowercase()
        // Simple heuristic for demo/mock purposes, in real apps use a proper language detector
        return when {
            m.contains(Regex("[\\u0900-\\u097F]")) -> "hi" // Devanagari script (Hindi/Marathi)
            else -> "en"
        }
    }
}
