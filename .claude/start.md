# RAG 启动优化框架设计计划

> 项目: Notebook (Jetpack Compose + Room + Paging3)
> 日期: 2026-03-03
> 状态: ✅ Phase 1-4 已实施完成，BUILD SUCCESSFUL

---

## 一、现状分析

### 1.1 当前启动链路

```
Application (系统默认，无自定义)
  └─ MainActivity.onCreate()
       ├─ enableEdgeToEdge()
       ├─ setContent {}
       │    ├─ NoteViewModel 初始化
       │    │    ├─ AppDatabase.getDatabase(app)  ← Room 数据库创建（重操作）
       │    │    ├─ NoteRepository 创建
       │    │    ├─ allNotes StateFlow 订阅
       │    │    └─ pagedNotes Flow 订阅 + cachedIn
       │    └─ Compose UI 渲染
       └─ 首帧绘制完成
```

### 1.2 当前问题识别

| # | 问题 | 影响 | 严重度 |
|---|------|------|--------|
| 1 | 无自定义 Application，缺少启动生命周期管控点 | 无法在 Activity 之前预热任何组件 | 🔴 高 |
| 2 | Room 数据库在 ViewModel.init 中懒创建（首次触发 synchronized + build） | 首帧前阻塞主线程 | 🔴 高 |
| 3 | `fallbackToDestructiveMigration()` 无回调监控 | 用户数据可能丢失无感知 | 🟡 中 |
| 4 | 无启动耗时监控 | 无法量化优化效果、无法防劣化 | 🔴 高 |
| 5 | 所有初始化都在主线程顺序执行 | 无法利用多核并行 | 🟡 中 |
| 6 | 无 Splash / StartupProfile | 冷启动无视觉反馈、无 Baseline Profile 加速 | 🟡 中 |

---

## 二、框架设计目标

### 2.1 核心目标

1. **快速冷启动**: 首帧渲染时间 ≤ 300ms（当前估算 500ms+）
2. **防劣化**: 内建基线对比机制，CI 中自动检测启动耗时回归
3. **有序调度**: 业务初始化任务按依赖关系、优先级、线程要求有序执行
4. **可观测**: 每个启动任务的耗时、状态可追踪、可上报

### 2.2 设计原则

- **RAG 思路（Retrieve → Analyze → Generate）**:
  - **Retrieve**: 自动收集所有启动任务的注册信息（耗时、依赖、线程要求）
  - **Analyze**: 运行时分析依赖图，计算最优调度顺序，检测环形依赖
  - **Generate**: 动态生成执行计划，按拓扑序 + 优先级并行/串行调度

---

## 三、架构设计

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                    StartupOptimizer                      │
│  (Application.onCreate 入口，全局单例)                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  TaskRegistry │  │ TaskAnalyzer │  │ TaskScheduler │  │
│  │  (Retrieve)   │  │  (Analyze)   │  │  (Generate)   │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬────────┘  │
│         │                 │                  │           │
│         ▼                 ▼                  ▼           │
│  ┌──────────────────────────────────────────────────┐   │
│  │              StartupTaskGraph                     │   │
│  │  (DAG: 有向无环图，拓扑排序 + 关键路径分析)        │   │
│  └──────────────────────────────────────────────────┘   │
│                          │                               │
│  ┌──────────────┐  ┌─────┴──────┐  ┌────────────────┐  │
│  │ StartupMonitor│  │ TaskRunner │  │ AntiRegression │  │
│  │ (耗时追踪)    │  │ (执行引擎) │  │ (防劣化检测)    │  │
│  └──────────────┘  └────────────┘  └────────────────┘  │
│                                                          │
├─────────────────────────────────────────────────────────┤
│                    Lifecycle Anchors                      │
│  App.attach → App.onCreate → Activity.onCreate → 首帧    │
│     T0           T1              T2               T3     │
└─────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

#### 3.2.1 `StartupTask` — 启动任务抽象

```
StartupTask {
    name: String                    // 任务唯一标识
    priority: Priority              // CRITICAL / HIGH / NORMAL / LOW
    runOn: ThreadPolicy             // MAIN_THREAD / IO / DEFAULT
    dependsOn: List<String>         // 依赖的前置任务名
    waitOnMainThread: Boolean       // 是否需要在主线程等待完成（阻塞首帧）
    lifecycle: LifecycleAnchor      // 期望执行时机: APP_CREATE / ACTIVITY_CREATE / POST_FIRST_FRAME / IDLE
    timeout: Long                   // 超时时间(ms)
    execute(): Unit                 // 实际执行逻辑
}
```

**优先级定义**:

