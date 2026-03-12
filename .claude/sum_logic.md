# SummaryGenerator 本地模型摘要生成逻辑分析

## 一、项目整体结构

该项目是一个 Android 笔记应用，采用 MVVM + Clean Architecture 架构。主要包含以下层次：

```
UI 层 (Compose)
    ↓
ViewModel 层 (NoteViewModel)
    ↓
业务逻辑层 (SummaryManager)
    ↓
摘要生成器 (SummaryGenerator 接口及实现)
    ↓
数据层 (Room Database)
```

---

## 二、SummaryGenerator 接口及实现类

### 1. SummaryGenerator 接口
**文件位置**: `app/src/main/java/com/example/notebook/ai/SummaryGenerator.kt`

```kotlin
interface SummaryGenerator {
    // 生成摘要
    suspend fun generateSummary(title: String, content: String): Result<String>

    // 检查生成器是否可用
    suspend fun isAvailable(): Boolean

    // 初始化生成器
    suspend fun initialize(): Boolean

    // 释放资源
    fun release()
}
```

该接口定义了摘要生成器的通用契约，便于扩展其他实现。

---

### 2. MediaPipeLlmSummaryGenerator (当前主要实现)
**文件位置**: `app/src/main/java/com/example/notebook/ai/MediaPipeLlmSummaryGenerator.kt`

**核心特性**:
- 使用 **MediaPipe LLM Inference** 库进行设备端摘要生成
- 使用 **Gemma 3 1B IT int4** 模型（约 550MB，运行时 1-2GB RAM）
- 模型文件路径: `app/files/models/gemma3-1b-it-int4.task`
- 模型需要通过 `scripts/download_model.sh` 脚本下载

**关键配置**:
```kotlin
MAX_TOKENS = 512
MAX_SUMMARY_LENGTH = 200
TOP_K = 40
TEMPERATURE = 0.7f
```

**Prompt 构建逻辑**:
- 根据内容语言（中文/英文）使用不同的 prompt
- 中文 prompt: "请为以下笔记生成一个简洁的中文摘要..."
- 英文 prompt: "Generate a concise summary for the note below..."
- 截断过长内容（保留前 1500 字符）

**响应后处理**:
- 去除前缀（如 "摘要："、"Summary:"）
- 去除引号
- 截断到最大长度，在句子边界处切断

---

### 3. TextRankSummaryGenerator (备选/离线方案)
**文件位置**: `app/src/main/java/com/example/notebook/ai/TextRankSummaryGenerator.kt`

**核心特性**:
- 纯 Kotlin 实现的抽取式摘要算法
- **完全离线运行**，不依赖任何 native 代码或模型文件
- 基于 **TextRank** 图排序算法

**算法流程**:
1. 句子切分（支持中英文标点）
2. 分词（中文按字，英文按单词）
3. TF-IDF 向量化
4. 构建句子相似度图
5. PageRank 迭代收敛
6. 标题相关性加权 + 位置加权
7. 贪心选句（去重）

**注意**: 当前代码中已不再使用 TextRank 作为降级方案，采用严格的 MediaPipe LLM 模式。

---

### 4. 已删除的实现类
根据 git 状态，以下文件已被删除：
- `LlamaCppSummaryGenerator.kt` (原 llama.cpp 本地模型)
- `GeminiNanoSummaryGenerator.kt` (原 Google Gemini Nano)

---

## 三、SummaryManager 核心逻辑
**文件位置**: `app/src/main/java/com/example/notebook/ai/SummaryManager.kt`

**设计模式**: 单例模式，管理所有摘要生成任务

**核心特性**:

1. **严格本地 LLM 模式**
   - 仅使用 MediaPipe LLM 作为生成器
   - 不做 TextRank 降级

2. **防抖机制**
   - 延迟 5 秒（`DEBOUNCE_DELAY_MS = 5000L`）
   - 避免频繁生成

3. **重试机制**
   - 最大重试次数: 2 次
   - 指数退避策略

4. **状态管理**
   - `_isAiAvailable`: AI 可用性状态（StateFlow）
   - `_summaryErrors`: 每条笔记的错误类型映射
   - `processingNotes`: 当前正在处理的笔记集合

5. **错误类型**
```kotlin
enum class SummaryErrorType {
    MODEL_MISSING,   // 模型文件缺失
    INIT_FAILED,     // 初始化失败
    INFERENCE_FAILED // 推理失败
}
```

