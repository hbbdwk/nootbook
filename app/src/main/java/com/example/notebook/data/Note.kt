package com.example.notebook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val imagePaths: List<String> = emptyList(),  // 多图片路径
    val timestamp: Long = System.currentTimeMillis()
)
