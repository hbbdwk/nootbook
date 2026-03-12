package com.example.notebook.ai

/**
 * 摘要失败类型：用于在 UI 层给出可操作的错误提示。
 */
enum class SummaryErrorType {
    MODEL_MISSING,
    INIT_FAILED,
    INFERENCE_FAILED
}

class SummaryGenerationException(
    val errorType: SummaryErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