---

## 四、UI 层调用流程

### 1. MainActivity 入口
**文件位置**: `app/src/main/java/com/example/notebook/MainActivity.kt`

```kotlin
// 创建 ViewModel
val viewModel: NoteViewModel = viewModel()

// 获取 AI 可用状态
val isAiAvailable by viewModel.isAiAvailable.collectAsState()
val summaryErrors by viewModel.summaryErrors.collectAsState()

// 观察当前笔记的实时变化
val observedNote by remember(currentNoteId) {
    currentNoteId?.let { viewModel.observeNoteById(it) }
        ?: flowOf(null)
}.collectAsState(initial = null)
```

### 2. NoteViewModel
**文件位置**: `app/src/main/java/com/example/notebook/ui/NoteViewModel.kt`

```kotlin
class NoteViewModel(application: Application) : AndroidViewModel(application) {
    val summaryManager: SummaryManager

    // 插入笔记时自动生成摘要
    fun insertNote(note: Note) = viewModelScope.launch {
        val noteId = repository.insertNote(note)
        summaryManager.generateSummaryForNote(noteId, note.title, note.content)
    }

    // 更新笔记时重新生成摘要
    fun updateNote(note: Note) = viewModelScope.launch {
        repository.updateNote(note)
        summaryManager.generateSummaryForNote(note.id, note.title, note.content)
    }

    // 手动刷新摘要
    fun regenerateSummary(note: Note) {
        summaryManager.regenerateSummary(note.id, note.title, note.content)
    }
}
```

### 3. 摘要卡片组件
**文件位置**: `app/src/main/java/com/example/notebook/ui/components/SummaryCard.kt`

**显示状态**:
- `PENDING`: "待生成"
- `GENERATING`: 动画加载指示器
- `COMPLETED`: 显示摘要内容
- `FAILED`: 显示错误信息

### 4. NoteEditScreen
**文件位置**: `app/src/main/java/com/example/notebook/ui/NoteEditScreen.kt`

```kotlin
// 编辑已有笔记时显示摘要卡片
if (note != null) {
    SummaryCard(
        summary = note.summary,
        status = note.summaryStatus,
        isAiAvailable = isAiAvailable,
        errorType = summaryErrorType,
        onRefreshClick = { onRefreshSummary(note) }
    )
}
```

---

## 五、数据库层设计

**Note 实体** (`Note.kt`):
```kotlin
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val imagePaths: List<String> = emptyList(),
    val summary: String? = null,                  // AI 摘要
    val summaryStatus: SummaryStatus = SummaryStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis()
)
```

**SummaryStatus 枚举**:
```kotlin
enum class SummaryStatus {
    PENDING,      // 待生成
    GENERATING,   // 生成中
    COMPLETED,    // 已完成
    FAILED        // 生成失败
}
```

---

## 六、整体流程图

```
用户操作 (保存/更新笔记)
        ↓
NoteViewModel.insertNote() / updateNote()
        ↓
SummaryManager.generateSummaryForNote()
        ↓
    [防抖等待 5 秒]
        ↓
检查 AI 可用性 (isAvailable)
        ↓
生成摘要 (调用 MediaPipe LLM)
        ↓
    [失败重试最多 2 次]
        ↓
更新数据库 (repository.updateSummary)
        ↓
UI 观察 Flow 变化自动刷新
```

---

## 七、架构总结

| 层次 | 组件 | 职责 |
|------|------|------|
| **UI** | MainActivity, NoteEditScreen, NoteListScreen | 展示笔记和摘要，处理用户交互 |
| **ViewModel** | NoteViewModel | 协调数据和业务逻辑，管理 SummaryManager |
| **业务逻辑** | SummaryManager | 任务调度、防抖重试、状态管理 |
| **生成器** | MediaPipeLlmSummaryGenerator | 设备端 LLM 推理生成摘要 |
| **数据层** | NoteRepository, NoteDao | Room 数据库操作 |

**当前设计特点**:
1. **纯本地**: 所有摘要生成在设备端完成，无需网络
2. **严格模式**: 仅使用 MediaPipe LLM，不做降级方案
3. **响应式**: 使用 Kotlin Flow 实现数据流驱动 UI 更新
4. **容错**: 支持重试、防抖、错误状态追踪
