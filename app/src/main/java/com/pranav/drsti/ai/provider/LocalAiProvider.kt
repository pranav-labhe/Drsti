package com.pranav.drsti.ai.provider

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import com.pranav.drsti.ai.resolver.AstroTermResolver
import com.pranav.drsti.ai.resolver.FactFormatter
import com.pranav.drsti.ai.util.AstroModelManager
import com.pranav.drsti.ai.util.AstroTranslationService
import com.pranav.drsti.database.dao.AIRequestLogDao
import com.pranav.drsti.database.entity.AIRequestLogEntity
import com.pranav.drsti.model.*
import com.pranav.drsti.util.HashUtil
import kotlinx.coroutines.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MOCK mode provider powered by a local LLM (SmolLM2-135M-Instruct).
 */
class LocalAiProvider(
    private val context: Context,
    private val logDao: AIRequestLogDao? = null
) : AiAstrologyService {

    private val TAG = "LocalAiProvider"
    private val mockFallback = MockAiProvider()
    private val translationService = AstroTranslationService()
    
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private val isInitializing = AtomicBoolean(false)

    private val GREETINGS = setOf("hi", "hello", "hey", "thanks", "thank you", "ok", "bye", "namaste")

    init {
        initializeLlm()
    }

    private fun initializeLlm() {
        if (!isInitializing.compareAndSet(false, true)) return
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing Local LLM...")
                AstroTermResolver.initialize(context)

                val modelPath = AstroModelManager.getOrExtractModel(context)
                if (modelPath != null && File(modelPath).exists()) {
                    try {
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelPath)
                            .setMaxTokens(1280)
                            .setPreferredBackend(LlmInference.Backend.CPU)
                            .build()
                        llmInference = LlmInference.createFromOptions(context, options)

                        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                            .setTemperature(0.4f) 
                            .setTopK(40)
                            .build()
                        llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)

                        Log.i(TAG, "Local LLM and Session initialized (CPU Backend)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Initialization failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
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

    override suspend fun analyzeTransits(context: AiRequestContext) = mockFallback.analyzeTransits(context)
    override suspend fun analyzeDecision(context: AiRequestContext, request: DecisionRequest) = mockFallback.analyzeDecision(context, request)
    override suspend fun analyzeOutcome(originalAnalysis: DecisionAnalysis, outcome: OutcomeInput) = mockFallback.analyzeOutcome(originalAnalysis, outcome)

    override suspend fun chat(context: AiRequestContext, userMessage: String, conversationId: Long?): ChatReply {
        val llm = llmInference
        val session = llmSession
        if (llm == null || session == null) {
            if (!isInitializing.get()) initializeLlm()
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

            val normalizedMessage = userMessage.lowercase().trim().replace(Regex("[^a-z\\s]"), "")
            if (GREETINGS.contains(normalizedMessage)) {
                output = "Namaste! I'm Drishti. How can I help you see clearly today?"
                success = true
                return createReply(output!!, "SMALL_TALK", startTime, userMessage, "v9-stream")
            }

            val concepts = AstroTermResolver.resolve(userMessage)
            val facts = FactFormatter.format(concepts, context)
            val prompt = buildPrompt(facts, context, userMessage)
            Log.d(TAG, "Prompt: $prompt")

            val deferredOutput = CompletableDeferred<String>()
            val sessionDone = CompletableDeferred<Unit>()
            val outputBuilder = StringBuilder()
            var wordsGenerated = 0

            val callSession = session.cloneSession()
            try {
                callSession.addQueryChunk(prompt)
                withContext(Dispatchers.Default) {
                    callSession.generateResponseAsync(object : ProgressListener<String> {
                        override fun run(partialResult: String?, done: Boolean) {
                            if (done) sessionDone.complete(Unit)
                            if (deferredOutput.isCompleted) return
                            
                            partialResult?.let { 
                                outputBuilder.append(it)
                                wordsGenerated += it.split(Regex("\\s+")).filter { w -> w.isNotBlank() }.size
                                Log.d(TAG, "Partial Output: $it")
                            }
                            
                            val currentText = outputBuilder.toString()
                            // Stop early if im_end reached, or exceeded 7000 words (safer for 135M memory)
                            if (currentText.contains("<|im_end|>") || wordsGenerated > 7000) {
                                val finalized = currentText.substringBefore("<|im_end|>").trim()
                                deferredOutput.complete(finalized)
                                if (!done) {
                                    Log.d(TAG, "Safety valve: cancelling runaway generation")
                                    callSession.cancelGenerateResponseAsync()
                                }
                            } else if (done) {
                                deferredOutput.complete(currentText.trim())
                            }
                        }
                    })
                    withTimeout(25000) { output = deferredOutput.await() }
                }
            } finally {
                // Ensure we wait for the engine to stop before closing the session
                withContext(Dispatchers.Default) { delay(200); sessionDone.join() }
                callSession.close()
            }
            
            Log.d(TAG, "Raw Output: $output")

            // Filter output
            output = output?.substringBefore("<|im_end|>")?.trim() ?: ""

            if (output.isBlank() || output.startsWith("+") || output.contains("jupyter_text")) {
                Log.w(TAG, "Junk detected, falling back.")
                output = facts.joinToString(" ").replace(Regex("<.*?>"), "").trim()
            }

            val userLang = detectLanguage(userMessage)
            if (userLang != "en") output = translationService.translate(output, userLang)

            success = true
            return createReply(output, "LOCAL_V9_STREAM", startTime, prompt, "v9-stream")
        } catch (e: Exception) {
            errorMsg = e.message
            Log.e(TAG, "Chat failed", e)
            return mockFallback.chat(context, userMessage, conversationId)
        } finally {
            logChatResult(conversationId, responseId, previousId, "v9-stream", userMessage, output, success, errorMsg, startTime)
        }
    }

    private fun buildPrompt(facts: List<String>, context: AiRequestContext, userMessage: String): String {
        val factsText = facts.joinToString(" ")
        val userMsg = userMessage.take(100)

        val kbMap = AstroInterpretationRenderer.getKnowledgeBase(context)
        val profile = kbMap.filter { it.key == "MOON_SIGN" || it.key == "LAGNA" || it.key == "CURRENT_DASHA" }
            .values.joinToString(" ") { FactFormatter.synthesize(it) }

        // OFFICIAL ChatML template with clear visual boundaries for factual grounding
        val promptBuilder = StringBuilder()
        
        // 1. System Block: Persona & Constraints
        promptBuilder.append("<|im_start|>system\n")
        promptBuilder.append("You are Drishti, a warm Vedic assistant. Use ONLY the provided Facts to help the user in simple English. Never use technical jargon or symbols.\n")
        promptBuilder.append("<|im_end|>\n")
        
        // 2. Shot Block: Teaching the model to use the "Facts"
        promptBuilder.append("<|im_start|>user\n")
        promptBuilder.append("Facts: Your Emotions (Moon) are in their home sign.\n")
        promptBuilder.append("Question: How do I feel?\n")
        promptBuilder.append("<|im_end|>\n")
        promptBuilder.append("<|im_start|>assistant\n")
        promptBuilder.append("Namaste! Since your Emotions are in their home sign today, you are likely feeling very peaceful and centered.\n")
        promptBuilder.append("<|im_end|>\n")

        // 3. Current Block: Dynamic Data
        promptBuilder.append("<|im_start|>user\n")
        promptBuilder.append("Facts: $profile $factsText\n")
        promptBuilder.append("Question: $userMsg\n")
        promptBuilder.append("<|im_end|>\n")
        
        // 4. Open Assistant block for generation
        promptBuilder.append("<|im_start|>assistant\n")
        
        return promptBuilder.toString()
    }

    private fun createReply(text: String, intent: String, startTime: Long, prompt: String, version: String): ChatReply {
        return ChatReply(text, intent, Provenance("1.0", "1.0", version, "SmolLM-135M", Instant.now().toString(), "LocalAiProvider", "1.0", HashUtil.sha256(prompt), HashUtil.sha256(text)))
    }

    private fun logChatResult(conversationId: Long?, responseId: String, previousId: String?, version: String, input: String, output: String?, success: Boolean, error: String?, startTime: Long) {
        logDao?.let { dao ->
            val log = AIRequestLogEntity(
                conversationId = conversationId,
                interactionId = responseId,
                previousInteractionId = previousId,
                requestType = version,
                timestamp = Instant.now().toString(),
                model = "SmolLM-135M",
                promptVersion = version,
                inputHash = HashUtil.sha256(input),
                outputHash = output?.let { HashUtil.sha256(it) },
                success = success,
                error = error,
                latencyMs = System.currentTimeMillis() - startTime
            )
            GlobalScope.launch(Dispatchers.IO) { dao.insert(log) }
        }
    }

    private fun detectLanguage(text: String) = if (text.contains(Regex("[\\u0900-\\u097F]"))) "hi" else "en"

    override fun close() {
        llmInference?.close()
        translationService.close()
    }
}
