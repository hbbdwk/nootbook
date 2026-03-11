package com.example.notebook.startup

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 启动耗时监控
 *
 * 追踪每个任务和每个锚点的耗时，生成启动报告。
 */
class StartupMonitor {

    companion object {
        private const val TAG = "StartupMonitor"
    }

    @Volatile
    private var startTimestamp: Long = 0L
    @Volatile
    private var endTimestamp: Long = 0L
    private val anchorTimestamps = ConcurrentHashMap<LifecycleAnchor, Long>()
    private val taskDetailsList = mutableListOf<TaskDetail>()
    private val taskStartTimes = ConcurrentHashMap<String, Long>()

    /**
     * 标记启动开始（App.attach 时调用）
     */
    fun markStartupBegin() {
        startTimestamp = SystemClock.elapsedRealtime()
        Log.d(TAG, "Startup begin at $startTimestamp")
    }

    /**
     * 标记启动结束（首帧绘制后调用）
     */
    fun markStartupEnd() {
        endTimestamp = SystemClock.elapsedRealtime()
        val total = if (startTimestamp > 0) endTimestamp - startTimestamp else 0
        Log.d(TAG, "Startup end at $endTimestamp, total: ${total}ms")
    }

    /**
     * 标记锚点到达
     */
    fun markAnchorReached(anchor: LifecycleAnchor) {
        val now = SystemClock.elapsedRealtime()
        anchorTimestamps[anchor] = now
        val elapsed = if (startTimestamp > 0) now - startTimestamp else 0
        Log.d(TAG, "Anchor ${anchor.name} reached at +${elapsed}ms")
    }

    /**
     * 标记任务开始
     */
    fun onTaskStart(taskName: String) {
        val now = SystemClock.elapsedRealtime()
        taskStartTimes[taskName] = now
    }

    /**
     * 标记任务结束
     */
    fun onTaskEnd(taskName: String, success: Boolean, errorMessage: String? = null) {
        val now = SystemClock.elapsedRealtime()
        val startTime = taskStartTimes[taskName] ?: now

        val detail = TaskDetail(
            name = taskName,
            startTime = startTime,
            endTime = now,
            threadName = Thread.currentThread().name,
            success = success,
            errorMessage = errorMessage
        )
        synchronized(taskDetailsList) {
            taskDetailsList.add(detail)
        }

        val status = if (success) "✅" else "❌ $errorMessage"
        Log.d(TAG, "Task '$taskName' completed in ${detail.duration}ms on [${detail.threadName}] $status")
    }

    /**
     * 获取启动报告
     */
    fun getReport(): StartupReport {
        val details = synchronized(taskDetailsList) { taskDetailsList.toMutableList() }
        return StartupReport(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            anchorTimestamps = anchorTimestamps.toMutableMap(),
            taskDetails = details
        )
    }

    /**
     * 获取指定任务的耗时
     */
    fun getTaskDuration(taskName: String): Long {
        synchronized(taskDetailsList) {
            return taskDetailsList.find { it.name == taskName }?.duration ?: -1
        }
    }

    /**
     * 打印格式化的启动报告
     */
    fun printReport() {
        val r = getReport()
        val sb = StringBuilder()
        sb.appendLine("╔══════════════════════════════════════════════════════════╗")
        sb.appendLine("║              Startup Performance Report                 ║")
        sb.appendLine("╠══════════════════════════════════════════════════════════╣")
        sb.appendLine("║ Total: ${r.totalStartupTime}ms                          ")
        sb.appendLine("╠──────────────────────────────────────────────────────────╣")

        // 锚点
        sb.appendLine("║ Phase            │ Timestamp  │ Delta                    ")
        var prevTime = r.startTimestamp
        LifecycleAnchor.entries.forEach { anchor ->
            val ts = r.anchorTimestamps[anchor]
            if (ts != null) {
                val delta = ts - prevTime
                val total = ts - r.startTimestamp
                sb.appendLine("║ %-16s │ +%-8dms │ Δ%-6dms".format(anchor.name, total, delta))
                prevTime = ts
            }
        }

        sb.appendLine("╠──────────────────────────────────────────────────────────╣")

        // 任务详情
        sb.appendLine("║ Task                 │ Time   │ Thread       │ Status    ")
        r.taskDetails.sortedBy { it.startTime }.forEach { detail ->
            val status = if (detail.success) "✅" else "❌"
            sb.appendLine(
                "║ %-20s │ %4dms │ %-12s │ %s".format(
                    detail.name.take(20),
                    detail.duration,
                    detail.threadName.take(12),
                    status
                )
            )
        }

        sb.appendLine("╠──────────────────────────────────────────────────────────╣")
        sb.appendLine("║ Parallel Efficiency: %.1f%%".format(r.parallelEfficiency * 100))
        sb.appendLine("╚══════════════════════════════════════════════════════════╝")

        // 按行输出到 Logcat（避免截断）
        sb.lines().forEach { line ->
            Log.i(TAG, line)
        }
    }
}

