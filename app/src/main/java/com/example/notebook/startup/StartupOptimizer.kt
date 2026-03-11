package com.example.notebook.startup

import android.content.Context
import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

/**
 * 启动优化框架 — 统一入口 (Facade)
 *
 * 生命周期:
 *   Application.attachBaseContext → init()
 *   Application.onCreate         → registerTasks() + execute(APP_CREATE)
 *   Activity.onCreate (before UI)→ execute(ACTIVITY_CREATE) + awaitCriticalTasks()
 *   首帧绘制后                    → onFirstFrameDrawn()
 *   主线程空闲                    → 自动触发 IDLE 任务
 */
object StartupOptimizer {

    private const val TAG = "StartupOptimizer"

    private lateinit var appContext: Context

    val registry = TaskRegistry()
    val monitor = StartupMonitor()
    val analyzer = TaskAnalyzer()
    lateinit var scheduler: TaskScheduler
        private set
    private var antiRegression: AntiRegression? = null

    @Volatile
    private var initialized = false
    private var debugMode = false

    /**
     * Phase 1: 最早期初始化（Application.attachBaseContext）
     */
    fun init(context: Context, debug: Boolean = false) {
        if (initialized) {
            Log.w(TAG, "Already initialized!")
            return
        }

        appContext = context.applicationContext
        debugMode = debug
        scheduler = TaskScheduler(monitor, analyzer)

        // 标记启动开始
        monitor.markStartupBegin()
        monitor.markAnchorReached(LifecycleAnchor.APP_ATTACH)

        initialized = true
        Log.d(TAG, "StartupOptimizer initialized (debug=$debug)")
    }

    /**
     * 注册启动任务
     */
    fun register(task: StartupTask) {
        checkInit()
        registry.register(task)
    }

    /**
     * 批量注册启动任务
     */
    fun registerAll(tasks: List<StartupTask>) {
        checkInit()
        registry.registerAll(tasks)
    }

    /**
     * 验证所有注册的任务
     */
    fun validate(): ValidationResult {
        val result = registry.validate()
        if (!result.valid) {
            val msg = buildString {
                appendLine("Task validation failed!")
                if (result.duplicateNames.isNotEmpty())
                    appendLine("  Duplicate names: ${result.duplicateNames}")
                if (result.cyclicDependencies.isNotEmpty())
                    appendLine("  Cyclic dependencies: ${result.cyclicDependencies}")
                if (result.missingDependencies.isNotEmpty())
                    appendLine("  Missing dependencies: ${result.missingDependencies}")
            }
            Log.e(TAG, msg)

            if (debugMode) {
                throw IllegalStateException(msg)
            }
        } else {
            Log.d(TAG, "Task validation passed. ${registry.getAllTasks().size} tasks registered.")
        }
        return result
    }

    /**
     * 触发指定锚点的所有任务
     */
    fun execute(anchor: LifecycleAnchor) {
        checkInit()
        monitor.markAnchorReached(anchor)

        val tasks = registry.getTasks(anchor)
        if (tasks.isEmpty()) {
            Log.d(TAG, "No tasks for anchor ${anchor.name}")
            return
        }

        Log.d(TAG, "Executing ${tasks.size} tasks for ${anchor.name}: ${tasks.map { it.name }}")
        scheduler.execute(anchor, tasks)
    }

    /**
     * 在主线程等待所有 waitOnMainThread=true 的关键任务完成
     * 在 setContent{} 之前调用，确保首帧渲染所需的初始化已完成
     */
    fun awaitCriticalTasks() {
        checkInit()
        val allTasks = registry.getAllTasks().filter { it.waitOnMainThread }
        if (allTasks.isEmpty()) {
            Log.d(TAG, "No critical tasks to await.")
            return
        }

        Log.d(TAG, "Awaiting ${allTasks.size} critical tasks...")
        runBlocking {
            scheduler.awaitCriticalTasks(allTasks)
        }
        Log.d(TAG, "All critical tasks completed, UI can proceed.")
    }

    /**
     * 首帧绘制完成后调用
     */
    fun onFirstFrameDrawn() {
        checkInit()
        monitor.markStartupEnd()
        execute(LifecycleAnchor.POST_FIRST_FRAME)

        // 注册 IdleHandler，在主线程空闲时触发 IDLE 任务
        Looper.myQueue().addIdleHandler(object : MessageQueue.IdleHandler {
            override fun queueIdle(): Boolean {
                execute(LifecycleAnchor.IDLE)

                // 所有任务完成后，打印报告 + 防劣化检查
                printReportAndCheck()
                return false // 只执行一次
            }
        })
    }

    /**
     * 打印启动报告 & 执行防劣化检查
     */
    private fun printReportAndCheck() {
        monitor.printReport()

        try {
            if (antiRegression == null) {
                antiRegression = AntiRegression(appContext)
            }
            val report = monitor.getReport()
            val result = antiRegression!!.check(report)

            if (!result.passed && debugMode) {
                throw RuntimeException(
                    "Startup regression detected!\n" +
                            result.errors.joinToString("\n")
                )
            }
        } catch (e: RuntimeException) {
            if (debugMode) throw e
            Log.e(TAG, "Anti-regression check error: ${e.message}")
        }
    }

    /**
     * 等待特定任务完成（供 ViewModel 等外部调用）
     */
    fun awaitTask(taskName: String): CompletableDeferred<Unit>? {
        return scheduler.getTaskCompletion(taskName)
    }

    /**
     * 获取启动报告
     */
    fun getReport(): StartupReport = monitor.getReport()

    private fun checkInit() {
        if (!initialized) {
            throw IllegalStateException("StartupOptimizer not initialized! Call init() in Application.attachBaseContext()")
        }
    }
}

