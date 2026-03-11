package com.example.notebook

import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.notebook.data.Note
import com.example.notebook.startup.LifecycleAnchor
import com.example.notebook.startup.StartupOptimizer
import com.example.notebook.ui.NoteEditScreen
import com.example.notebook.ui.NoteListScreen
import com.example.notebook.ui.NoteViewModel
import com.example.notebook.ui.theme.NotebookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 触发 ACTIVITY_CREATE 锚点任务
        StartupOptimizer.execute(LifecycleAnchor.ACTIVITY_CREATE)

        // 等待首帧必需的关键任务完成（如 Room 数据库初始化）
        StartupOptimizer.awaitCriticalTasks()

        enableEdgeToEdge()

        // 注册首帧绘制回调
        val decorView = window.decorView
        decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                decorView.viewTreeObserver.removeOnPreDrawListener(this)
                // 首帧绘制完成 → 触发 POST_FIRST_FRAME + IDLE
                StartupOptimizer.onFirstFrameDrawn()
                return true
            }
        })

        setContent {
            NotebookTheme {
                val viewModel: NoteViewModel = viewModel()
                // 使用分页数据
                val pagedNotes = viewModel.pagedNotes.collectAsLazyPagingItems()
                var currentNote by remember { mutableStateOf<Note?>(null) }
                var isEditing by remember { mutableStateOf(false) }
                var isNewNote by remember { mutableStateOf(false) }

                if (isEditing) {
                    NoteEditScreen(
                        note = if (isNewNote) null else currentNote,
                        onBackClick = {
                            isEditing = false
                            isNewNote = false
                            currentNote = null
                        },
                        onSaveClick = { note ->
                            if (isNewNote) {
                                viewModel.insertNote(note)
                            } else {
                                viewModel.updateNote(note)
                            }
                            isEditing = false
                            isNewNote = false
                            currentNote = null
                        }
                    )
                } else {
                    NoteListScreen(
                        pagedNotes = pagedNotes,
                        onNoteClick = { note ->
                            currentNote = note
                            isEditing = true
                            isNewNote = false
                        },
                        onAddClick = {
                            currentNote = null
                            isEditing = true
                            isNewNote = true
                        },
                        onDeleteClick = { note ->
                            viewModel.deleteNote(note)
                        }
                    )
                }
            }
        }
    }
}
