package com.example.notebook.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.notebook.R
import com.example.notebook.ai.SummaryErrorType
import com.example.notebook.data.Note
import com.example.notebook.ui.components.SummaryCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    note: Note?,
    isAiAvailable: Boolean?,
    summaryErrorType: SummaryErrorType?,
    onBackClick: () -> Unit,
    onSaveClick: (Note) -> Unit,
    onRefreshSummary: (Note) -> Unit = {}
) {
    // Key editable state by note id so data arriving after first composition can populate fields.
    var title by remember(note?.id) { mutableStateOf(note?.title ?: "") }
    var content by remember(note?.id) { mutableStateOf(note?.content ?: "") }
    var imagePaths by remember(note?.id) { mutableStateOf(note?.imagePaths ?: emptyList()) }
    var hasChanges by remember { mutableStateOf(false) }
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImageIndex by remember { mutableIntStateOf(0) }

    val context = LocalContext.current

    // 图片预览对话框（支持多图）
    if (showImagePreview && imagePaths.isNotEmpty()) {
        ImagePreviewDialog(
            imagePaths = imagePaths,
            initialIndex = previewImageIndex,
            onDismiss = { showImagePreview = false }
        )
    }

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 复制图片到应用私有目录
            val inputStream = context.contentResolver.openInputStream(it)
            val fileName = "note_img_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            imagePaths = imagePaths + file.absolutePath
            hasChanges = true
        }
    }

    LaunchedEffect(title, content, imagePaths) {
        hasChanges = title != (note?.title ?: "") ||
                content != (note?.content ?: "") ||
                imagePaths != note?.imagePaths
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (note == null) stringResource(R.string.new_note) else stringResource(R.string.edit_note)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val newNote = if (note != null) {
                                note.copy(
                                    title = title,
                                    content = content,
                                    imagePaths = imagePaths,
                                    timestamp = System.currentTimeMillis()
                                )
                            } else {
                                Note(
                                    title = title,
                                    content = content,
                                    imagePaths = imagePaths
                                )
                            }
                            onSaveClick(newNote)
                        },
                        enabled = title.isNotEmpty() || content.isNotEmpty() || imagePaths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.title_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 图片网格预览区域
            if (imagePaths.isNotEmpty()) {
                val columns = 3
                val rows = imagePaths.chunked(columns)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEachIndexed { rowIndex, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEachIndexed { colIndex, path ->
                                val index = rowIndex * columns + colIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(File(path))
                                            .size(300)
                                            .build(),
                                        contentDescription = stringResource(R.string.note_image),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable {
                                                previewImageIndex = index
                                                showImagePreview = true
                                            },
                                        contentScale = ContentScale.Fit
                                    )
                                    IconButton(
                                        onClick = {
                                            // 删除图片文件
                                            File(path).delete()
                                            imagePaths = imagePaths.filterIndexed { i, _ -> i != index }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                RoundedCornerShape(12.dp)
                                            )
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.delete_image),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            repeat(columns - row.size) {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 添加图片按钮
            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (imagePaths.isNotEmpty()) stringResource(R.string.add_more_images) else stringResource(R.string.add_image))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.content_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                minLines = 10
            )

            // AI 摘要卡片（仅在编辑已有笔记时显示）
            if (note != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SummaryCard(
                    summary = note.summary,
                    status = note.summaryStatus,
                    isAiAvailable = isAiAvailable,
                    errorType = summaryErrorType,
                    onRefreshClick = { onRefreshSummary(note) }
                )
            }
        }
    }
}
