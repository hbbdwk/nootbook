package com.example.notebook.data

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    // 分页查询 - Paging 3 使用
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getNotesPagingSource(): PagingSource<Int, Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    // 观察单个笔记的变化
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNoteById(id: Long): Flow<Note?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    // 更新摘要和状态
    @Query("UPDATE notes SET summary = :summary, summaryStatus = :status WHERE id = :id")
    suspend fun updateSummary(id: Long, summary: String?, status: SummaryStatus)

    // 更新摘要状态
    @Query("UPDATE notes SET summaryStatus = :status WHERE id = :id")
    suspend fun updateSummaryStatus(id: Long, status: SummaryStatus)

    // 获取待生成摘要的笔记
    @Query("SELECT * FROM notes WHERE summaryStatus = :status ORDER BY timestamp DESC")
    suspend fun getNotesByStatus(status: SummaryStatus): List<Note>
}
