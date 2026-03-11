package com.example.notebook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.notebook.data.AppDatabase
import com.example.notebook.data.Note
import com.example.notebook.data.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository

    val allNotes: StateFlow<List<Note>>

    // 分页数据
    val pagedNotes: Flow<PagingData<Note>>

    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote: StateFlow<Note?> = _selectedNote

    init {
        val noteDao = AppDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(noteDao)
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

    fun insertNote(note: Note) = viewModelScope.launch {
        repository.insertNote(note)
    }

    fun updateNote(note: Note) = viewModelScope.launch {
        repository.updateNote(note)
    }

    fun deleteNote(note: Note) = viewModelScope.launch {
        repository.deleteNote(note)
    }

    fun deleteNoteById(id: Long) = viewModelScope.launch {
        repository.deleteNoteById(id)
    }
}
