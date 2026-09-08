package com.pranav.drsti.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.pranav.drsti.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the local SmolLM2-135M model lifecycle and inference using MediaPipe.
 * Provides intent classification and interpretation synthesis.
 */
class SemanticIntelligenceManager(private val context: Context) {

    private var llmInference: LlmInference? = null
    private val modelPath = "/data/local/tmp/smollm2_135m.bin" // Placeholder or assets path

    private val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTemperature(0.4f)
        .setTopK(40)
        .build()

    init {
        setupModel()
    }

    private fun setupModel() {
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            // Log error
        }
    }

    /**
     * Classifies the user intent using the local SLM.
     */
    suspend fun classifyIntent(message: String): ResolvedIntent = withContext(Dispatchers.Default) {
        val inference = llmInference ?: return@withContext ResolvedIntent.GENERAL_CHAT
        
        val prompt = """
            <|im_start|>system
            You are a highly accurate intent classifier for Drsti, a Vedic Astrology app.
            Classify the user message into one of these intents:
            - DECISION_START: Starting a new life decision (e.g., "Should I change jobs?")
            - ASTROLOGY_QUERY: General astrology question (e.g., "What is my dasha?")
            - IOT_COMMAND: Unrelated commands (e.g., "switch off light", "play music")
            - GENERAL_CHAT: Small talk or greetings.
            
            Respond only with the intent name.
            <|im_end|>
            <|im_start|>user
            Message: $message
            Intent:<|im_end|>
            <|im_start|>assistant
        """.trimIndent()

        try {
            val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            session.addQueryChunk(prompt)
            val response = session.generateResponse().trim()
            
            when {
                "DECISION_START" in response -> ResolvedIntent.DECISION_START
                "ASTROLOGY_QUERY" in response -> ResolvedIntent.ASTROLOGY_QUERY
                "IOT_COMMAND" in response -> ResolvedIntent.UNKNOWN
                else -> ResolvedIntent.GENERAL_CHAT
            }
        } catch (e: Exception) {
            ResolvedIntent.GENERAL_CHAT
        }
    }

    /**
     * Synthesizes structured findings into conversational human prose.
     */
    suspend fun synthesizeInterpretation(
        findings: List<JyotishFinding>,
        language: String
    ): String = withContext(Dispatchers.Default) {
        val inference = llmInference ?: return@withContext "I'm reflecting on your chart."
        
        val findingsText = findings.joinToString("\n") { "- ${it.key}" }
        val prompt = """
            <|im_start|>system
            You are Dṛṣṭi, a wise Vedic companion. Translate the following Jyotish findings into empathetic, human-friendly prose.
            RULES:
            - No technical jargon (no house numbers, no technical planet names).
            - Use warm, conversational language.
            - Language: $language
            <|im_end|>
            <|im_start|>user
            Findings:
            $findingsText
            Interpretation:<|im_end|>
            <|im_start|>assistant
        """.trimIndent()

        try {
            val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            session.addQueryChunk(prompt)
            session.generateResponse().trim()
        } catch (e: Exception) {
            "I'm reflecting on your chart. This period suggests a focus on growth and stability."
        }
    }
}
