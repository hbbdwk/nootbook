package com.example.notebook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.notebook.ai.SummaryErrorType
import com.example.notebook.ai.SummaryManager
import com.example.notebook.data.AppDatabase
import com.example.notebook.data.Note
import com.example.notebook.data.NoteRepository
import com.example.notebook.data.drawing.DrawingSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    val summaryManager: SummaryManager

    val allNotes: StateFlow<List<Note>>

    // 分页数据
    val pagedNotes: Flow<PagingData<Note>>

    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote: StateFlow<Note?> = _selectedNote

    // AI 可用状态
    val isAiAvailable: StateFlow<Boolean?>
        get() = summaryManager.isAiAvailable

    // 每条笔记的摘要错误类型
    val summaryErrors: StateFlow<Map<Long, SummaryErrorType>>
        get() = summaryManager.summaryErrors

    init {
        val noteDao = AppDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(noteDao)
        summaryManager = SummaryManager(application, repository)

        allNotes = repository.allNotes.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
        // 分页数据 - 缓存到 ViewModel 作用域
        pagedNotes = repository.getNotesPaged().cachedIn(viewModelScope)
    }

    fun getNoteById(id: Long, callback: (Note?) -> Unit) {
        viewModelScope.launch {
            val note = repository.getNoteById(id)
            _selectedNote.value = note
            callback(note)
        }
    }

    /**
     * 观察单个笔记的实时变化
     */
    fun observeNoteById(id: Long): Flow<Note?> {
        return repository.observeNoteById(id)
    }

    fun insertNote(note: Note) = viewModelScope.launch {
        val noteId = repository.insertNote(note)
        // 保存后自动生成摘要
        summaryManager.generateSummaryForNote(noteId, note.title, note.content)
    }

    fun updateNote(note: Note) = viewModelScope.launch {
        // 清理被替换的旧手绘文件
        val oldNote = repository.getNoteById(note.id)
        if (oldNote != null) {
            if (oldNote.drawingPath != null && oldNote.drawingPath != note.drawingPath) {
                DrawingSerializer.deleteFile(oldNote.drawingPath)
            }
            if (oldNote.drawingThumbnailPath != null && oldNote.drawingThumbnailPath != note.drawingThumbnailPath) {
                DrawingSerializer.deleteFile(oldNote.drawingThumbnailPath)
            }
        }
        repository.updateNote(note)
        // 更新后重新生成摘要
        summaryManager.generateSummaryForNote(note.id, note.title, note.content)
    }

    fun deleteNote(note: Note) = viewModelScope.launch {
        // 删除关联的手绘文件
        DrawingSerializer.deleteFile(note.drawingPath)
        DrawingSerializer.deleteFile(note.drawingThumbnailPath)
        repository.deleteNote(note)
    }

    fun deleteNoteById(id: Long) = viewModelScope.launch {
        val note = repository.getNoteById(id)
        if (note != null) {
            DrawingSerializer.deleteFile(note.drawingPath)
            DrawingSerializer.deleteFile(note.drawingThumbnailPath)
        }
        repository.deleteNoteById(id)
    }

    /**
     * 手动刷新摘要
     */
    fun regenerateSummary(note: Note) {
        summaryManager.regenerateSummary(note.id, note.title, note.content)
    }

    override fun onCleared() {
        super.onCleared()
        summaryManager.release()
    }
}
