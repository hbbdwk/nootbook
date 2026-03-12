package com.example.notebook.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaPipe LLM Inference local summary generator.
 *
 * Uses Gemma 3 1B IT int4 model via MediaPipe to generate
 * abstractive summaries entirely on-device.
 *
 * Model file must be pre-downloaded to files/models/ via
 * scripts/download_model.sh.
 *
 * Target: Pixel 8 Pro (12 GB RAM)
 * Model : ~550 MB int4, ~1-2 GB runtime RAM
 */
class MediaPipeLlmSummaryGenerator(
    private val context: Context
) : SummaryGenerator {

    companion object {
        private const val TAG = "MediaPipeLlm"
        private const val MODEL_NAME = "gemma3-1b-it-int4.task"
        private const val MAX_TOKENS = 512
        private const val MAX_SUMMARY_LENGTH = 200
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.7f
    }

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    var lastAvailabilityError: SummaryErrorType? = null
        private set

    private fun getModelPath(): String =
        File(context.filesDir, "models/$MODEL_NAME").absolutePath

    private fun modelExists(): Boolean {
        val f = File(getModelPath())
        val ok = f.exists() && f.length() > 0
        Log.d(TAG, "modelExists=$ok  path=${f.absolutePath}  size=${f.length()}")
        return ok
    }

    /* ---- SummaryGenerator contract ---- */

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && llmInference != null) return@withContext true
        if (!modelExists()) {
            Log.w(TAG, "Model not found. Run scripts/download_model.sh")
            lastAvailabilityError = SummaryErrorType.MODEL_MISSING
            return@withContext false
        }
        try {
            val t0 = System.currentTimeMillis()
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(getModelPath())
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(TOP_K)
                .build()
            llmInference = LlmInference.createFromOptions(context, opts)
            isInitialized = true
            lastAvailabilityError = null
            Log.d(TAG, "LLM ready in ${System.currentTimeMillis() - t0} ms")
            true
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
            isInitialized = false
            llmInference = null
            lastAvailabilityError = SummaryErrorType.INIT_FAILED
            false
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (!modelExists()) {
            lastAvailabilityError = SummaryErrorType.MODEL_MISSING
            return@withContext false
        }
        if (!isInitialized) return@withContext initialize()
        lastAvailabilityError = null
        llmInference != null
    }

    override suspend fun generateSummary(
        title: String,
        content: String
    ): Result<String> = withContext(Dispatchers.IO) {
        // lazy init
        if (!isInitialized || llmInference == null) {
            if (!modelExists()) {
                lastAvailabilityError = SummaryErrorType.MODEL_MISSING
                return@withContext Result.failure(
                    SummaryGenerationException(
                        SummaryErrorType.MODEL_MISSING,
                        "Model file missing at ${getModelPath()}"
                    )
                )
            }
            if (!initialize()) {
                val type = lastAvailabilityError ?: SummaryErrorType.INIT_FAILED
                return@withContext Result.failure(
                    SummaryGenerationException(type, "LLM not initialised")
                )
            }
        }
        val engine = llmInference ?: return@withContext Result.failure(
            IllegalStateException("LLM instance null")
        )

        var session: LlmInferenceSession? = null
        try {
            val prompt = buildPrompt(title, content)
            Log.d(TAG, "prompt len=${prompt.length}")
            val t0 = System.currentTimeMillis()

            // Create a session with temperature / topK for this generation
            val sessionOpts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .setRandomSeed(42)
                .build()
            session = LlmInferenceSession.createFromOptions(engine, sessionOpts)
            session.addQueryChunk(prompt)
            val raw: String = session.generateResponse()

            Log.d(TAG, "inference ${System.currentTimeMillis() - t0} ms  raw=${raw.length} $raw")

            if (raw.isBlank()) {
                return@withContext Result.failure(
                    SummaryGenerationException(
                        SummaryErrorType.INFERENCE_FAILED,
                        "empty response"
                    )
                )
            }
            val summary = cleanResponse(raw)
            if (summary.isBlank()) {
                return@withContext Result.failure(
                    SummaryGenerationException(
                        SummaryErrorType.INFERENCE_FAILED,
                        "cleaned summary blank"
                    )
                )
            }
            Log.d(TAG, "summary(${summary.length}): ${summary.take(80)}")
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "generateSummary failed", e)
            val typed = if (e is SummaryGenerationException) {
                e
            } else {
                SummaryGenerationException(
                    SummaryErrorType.INFERENCE_FAILED,
                    e.message ?: "inference failed",
                    e
                )
            }
            Result.failure(typed)
        } finally {
            try { session?.close() } catch (_: Exception) {}
        }
    }

    override fun release() {
        try { llmInference?.close() } catch (_: Exception) {}
        llmInference = null
        isInitialized = false
        Log.d(TAG, "released")
    }

    /* ---- prompt / post-processing ---- */

    private fun buildPrompt(title: String, content: String): String {
        val body = if (content.length > 1500) content.take(1500) + "..." else content
        val chinese = content.any { it.code in 0x4E00..0x9FFF }
        val sb = StringBuilder()
        if (chinese) {
            sb.appendLine("\u8bf7\u4e3a\u4ee5\u4e0b\u7b14\u8bb0\u751f\u6210\u4e00\u4e2a\u7b80\u6d01\u7684\u4e2d\u6587\u6458\u8981\u3002")
            sb.appendLine("\u8981\u6c42\uff1a1.\u4e0d\u8d85\u8fc7${MAX_SUMMARY_LENGTH}\u5b57 2.\u63d0\u53d6\u6838\u5fc3\u8981\u70b9 3.\u8bed\u8a00\u7b80\u6d01 4.\u53ea\u8f93\u51fa\u6458\u8981")
            sb.appendLine()
            if (title.isNotBlank()) sb.appendLine("\u6807\u9898\uff1a$title")
            sb.appendLine("\u5185\u5bb9\uff1a$body")
            sb.appendLine()
            sb.append("\u6458\u8981\uff1a")
        } else {
            sb.appendLine("Generate a concise summary for the note below.")
            sb.appendLine("Rules: max $MAX_SUMMARY_LENGTH chars, key points only, output summary only.")
            sb.appendLine()
            if (title.isNotBlank()) sb.appendLine("Title: $title")
            sb.appendLine("Content: $body")
            sb.appendLine()
            sb.append("Summary:")
        }
        return sb.toString()
    }

    private fun cleanResponse(raw: String): String {
        var s = raw.trim()

        // strip known prefixes
        val prefixes = arrayOf(
            "\u6458\u8981\uff1a", "\u6458\u8981:", "\u6458\u8981 \uff1a", "\u6458\u8981 :",
            "Summary:", "Summary\uff1a", "summary:", "summary\uff1a",
            "Here is the summary:", "Here\u2019s the summary:",
            "\u4ee5\u4e0b\u662f\u6458\u8981\uff1a", "\u4ee5\u4e0b\u662f\u6458\u8981:"
        )
        for (p in prefixes) {
            if (s.startsWith(p, ignoreCase = true)) {
                s = s.substring(p.length).trim(); break
            }
        }

        // strip surrounding quotes
        val qc = charArrayOf('"', '\u201C', '\u201D', '\u300C', '\u300D')
        while (s.isNotEmpty() && s[0] in qc) s = s.substring(1)
        while (s.isNotEmpty() && s[s.length - 1] in qc) s = s.substring(0, s.length - 1)
        s = s.trim()

        // first paragraph only
        val pp = s.indexOf("\n\n")
        if (pp > 0) s = s.substring(0, pp).trim()

        // normalise whitespace
        s = s.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

        // truncate
        if (s.length > MAX_SUMMARY_LENGTH) {
            val cut = s.substring(0, MAX_SUMMARY_LENGTH)
            val last = cut.lastIndexOfAny(charArrayOf('\u3002', '\uFF01', '\uFF1F', '.', '!', '?'))
            s = if (last > MAX_SUMMARY_LENGTH / 2) cut.substring(0, last + 1)
            else cut.substring(0, MAX_SUMMARY_LENGTH - 3) + "..."
        }
        return s
    }
}