| Priority | 含义 | 示例 |
|----------|------|------|
| CRITICAL | 首帧前必须完成 | 无 (当前项目) |
| HIGH | 首屏数据依赖 | Room 数据库初始化 |
| NORMAL | 用户交互前完成 | 图片加载器配置 |
| LOW | 可延迟到空闲时 | 统计 SDK、崩溃上报 |

**生命周期锚点定义**:

| Anchor | 时机 | 说明 |
|--------|------|------|
| APP_ATTACH | Application.attachBaseContext | 最早时机，仅限极少数场景 |
| APP_CREATE | Application.onCreate | 常规初始化 |
| ACTIVITY_CREATE | MainActivity.onCreate 之前 | 与 UI 相关的初始化 |
| POST_FIRST_FRAME | 首帧绘制完成后 | 非首屏必需的初始化 |
| IDLE | 主线程空闲时 | 完全可延迟的任务 |

#### 3.2.2 `TaskRegistry` — 任务注册中心 (Retrieve)

```
TaskRegistry {
    register(task: StartupTask)
    registerAll(tasks: List<StartupTask>)
    getTasks(anchor: LifecycleAnchor): List<StartupTask>
    getAllTasks(): List<StartupTask>
    validate(): ValidationResult     // 检测重名、循环依赖
}
```

**注册方式**:
- **手动注册**: 在 Application 中显式注册（当前项目推荐，简单直接）
- **未来扩展**: 可通过 KSP 注解自动发现 `@StartupTask` 标注的类

#### 3.2.3 `TaskAnalyzer` — 依赖分析器 (Analyze)

```
TaskAnalyzer {
    buildGraph(tasks: List<StartupTask>): TaskGraph
    topologicalSort(graph: TaskGraph): List<List<StartupTask>>  // 按层级分组
    detectCycle(graph: TaskGraph): List<String>?                 // 环检测
    criticalPath(graph: TaskGraph): CriticalPathInfo             // 关键路径
    estimateParallelTime(graph: TaskGraph): Long                 // 预估并行耗时
}
```

核心算法:
- **Kahn 拓扑排序**: 确定执行层级，同一层级内可并行
- **环检测**: 启动时 debug 模式下检测并 crash，防止死锁
- **关键路径**: 识别启动瓶颈任务

#### 3.2.4 `TaskScheduler` — 调度执行器 (Generate)

```
TaskScheduler {
    execute(anchor: LifecycleAnchor)           // 触发某个锚点的所有任务
    awaitMainThreadTasks(anchor): Boolean      // 等待需要阻塞主线程的任务
    cancel()                                    // 取消未执行的任务
}
```

调度策略:
- **主线程任务**: 按拓扑序在主线程顺序执行
- **IO 任务**: 提交到 `Dispatchers.IO`，尊重依赖关系
- **同层并行**: 无依赖关系的任务同时发射
- **跨层等待**: 下一层等待上一层全部完成（或指定依赖完成）

#### 3.2.5 `StartupMonitor` — 启动监控

```
StartupMonitor {
    onTaskStart(taskName: String, timestamp: Long)
    onTaskEnd(taskName: String, timestamp: Long, success: Boolean)
    onAnchorReached(anchor: LifecycleAnchor, timestamp: Long)
    getReport(): StartupReport
    getTaskDuration(taskName: String): Long
}
```

**StartupReport 结构**:
```
StartupReport {
    totalStartupTime: Long           // App.attach → 首帧
    anchorTimestamps: Map<Anchor, Long>
    taskDetails: List<TaskDetail> {
        name, duration, thread, startTime, endTime, success
    }
    criticalPathDuration: Long
    parallelEfficiency: Float        // 并行效率 = 串行总耗时 / 实际耗时
}
```

#### 3.2.6 `AntiRegression` — 防劣化检测

```
AntiRegression {
    baseline: StartupBaseline        // 基线数据（从文件/配置读取）
    check(report: StartupReport): RegressionResult
}
```

**防劣化策略**:

| 维度 | 基线 | 阈值 | 动作 |
|------|------|------|------|
| 总启动耗时 | 历史 P50 | +15% | ⚠️ Warning log |
| 总启动耗时 | 历史 P50 | +30% | 🔴 Debug crash |
| 单任务耗时 | 上次记录 | +50ms | ⚠️ Warning log |
| 新增主线程任务 | 无 | 任何新增 | 📝 Info log |
| 任务超时 | timeout 配置 | 超时 | ⚠️ Warning + 降级 |

---

## 四、本项目具体实施方案

### 4.1 Phase 1: 基础设施搭建（启动框架核心）

#### 文件清单

