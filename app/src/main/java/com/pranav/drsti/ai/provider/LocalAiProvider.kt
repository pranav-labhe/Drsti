package com.pranav.drsti.ai.provider

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.pranav.drsti.ai.resolver.AstroTermResolver
import com.pranav.drsti.ai.resolver.FactFormatter
import com.pranav.drsti.ai.util.AstroModelManager
import com.pranav.drsti.ai.util.AstroTranslationService
import com.pranav.drsti.database.dao.AIRequestLogDao
import com.pranav.drsti.database.entity.AIRequestLogEntity
import com.pranav.drsti.model.*
import com.pranav.drsti.util.HashUtil
import kotlinx.coroutines.*
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

    // Task 5: Budget and Prompt constants
    private val MAX_PROMPT_TOKENS = 200
    private val GREETINGS = setOf("hi", "hello", "hey", "thanks", "thank you", "ok", "bye", "namaste")
    private val STOPWORDS = setOf("this", "that", "with", "from", "your", "have", "been", "will", "they", "asked")

    init {
        initializeLlm()
    }

    private fun initializeLlm() {
        if (!isInitializing.compareAndSet(false, true)) return
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting Local LLM initialization...")
                AstroTermResolver.initialize(context) // Task 2

                val modelPath = AstroModelManager.getOrExtractModel(context)
                if (modelPath != null && java.io.File(modelPath).exists()) {
                    try {
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelPath)
                            .setMaxTokens(1065) // Task 7
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

            // Task 8: Small-talk short-circuit
            val normalizedMessage = userMessage.lowercase().trim().replace(Regex("[^a-z\\s]"), "")
            if (GREETINGS.contains(normalizedMessage)) {
                val cannedReply = "Namaste! I'm Drishti, your companion. How can I help you see clearly today?"
                output = cannedReply
                success = true
                return createReply(output!!, "SMALL_TALK", startTime, userMessage, "v5-small-talk")
            }

            // Task 4.3: Resolve concepts
            val concepts = AstroTermResolver.resolve(userMessage)
            
            // Task 4.4: Format facts
            val facts = FactFormatter.format(concepts, context)
            
            // Task 5: Build ONE prompt
            val prompt = buildPrompt(facts, context, userMessage)
            Log.d(TAG, "Prompt: $prompt")

            // Task 4.6 & 7: ONE llm call
            val rawOutput = withContext(Dispatchers.Default) {
                llm.generateResponse(prompt)
            }
            Log.d(TAG, "Raw Output: $rawOutput")

            // Task 6: Validate output
            output = validateAndCleanOutput(rawOutput, facts)
            Log.d(TAG, "Validated Output: $output")

            // Handle language translation
            val userLang = detectLanguage(userMessage)
            if (userLang != "en") {
                output = translationService.translate(output!!, userLang)
            }

            success = true

            return createReply(output!!, "LOCAL_V5_SINGLE_CALL", startTime, prompt, "v5-single-call")
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
            Log.e(TAG, "Pipeline error - FALLING BACK", e)
            val reply = mockFallback.chat(context, userMessage, conversationId)
            return reply.copy(text = "[Fallback] ${reply.text}")
        } finally {
            logChatResult(conversationId, responseId, previousId, "v5-single-call", userMessage, output, success, errorMsg, startTime)
        }
    }

    private fun buildPrompt(facts: List<String>, context: AiRequestContext, userMessage: String): String {
        val factsText = facts.joinToString(" ")
        
        // History: last 2 turns, each clamped to 15 tokens for even more stability
        val historyPart = if (context.recentMessages.isNotEmpty()) {
            val last2 = context.recentMessages.takeLast(2).map { 
                val words = it.split(Regex("\\s+")).filter { w -> w.isNotBlank() }
                if (words.size > 15) words.takeLast(15).joinToString(" ") else it
            }
            "Past: " + last2.joinToString("; ")
        } else ""

        val userWords = userMessage.split(Regex("\\s+")).filter { it.isNotBlank() }
        val userMessageClamped = if (userWords.size > 20) {
            userWords.take(20).joinToString(" ")
        } else userMessage

        val promptBuilder = StringBuilder()
        // Stronger, more restrictive system instruction
        promptBuilder.append("<|im_start|>system\nRewrite the following facts as one short, friendly sentence in English. Do not add advice or opinions.<|im_end|>\n")
        promptBuilder.append("<|im_start|>user\n")
        promptBuilder.append("FACTS: ").append(factsText)
        if (historyPart.isNotEmpty()) {
            promptBuilder.append("\n").append(historyPart)
        }
        promptBuilder.append("\nQUESTION: \"").append(userMessageClamped).append("\"<|im_end|>\n")
        promptBuilder.append("<|im_start|>assistant\n")
        
        var prompt = promptBuilder.toString()
        
        // Task 5: Budget enforcement ≤ 200 tokens
        if (estimateTokens(prompt) > MAX_PROMPT_TOKENS) {
            // Drop history first
            promptBuilder.setLength(0)
            promptBuilder.append("<|im_start|>system\nReword these facts warmly in one short sentence. Add no new information.<|im_end|>\n")
            promptBuilder.append("<|im_start|>user\n")
            promptBuilder.append(factsText)
            promptBuilder.append("\n\nThey asked: \"").append(userMessageClamped).append("\"<|im_end|>\n")
            promptBuilder.append("<|im_start|>assistant\n")
            prompt = promptBuilder.toString()
            
            if (estimateTokens(prompt) > MAX_PROMPT_TOKENS) {
                // Still over? Truncate user message further
                promptBuilder.setLength(0)
                promptBuilder.append("<|im_start|>system\nReword these facts warmly in one short sentence. Add no new information.<|im_end|>\n")
                promptBuilder.append("<|im_start|>user\n")
                promptBuilder.append(factsText)
                promptBuilder.append("\n\nThey asked: \"").append(userMessage.take(30)).append("\"<|im_end|>\n")
                promptBuilder.append("<|im_start|>assistant\n")
                prompt = promptBuilder.toString()
            }
        }
        
        return prompt
    }

    private fun validateAndCleanOutput(raw: String, facts: List<String>): String {
        var output = raw.split("<|im_end|>")[0].split("<|im_start|>")[0].trim()
        if (output.startsWith("assistant:", ignoreCase = true)) {
            output = output.substringAfter(":").trim()
        }

        val factsText = facts.joinToString(" ")

        // Task 6.2: Reject conditions
        if (output.isBlank() || output.length < 5 || output.length > 300) {
            return factsText
        }

        // Catch list-style hallucinations and generic self-help patterns
        val forbiddenPatterns = listOf(
            "im_start", "im_end", "Reword these facts", "1.", "2.", "3.", 
            "mental health", "freelance", "planner", "visualization", "self-compassion"
        )
        if (forbiddenPatterns.any { output.lowercase().contains(it) }) {
            Log.w(TAG, "Rejected hallucinated content: $output")
            return factsText
        }

        // Shares no significant word with supplied facts
        val factWords = factsText.lowercase()
            .split(Regex("[^a-z]"))
            .filter { it.length > 3 && !STOPWORDS.contains(it) }
            .toSet()
        
        val outputWords = output.lowercase()
            .split(Regex("[^a-z]"))
            .filter { it.length > 3 && !STOPWORDS.contains(it) }
        
        if (factWords.isNotEmpty() && outputWords.none { factWords.contains(it) }) {
            return factsText
        }

        return output
    }

    private fun estimateTokens(text: String): Double {
        return text.split(Regex("\\s+")).filter { it.isNotBlank() }.size * 1.3
    }

    private fun createReply(text: String, intent: String, startTime: Long, prompt: String, promptVersion: String): ChatReply {
        return ChatReply(
            text = text,
            intent = intent,
            provenance = Provenance(
                calculationVersion = "astrocalc-1.0",
                promptVersion = promptVersion,
                model = "SmolLM2-135M-Instruct",
                generatedAt = Instant.now().toString(),
                source = "LocalAiProvider",
                sourceVersion = "mediapipe-llm-1.0",
                inputHash = HashUtil.sha256(prompt),
                outputHash = HashUtil.sha256(text)
            )
        )
    }

    private fun logChatResult(conversationId: Long?, responseId: String, previousId: String?, promptVersion: String, input: String, output: String?, success: Boolean, error: String?, startTime: Long) {
        logDao?.let { dao ->
            val log = AIRequestLogEntity(
                conversationId = conversationId, interactionId = responseId,
                previousInteractionId = previousId, requestType = promptVersion,
                timestamp = Instant.now().toString(), model = "SmolLM2-135M-Instruct",
                promptVersion = promptVersion, inputHash = HashUtil.sha256(input),
                outputHash = output?.let { HashUtil.sha256(it) }, success = success, error = error,
                latencyMs = System.currentTimeMillis() - startTime
            )
            GlobalScope.launch(Dispatchers.IO) { dao.insert(log) }
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
