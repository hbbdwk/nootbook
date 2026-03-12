package com.example.notebook.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    // 分页数据流
    fun getNotesPaged(): Flow<PagingData<Note>> {
        return Pager(
            config = PagingConfig(
                pageSize = 9,              // 每页加载数量
                enablePlaceholders = false, // 禁用占位符
                prefetchDistance = 2        // 预加载距离
            ),
            pagingSourceFactory = { noteDao.getNotesPagingSource() }
        ).flow
    }

    suspend fun getNoteById(id: Long): Note? {
        return noteDao.getNoteById(id)
    }

    fun observeNoteById(id: Long): Flow<Note?> {
        return noteDao.observeNoteById(id)
    }

    suspend fun insertNote(note: Note): Long {
        return noteDao.insertNote(note)
    }

    suspend fun updateNote(note: Note) {
        noteDao.updateNote(note)
    }

    suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note)
    }

    suspend fun deleteNoteById(id: Long) {
        noteDao.deleteNoteById(id)
    }

    // 摘要相关方法
    suspend fun updateSummary(id: Long, summary: String?, status: SummaryStatus) {
        noteDao.updateSummary(id, summary, status)
    }

    suspend fun updateSummaryStatus(id: Long, status: SummaryStatus) {
        noteDao.updateSummaryStatus(id, status)
    }

    suspend fun getPendingNotes(): List<Note> {
        return noteDao.getNotesByStatus(SummaryStatus.PENDING)
    }
}
