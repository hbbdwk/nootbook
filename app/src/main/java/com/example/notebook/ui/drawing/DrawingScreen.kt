package com.example.notebook.ui.drawing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.example.notebook.R
import com.example.notebook.data.drawing.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    existingDrawingPath: String?,
    onSave: (drawingPath: String, thumbnailPath: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var currentColor by remember { mutableIntStateOf(PRESET_COLORS[0]) }
    var currentWidth by remember { mutableStateOf(StrokeWidth.MEDIUM) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    var canvasView by remember { mutableStateOf<DrawingCanvasView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val view = canvasView ?: return@IconButton
                        if (!view.hasContent()) { onBack(); return@IconButton }
                        val data = view.getDrawingData()
                        val ts = System.currentTimeMillis()
                        val drawPath = DrawingSerializer.saveToFile(context, ts, data)
                        val thumb = view.exportBitmap()
                        val thumbPath = if (thumb != null) {
                            DrawingSerializer.saveThumbnail(context, ts, thumb).also { thumb.recycle() }
                        } else ""
                        onSave(drawPath, thumbPath)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            DrawingToolbar(
                currentTool = currentTool,
                currentColor = currentColor,
                currentWidth = currentWidth,
                canUndo = canUndo,
                canRedo = canRedo,
                onToolSelected = { t -> currentTool = t; canvasView?.currentTool = t },
                onColorSelected = { c -> currentColor = c; canvasView?.penColor = c },
                onWidthSelected = { w ->
                    currentWidth = w
                    canvasView?.penStrokeWidth = w.value
                    canvasView?.eraserWidth = w.value * 3f
                },
                onUndoClick = { canvasView?.undo() },
                onRedoClick = { canvasView?.redo() }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                DrawingCanvasView(ctx).apply {
                    this.currentTool = currentTool
                    this.penColor = currentColor
                    this.penStrokeWidth = currentWidth.value
                    this.eraserWidth = currentWidth.value * 3f
                    this.onUndoRedoChanged = { u, r -> canUndo = u; canRedo = r }
                    canvasView = this
                    existingDrawingPath?.let { path ->
                        DrawingSerializer.loadFromFile(path)?.let { loadDrawingData(it) }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

