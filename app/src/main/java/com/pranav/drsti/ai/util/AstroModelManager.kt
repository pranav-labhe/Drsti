package com.pranav.drsti.ai.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages the extraction and availability of the local LLM model file.
 * SmolLM2-135M-Instruct is bundled in assets and extracted to internal storage.
 */
object AstroModelManager {
    private const val TAG = "AstroModelManager"
    const val MODEL_FILE_NAME = "smollm2_instruct.task"

    private val extractMutex = Mutex()

    suspend fun getOrExtractModel(context: Context): String? = withContext(Dispatchers.IO) {
        val targetFile = File(context.filesDir, MODEL_FILE_NAME)
        
        // Cleanup old .bin file if it exists
        val oldFile = File(context.filesDir, "smollm2_instruct.bin")
        if (oldFile.exists()) {
            Log.d(TAG, "Deleting legacy .bin model file...")
            oldFile.delete()
        }

        // 1. Double-check if a valid file already exists (Approx 166.7MB)
        if (targetFile.exists() && (targetFile.length() > 150 * 1024 * 1024)) {
            Log.d(TAG, "Valid model file already exists (Size: ${targetFile.length()} bytes)")
            return@withContext targetFile.absolutePath
        }

        // 2. Synchronize to ensure only one extraction happens at a time
        extractMutex.withLock {
            // Re-check after acquiring lock
            if (targetFile.exists() && targetFile.length() > 150 * 1024 * 1024) {
                return@withLock targetFile.absolutePath
            }

            Log.d(TAG, "Extracting model atomically (this may take a few seconds)...")
            val tempFile = File(context.filesDir, "${MODEL_FILE_NAME}.tmp")
            
            try {
                context.assets.open(MODEL_FILE_NAME).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Atomic rename to final destination
                if (tempFile.renameTo(targetFile)) {
                    Log.d(TAG, "Model extraction successful: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                    targetFile.absolutePath
                } else {
                    Log.e(TAG, "Failed to rename temp model file to final destination")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical error during model extraction", e)
                tempFile.delete() // Clean up failed attempt
                null
            }
        }
    }

    /**
     * Helper to verify if the model is ready for use.
     */
    fun isModelAvailable(context: Context): Boolean {
        return File(context.filesDir, MODEL_FILE_NAME).exists()
    }
}
