package com.example.notebook.ai

/**
 * 摘要生成器接口
 * 定义生成摘要的通用接口，便于后续扩展其他实现
 */
interface SummaryGenerator {

    /**
     * 生成摘要
     * @param title 笔记标题
     * @param content 笔记内容
     * @return 生成的摘要，失败时返回 Result.failure
     */
    suspend fun generateSummary(title: String, content: String): Result<String>

    /**
     * 检查生成器是否可用
     * @return 是否可用
     */
    suspend fun isAvailable(): Boolean

    /**
     * 初始化生成器
     * @return 是否初始化成功
     */
    suspend fun initialize(): Boolean

    /**
     * 释放资源
     */
    fun release()
}

