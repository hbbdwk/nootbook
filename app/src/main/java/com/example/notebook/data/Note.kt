package com.example.notebook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 摘要生成状态
 */
enum class SummaryStatus {
    PENDING,      // 待生成
    GENERATING,   // 生成中
    COMPLETED,    // 已完成
    FAILED        // 生成失败
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val imagePaths: List<String> = emptyList(),  // 多图片路径
    val summary: String? = null,                  // AI 生成的摘要
    val summaryStatus: SummaryStatus = SummaryStatus.PENDING,  // 摘要状态
    val timestamp: Long = System.currentTimeMillis()
)
