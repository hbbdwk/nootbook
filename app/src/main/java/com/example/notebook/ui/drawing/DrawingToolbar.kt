package com.example.notebook.ui.drawing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notebook.R
import com.example.notebook.data.drawing.DrawingTool
import com.example.notebook.data.drawing.PRESET_COLORS
import com.example.notebook.data.drawing.StrokeWidth

@Composable
fun DrawingToolbar(
    currentTool: DrawingTool,
    currentColor: Int,
    currentWidth: StrokeWidth,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolSelected: (DrawingTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onWidthSelected: (StrokeWidth) -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // —— 第一行：颜色 + 粗细 ——
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 颜色选择
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESET_COLORS.forEach { color ->
                        val selected = color == currentColor && currentTool == DrawingTool.PEN
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Gray.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    onColorSelected(color)
                                    if (currentTool != DrawingTool.PEN) onToolSelected(DrawingTool.PEN)
                                }
                        )
                    }
                }
                // 粗细选择
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StrokeWidth.entries.forEach { w ->
                        FilterChip(
                            selected = w == currentWidth,
                            onClick = { onWidthSelected(w) },
                            label = { Text(w.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // —— 第二行：工具 + 撤销/重做 ——
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 画笔
                    FilledIconToggleButton(
                        checked = currentTool == DrawingTool.PEN,
                        onCheckedChange = { onToolSelected(DrawingTool.PEN) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Create,
                            contentDescription = stringResource(R.string.drawing_pen),
                            Modifier.size(20.dp)
                        )
                    }
                    // 橡皮擦
                    FilledIconToggleButton(
                        checked = currentTool == DrawingTool.ERASER,
                        onCheckedChange = { onToolSelected(DrawingTool.ERASER) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            stringResource(R.string.drawing_eraser),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onUndoClick, enabled = canUndo) {
                        Text(stringResource(R.string.drawing_undo))
                    }
                    TextButton(onClick = onRedoClick, enabled = canRedo) {
                        Text(stringResource(R.string.drawing_redo))
                    }
                }
            }
        }
    }
}