| 操作 | 文件路径 | 说明 |
|------|---------|------|
| 新建 | `startup/StartupTask.kt` | 启动任务抽象基类 |
| 新建 | `startup/StartupEnums.kt` | Priority、ThreadPolicy、LifecycleAnchor 枚举 |
| 新建 | `startup/TaskRegistry.kt` | 任务注册中心 |
| 新建 | `startup/TaskGraph.kt` | 任务依赖图（DAG） |
| 新建 | `startup/TaskAnalyzer.kt` | 拓扑排序、环检测、关键路径 |
| 新建 | `startup/TaskScheduler.kt` | 协程调度执行引擎 |
| 新建 | `startup/StartupMonitor.kt` | 耗时监控 |
| 新建 | `startup/StartupOptimizer.kt` | 统一入口（门面模式） |
| 新建 | `startup/AntiRegression.kt` | 防劣化检测 |
| 新建 | `startup/StartupReport.kt` | 报告数据类 |

#### 关键实现要点

1. **协程驱动调度**: 使用 `kotlinx.coroutines` 的 `async/await` 实现并行调度
2. **CountDownLatch 语义**: 使用 `CompletableDeferred` 实现任务间等待
3. **主线程安全**: 使用 `withContext(Dispatchers.Main)` 确保主线程任务在主线程执行

### 4.2 Phase 2: Application 改造 + 任务注册

#### 文件清单

| 操作 | 文件路径 | 说明 |
|------|---------|------|
| 新建 | `NotebookApplication.kt` | 自定义 Application |
| 修改 | `AndroidManifest.xml` | 声明自定义 Application |
| 新建 | `startup/tasks/DatabaseInitTask.kt` | Room 数据库预创建任务 |
| 新建 | `startup/tasks/CoilInitTask.kt` | 图片加载器初始化（未来扩展） |
| 修改 | `data/AppDatabase.kt` | 支持外部注入已创建的实例 |

#### NotebookApplication 启动流程

```
NotebookApplication.attachBaseContext()
  └─ StartupOptimizer.init(this)
  └─ StartupOptimizer.execute(APP_ATTACH)   // 最早期任务（如有）

NotebookApplication.onCreate()
  └─ 注册所有启动任务:
       ├─ DatabaseInitTask        [HIGH, IO, APP_CREATE]
       └─ (未来更多任务...)
  └─ StartupOptimizer.execute(APP_CREATE)   // 触发 APP_CREATE 锚点任务
```

#### MainActivity 启动流程改造

```
MainActivity.onCreate()
  └─ StartupOptimizer.execute(ACTIVITY_CREATE)    // 触发 ACTIVITY_CREATE 任务
  └─ StartupOptimizer.awaitCriticalTasks()         // 等待首帧必需任务
  └─ setContent { ... }
  └─ 首帧回调 → StartupOptimizer.execute(POST_FIRST_FRAME)
  └─ IdleHandler → StartupOptimizer.execute(IDLE)
```

### 4.3 Phase 3: Room 数据库初始化优化

#### 当前问题

```kotlin
// NoteViewModel.init — 在主线程 Compose 首次组合时触发
val noteDao = AppDatabase.getDatabase(application).noteDao()
// ↑ 首次调用时: synchronized + Room.databaseBuilder().build()
// 耗时: ~50-200ms（取决于设备和数据库大小）
```

#### 优化方案

```
DatabaseInitTask:
  anchor = APP_CREATE
  runOn = IO
  priority = HIGH
  waitOnMainThread = true   // ViewModel 需要等它完成

执行时机: Application.onCreate → 立即在 IO 线程开始构建
ViewModel: 从 StartupOptimizer 获取已初始化的 Database 实例（无阻塞）
```

#### 改造要点

1. `AppDatabase.getDatabase()` 改为支持 `preInit()` + `getInstance()`
2. `DatabaseInitTask.execute()` 调用 `AppDatabase.preInit(context)`
3. `NoteViewModel` 从已初始化的实例获取 DAO

### 4.4 Phase 4: 监控 & 防劣化

#### 文件清单

| 操作 | 文件路径 | 说明 |
|------|---------|------|
| 新建 | `startup/StartupBaseline.kt` | 基线配置 |
| 新建 | `assets/startup_baseline.json` | 基线数据文件 |
| 新建 | `startup/StartupLogger.kt` | 格式化日志输出 |

#### 基线数据格式

```json
{
  "version": 1,
  "baseline": {
    "total_startup_ms": 280,
    "tasks": {
      "DatabaseInitTask": { "p50_ms": 80, "p90_ms": 150 },
      "CoilInitTask": { "p50_ms": 10, "p90_ms": 20 }
    }
  },
  "thresholds": {
    "total_regression_warn_pct": 15,
    "total_regression_error_pct": 30,
    "task_regression_abs_ms": 50
  }
}
```

