package com.example.notebook.startup

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * 任务调度执行器 (Generate)
 *
 * 根据依赖图，使用协程并行/串行调度启动任务。
 * - 主线程任务在主线程顺序执行
 * - IO 任务在 Dispatchers.IO 执行
 * - 同层无依赖任务并行执行
 * - 使用 CompletableDeferred 实现任务间等待
 */
class TaskScheduler(
    private val monitor: StartupMonitor,
    private val analyzer: TaskAnalyzer
) {
    companion object {
        private const val TAG = "TaskScheduler"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 每个任务对应的 CompletableDeferred，用于依赖等待 */
    private val taskCompletions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    /** 已执行的锚点 */
    private val executedAnchors = mutableSetOf<LifecycleAnchor>()

    /**
     * 执行指定锚点的所有任务
     */
    fun execute(anchor: LifecycleAnchor, tasks: List<StartupTask>) {
        if (tasks.isEmpty()) {
            Log.d(TAG, "No tasks for anchor ${anchor.name}")
            return
        }

        synchronized(executedAnchors) {
            if (anchor in executedAnchors) {
                Log.w(TAG, "Anchor ${anchor.name} already executed, skipping.")
                return
            }
            executedAnchors.add(anchor)
        }

        Log.d(TAG, "Executing ${tasks.size} tasks for anchor ${anchor.name}")

        // 为所有任务创建 CompletableDeferred
        tasks.forEach { task ->
            taskCompletions.getOrPut(task.name) { CompletableDeferred() }
        }

        // 构建依赖图并分层执行
        val graph = analyzer.analyze(tasks)

        graph.levels.forEach { level ->
            // 同层任务并行发射
            val jobs = level.map { task ->
                scope.async {
                    executeTask(task)
                }
            }

            // 等待当前层全部完成后再执行下一层
            // 使用 launch 不阻塞调用线程
            scope.launch {
                jobs.forEach { it.await() }
            }
        }
    }

    /**
     * 执行单个任务
     */
    private suspend fun executeTask(task: StartupTask) {
        // 1. 等待所有依赖完成
        task.dependsOn.forEach { depName ->
            val dep = taskCompletions[depName]
            if (dep != null) {
                Log.d(TAG, "Task '${task.name}' waiting for dependency '$depName'")
                dep.await()
            }
        }

        // 2. 在指定线程执行
        val dispatcher = when (task.threadPolicy) {
            ThreadPolicy.MAIN_THREAD -> Dispatchers.Main
            ThreadPolicy.IO -> Dispatchers.IO
            ThreadPolicy.DEFAULT -> Dispatchers.Default
        }

        monitor.onTaskStart(task.name)

        try {
            val result = if (task.timeout > 0) {
                withTimeoutOrNull(task.timeout) {
                    withContext(dispatcher) {
                        task.execute()
                    }
                }
            } else {
                withContext(dispatcher) {
                    task.execute()
                }
            }

            if (result == null) {
                // 超时
                val msg = "Task '${task.name}' timed out after ${task.timeout}ms"
                Log.w(TAG, msg)
                monitor.onTaskEnd(task.name, success = false, errorMessage = msg)
                task.onFailure(RuntimeException(msg))
            } else {
                monitor.onTaskEnd(task.name, success = true)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Task '${task.name}' failed: ${e.message}", e)
            monitor.onTaskEnd(task.name, success = false, errorMessage = e.message)
            task.onFailure(e)
        } finally {
            // 无论成功失败，标记任务完成（避免依赖方永久等待）
            taskCompletions[task.name]?.complete(Unit)
        }
    }

    /**
     * 在主线程阻塞等待所有 waitOnMainThread=true 的任务完成
     * 在 Activity.onCreate -> setContent 之前调用
     */
    suspend fun awaitCriticalTasks(tasks: List<StartupTask>) {
        val criticalTasks = tasks.filter { it.waitOnMainThread }
        if (criticalTasks.isEmpty()) return

        Log.d(TAG, "Awaiting ${criticalTasks.size} critical tasks on main thread...")

        criticalTasks.forEach { task ->
            val deferred = taskCompletions[task.name]
            if (deferred != null && !deferred.isCompleted) {
                Log.d(TAG, "Main thread waiting for '${task.name}'...")
                deferred.await()
                Log.d(TAG, "Task '${task.name}' completed, main thread unblocked.")
            }
        }

        Log.d(TAG, "All critical tasks completed.")
    }

    /**
     * 获取任务的 CompletableDeferred（用于外部等待特定任务）
     */
    fun getTaskCompletion(taskName: String): CompletableDeferred<Unit>? {
        return taskCompletions[taskName]
    }
}

