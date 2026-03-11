package com.example.notebook.startup

import android.util.Log

/**
 * 任务依赖分析器 (Analyze)
 *
 * 构建依赖图、拓扑排序、环检测、关键路径分析。
 */
class TaskAnalyzer {

    companion object {
        private const val TAG = "TaskAnalyzer"
    }

    /**
     * 构建指定锚点任务的依赖图
     */
    fun buildGraph(tasks: List<StartupTask>): TaskGraph {
        return TaskGraph.build(tasks)
    }

    /**
     * 分析并打印任务依赖图摘要
     */
    fun analyze(tasks: List<StartupTask>): TaskGraph {
        val graph = buildGraph(tasks)

        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "Task Graph Analysis:")
        Log.d(TAG, "  Total tasks: ${tasks.size}")
        Log.d(TAG, "  Levels: ${graph.levels.size}")
        graph.levels.forEachIndexed { index, level ->
            val names = level.joinToString(", ") { "${it.name}(${it.threadPolicy})" }
            Log.d(TAG, "  Level $index: [$names]")
        }

        val criticalPath = graph.criticalPath()
        if (criticalPath.isNotEmpty()) {
            Log.d(TAG, "  Critical path: ${criticalPath.joinToString(" → ")}")
        }

        val mainThreadTasks = tasks.filter { it.threadPolicy == ThreadPolicy.MAIN_THREAD }
        val waitTasks = tasks.filter { it.waitOnMainThread }
        Log.d(TAG, "  Main thread tasks: ${mainThreadTasks.size}")
        Log.d(TAG, "  Await-on-main tasks: ${waitTasks.size}")
        Log.d(TAG, "═══════════════════════════════════════")

        return graph
    }
}