#### 日志输出示例

```
╔══════════════════════════════════════════════════════════╗
║                 Startup Performance Report               ║
╠══════════════════════════════════════════════════════════╣
║ Total: 245ms (baseline: 280ms) ✅ -12.5%                ║
╠──────────────────────────────────────────────────────────╣
║ Phase          │ Duration │ Status                       ║
║ APP_ATTACH     │    5ms   │ ✅                           ║
║ APP_CREATE     │  120ms   │ ✅                           ║
║ ACTIVITY_CREATE│   80ms   │ ✅                           ║
║ POST_FIRST_FRAME│  40ms   │ ✅                           ║
╠──────────────────────────────────────────────────────────╣
║ Task               │ Time │ Thread │ Status              ║
║ DatabaseInitTask   │ 85ms │ IO     │ ✅ (baseline: 80ms) ║
║ CoilInitTask       │  8ms │ IO     │ ✅ (baseline: 10ms) ║
╠──────────────────────────────────────────────────────────╣
║ Parallel Efficiency: 78%                                 ║
║ Critical Path: DatabaseInitTask → [UI Ready]             ║
╚══════════════════════════════════════════════════════════╝
```

### 4.5 Phase 5: Baseline Profile（可选增强）

#### 说明

利用 Jetpack Macrobenchmark + Baseline Profile 生成启动热路径的 AOT 编译配置，进一步优化冷启动。

| 操作 | 文件路径 | 说明 |
|------|---------|------|
| 新建 | `baselineprofile/` 模块 | Macrobenchmark 模块 |
| 新建 | `BaselineProfileGenerator.kt` | 生成启动路径 Profile |
| 修改 | `app/build.gradle.kts` | 添加 baselineProfile 依赖 |

---

## 五、任务依赖图（本项目实例）

```
                    ┌─────────────────┐
                    │  APP_ATTACH     │
                    │  (无任务)        │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  APP_CREATE     │
                    │                 │
                    │ DatabaseInitTask│──── IO 线程
                    │   [HIGH, IO]    │
                    └────────┬────────┘
                             │ await
                    ┌────────▼────────┐
                    │ ACTIVITY_CREATE │
                    │                 │
                    │ (ViewModel 获取  │
                    │  已初始化的 DB)  │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ 首帧渲染        │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ POST_FIRST_FRAME│
                    │                 │
                    │ (预留: 预加载    │
                    │  首页数据等)     │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │     IDLE        │
                    │                 │
                    │ (预留: 统计SDK   │
                    │  崩溃上报等)     │
                    └─────────────────┘
```

---

## 六、预期收益

| 指标 | 优化前（估算） | 优化后（目标） | 提升 |
|------|--------------|--------------|------|
| 冷启动首帧 | ~500ms | ≤300ms | 40%+ |
| Room 初始化阻塞主线程 | ~100ms | 0ms（异步完成） | 100% |
| 启动监控覆盖 | 无 | 100% 任务 | — |
| 劣化检测 | 无 | 自动检测 | — |

---

## 七、实施顺序 & 优先级

```
Phase 1 ──────► Phase 2 ──────► Phase 3 ──────► Phase 4 ──────► Phase 5
 框架核心        Application      Room 优化       监控防劣化      Baseline Profile
 (2天)           改造 (1天)        (1天)           (1天)           (可选, 2天)
```

**建议**: Phase 1-4 为核心交付，Phase 5 为增强优化。

---

## 八、风险 & 注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 异步初始化时序问题 | ViewModel 访问未初始化的 DB | `awaitCriticalTasks()` 阻塞保证 |
| 过度异步化导致复杂度上升 | 难以调试 | 完善的日志 + Debug 模式同步执行选项 |
| Baseline 数据不准确 | 误报劣化 | 多次采样取 P50，允许 15% 波动 |
| 设备差异大 | 低端机表现不同 | 按设备等级分档基线 |
| 任务注册遗漏 | 有初始化逻辑绕过框架 | Code Review 规范 + lint 检查 |

---

## 九、与现有 .claude skills 的集成

启动优化框架实施完成后，可新增以下 skill/script:

| 类型 | 名称 | 功能 |
|------|------|------|
| Script | `startup-report.sh` | 构建并运行 app，抓取 Logcat 中的启动报告 |
| Skill | `startup-review` | 分析启动任务注册代码，检测反模式 |
| Script | `update-baseline.sh` | 运行 app 多次，取 P50 更新 baseline.json |

---

## 十、参考资料

- [Android App Startup](https://developer.android.com/topic/performance/vitals/launch-time)
- [Jetpack App Startup Library](https://developer.android.com/topic/libraries/app-startup)
- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)

