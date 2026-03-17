# Notebook 工程结构总览

更新时间：2026-03-17  
工程路径：`/Users/wangkun/AndroidStudioProjects/Notebook`

## 概览

- **工程类型**：Android（Gradle Kotlin DSL）
- **根项目名**：`Notebook`
- **模块**：仅 `:app`
- **UI 技术栈**：Jetpack Compose + Material 3
- **数据层**：Room（含 Paging 集成）
- **图片加载**：Coil
- **AI/摘要**：内置摘要生成器（含 MediaPipe GenAI 依赖 + TextRank 实现）
- **启动优化/监控**：自定义 `startup/` 任务编排与监控框架

## 构建与版本管理

### Gradle 入口

- 根 `settings.gradle.kts`：只 `include(":app")`，仓库源使用 `google()` + `mavenCentral()`，并设置 `FAIL_ON_PROJECT_REPOS`
- 根 `build.gradle.kts`：使用 Version Catalog 的插件别名（`libs.plugins.*`），在根工程 `apply false`

### 关键版本（`gradle/libs.versions.toml`）

- **AGP**：8.13.2
- **Kotlin**：2.0.21
- **KSP**：2.0.21-1.0.28
- **Compose BOM**：2025.05.01
- **Room**：2.6.1
- **Paging**：3.3.0
- **Coil**：2.6.0
- **MediaPipe GenAI**：0.10.24

### `:app` 模块构建要点（`app/build.gradle.kts`）

- **namespace / applicationId**：`com.example.notebook`
- **compileSdk / targetSdk**：36
- **minSdk**：24
- **Java/Kotlin**：JVM 11
- **buildFeatures**：Compose = true；BuildConfig = true
- **代码生成**：Room 使用 KSP（`ksp(libs.androidx.room.compiler)`）

## 应用入口

- **Application**：`.NotebookApplication`
- **Launcher Activity**：`.MainActivity`
- **Manifest**：`app/src/main/AndroidManifest.xml`

## 目录结构（高层）

```text
Notebook/
  app/                          # Android Application 模块（主要代码在此）
    build.gradle.kts
    proguard-rules.pro
    src/
      main/
        AndroidManifest.xml
        assets/
          startup_baseline.json
        java/
          com/example/notebook/  # 主要 Kotlin 源码（实际为 .kt 文件）
        res/
          drawable/
          mipmap-anydpi-v26/
          values/
          values-zh/
          xml/
      androidTest/
      test/
  gradle/
    libs.versions.toml           # Version Catalog（依赖/插件版本集中管理）
    wrapper/
  models/                        # 本地模型/权重文件（体积较大）
    gemma3-1b-it-int4.task
  scripts/
    download_model.sh            # 下载/准备模型脚本
  build.gradle.kts               # 根构建脚本（插件别名 apply false）
  settings.gradle.kts            # 模块 include 与仓库源配置
  gradle.properties
  local.properties               # 本机 Android SDK 路径等（通常不提交）
  plan.md
  LOCAL_SUMMARIZER_IMPLEMENTATION.md
  .claude/                       # 本项目的 Claude/Cursor 相关辅助文档与 skills
```

## 代码包结构（`app/src/main/java/com/example/notebook`）

### 顶层

- `MainActivity.kt`：应用主入口 Activity（Compose UI 启动点）
- `NotebookApplication.kt`：Application 初始化与全局配置

### `ai/`（摘要/生成相关）

- `SummaryGenerator.kt`：摘要生成接口/抽象
- `TextRankSummaryGenerator.kt`：TextRank 摘要实现
- `MediaPipeLlmSummaryGenerator.kt`：基于 MediaPipe GenAI 的摘要实现
- `SummaryManager.kt`：摘要策略/调度管理
- `SummaryErrorType.kt`：错误类型定义

### `data/`（Room + Repository）

- `Note.kt`：实体
- `NoteDao.kt`：DAO
- `AppDatabase.kt`：Room Database
- `Converters.kt`：类型转换
- `NoteRepository.kt`：Repository（业务数据访问入口）

### `startup/`（启动任务编排/监控）

- `StartupTask.kt` / `TaskRegistry.kt`：任务定义与注册
- `TaskGraph.kt` / `TaskScheduler.kt`：任务依赖图与调度
- `StartupMonitor.kt` / `StartupReport.kt`：监控与报告
- `StartupOptimizer.kt` / `TaskAnalyzer.kt` / `AntiRegression.kt`：优化与分析/防回归
- `tasks/DatabaseInitTask.kt`：示例/内置任务（数据库初始化）

### `ui/`（Compose UI + ViewModel）

- `NoteListScreen.kt`：列表页
- `NoteEditScreen.kt`：编辑页
- `NoteViewModel.kt`：状态与业务逻辑（Compose ViewModel）
- `ImagePreviewDialog.kt`：图片预览
- `components/SummaryCard.kt`：可复用组件
- `theme/`：Compose 主题（`Color.kt` / `Type.kt` / `Theme.kt`）

## 资源与配置

- **多语言**：`res/values/strings.xml` + `res/values-zh/strings.xml`
- **备份/数据导出规则**：`res/xml/backup_rules.xml`、`res/xml/data_extraction_rules.xml`
- **Launcher 图标**：`res/mipmap-anydpi-v26/*`、`res/drawable/*`
- **启动基线数据**：`assets/startup_baseline.json`

## 额外说明（建议）

- `models/gemma3-1b-it-int4.task` 体积很大（约 0.55GB），适合在文档中标注用途，并在 `.gitignore` / LFS 策略上确认是否符合团队约定。
- 当前工程模块较少（仅 `:app`），如果后续增长，可考虑按 **feature/data/domain** 拆分为多 module，以缩短编译时间并隔离依赖。

