package com.example.notebook.ai

import android.content.Context
import android.util.Log
import com.example.notebook.data.NoteRepository
import com.example.notebook.data.SummaryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 摘要管理器
 *
 * 负责协调摘要生成任务，管理生成状态，处理错误恢复
 */
class SummaryManager(
    context: Context,
    private val repository: NoteRepository
) {
    companion object {
        private const val TAG = "SummaryManager"
        private const val MIN_CONTENT_LENGTH = 20    // 最小内容长度
        private const val DEBOUNCE_DELAY_MS = 5000L  // 防抖延迟（LLM推理较慢，增大防抖）
        private const val MAX_RETRY_COUNT = 2        // 最大重试次数
    }

    // 严格本地 LLM 模式：仅允许 MediaPipe LLM 生成，不做 TextRank 降级。
    private val mediaPipeGenerator = MediaPipeLlmSummaryGenerator(context)
    private var activeGenerator: SummaryGenerator? = mediaPipeGenerator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // AI 可用状态
    private val _isAiAvailable = MutableStateFlow<Boolean?>(null)
    val isAiAvailable: StateFlow<Boolean?> = _isAiAvailable

    // 每条笔记的摘要错误类型（仅内存态，用于 UI 细分提示）
    private val _summaryErrors = MutableStateFlow<Map<Long, SummaryErrorType>>(emptyMap())
    val summaryErrors: StateFlow<Map<Long, SummaryErrorType>> = _summaryErrors

    // 当前正在处理的笔记 ID
    private val processingNotes = mutableSetOf<Long>()

    init {
        // 检查 AI 可用性
        scope.launch {
            checkAvailability()
        }
    }

    /**
     * 检查 AI 是否可用
     */
    suspend fun checkAvailability(): Boolean {
        val available = mediaPipeGenerator.isAvailable()
        if (available) {
            activeGenerator = mediaPipeGenerator
            _isAiAvailable.value = true
            Log.d(TAG, "AI availability: true, active=${mediaPipeGenerator.javaClass.simpleName}")
            return true
        }

        val reason = mediaPipeGenerator.lastAvailabilityError
        Log.w(TAG, "MediaPipe LLM unavailable, reason=$reason")

        activeGenerator = null
        _isAiAvailable.value = false
        Log.d(TAG, "AI availability: false")
        return false
    }

    /**
     * 为笔记生成摘要（带防抖）
     *
     * @param noteId 笔记 ID
     * @param title 笔记标题
     * @param content 笔记内容
     * @param immediate 是否立即生成（跳过防抖）
     */
    fun generateSummaryForNote(
        noteId: Long,
        title: String,
        content: String,
        immediate: Boolean = false
    ) {
        scope.launch {
            // 内容太短，跳过生成
            if (content.length < MIN_CONTENT_LENGTH && title.length < MIN_CONTENT_LENGTH) {
                Log.d(TAG, "Content too short, skipping summary for note $noteId")
                return@launch
            }

            // 检查是否正在处理
            mutex.withLock {
                if (processingNotes.contains(noteId)) {
                    Log.d(TAG, "Note $noteId is already being processed")
                    return@launch
                }
                processingNotes.add(noteId)
            }

            try {
                // 防抖延迟
                if (!immediate) {
                    delay(DEBOUNCE_DELAY_MS)
                }

                // 更新状态为生成中
                repository.updateSummaryStatus(noteId, SummaryStatus.GENERATING)
                updateSummaryError(noteId, null)

                // 检查 AI 可用性
                if (_isAiAvailable.value != true) {
                    val available = checkAvailability()
                    if (!available) {
                        Log.w(TAG, "AI not available, marking note $noteId as failed")
                        updateSummaryError(
                            noteId,
                            mediaPipeGenerator.lastAvailabilityError ?: SummaryErrorType.MODEL_MISSING
                        )
                        repository.updateSummaryStatus(noteId, SummaryStatus.FAILED)
                        return@launch
                    }
                }

                // 生成摘要（带重试）
                var lastException: Exception? = null
                for (attempt in 1..MAX_RETRY_COUNT) {
                    val result = generateWithLlmOnly(title, content)

                    result.onSuccess { summary ->
                        repository.updateSummary(noteId, summary, SummaryStatus.COMPLETED)
                        updateSummaryError(noteId, null)
                        Log.d(TAG, "Summary generated for note $noteId")
                        return@launch
                    }.onFailure { e ->
                        lastException = e as? Exception
                        updateSummaryError(noteId, mapErrorType(e))
                        Log.w(TAG, "Attempt $attempt failed for note $noteId", e)
                        if (attempt < MAX_RETRY_COUNT) {
                            delay(1000L * attempt) // 指数退避
                        }
                    }
                }

                // 所有重试都失败
                Log.e(TAG, "All attempts failed for note $noteId", lastException)
                repository.updateSummaryStatus(noteId, SummaryStatus.FAILED)

            } finally {
                mutex.withLock {
                    processingNotes.remove(noteId)
                }
            }
        }
    }

    /**
     * 重新生成摘要
     */
    fun regenerateSummary(noteId: Long, title: String, content: String) {
        scope.launch {
            // 先重置状态
            repository.updateSummary(noteId, null, SummaryStatus.PENDING)
            updateSummaryError(noteId, null)
            // 立即生成
            generateSummaryForNote(noteId, title, content, immediate = true)
        }
    }

    private suspend fun generateWithLlmOnly(title: String, content: String): Result<String> {
        val generator = activeGenerator ?: mediaPipeGenerator
        if (!generator.isAvailable()) {
            val reason = mediaPipeGenerator.lastAvailabilityError ?: SummaryErrorType.MODEL_MISSING
            return Result.failure(SummaryGenerationException(reason, "LLM unavailable"))
        }

        val result = generator.generateSummary(title, content)
        if (result.isSuccess) {
            activeGenerator = generator
            Log.d(TAG, "Summary generated by: ${generator.javaClass.simpleName}")
        }
        return result
    }

    private fun mapErrorType(error: Throwable): SummaryErrorType {
        return when (error) {
            is SummaryGenerationException -> error.errorType
            else -> SummaryErrorType.INFERENCE_FAILED
        }
    }

    private fun updateSummaryError(noteId: Long, errorType: SummaryErrorType?) {
        _summaryErrors.value = _summaryErrors.value.toMutableMap().apply {
            if (errorType == null) remove(noteId) else put(noteId, errorType)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        mediaPipeGenerator.release()
    }
}
