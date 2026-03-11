package com.example.notebook.startup

import android.util.Log

/**
 * 任务注册中心 (Retrieve)
 *
 * 收集所有启动任务的注册信息，按锚点分组管理，
 * 提供验证能力（检测重名、循环依赖、缺失依赖）。
 */
class TaskRegistry {

    private val tasks = mutableListOf<StartupTask>()
    private val taskMap = mutableMapOf<String, StartupTask>()

    companion object {
        private const val TAG = "TaskRegistry"
    }

    /**
     * 注册单个启动任务
     */
    fun register(task: StartupTask) {
        if (taskMap.containsKey(task.name)) {
            Log.w(TAG, "Task '${task.name}' already registered, replacing.")
        }
        tasks.add(task)
        taskMap[task.name] = task
    }

    /**
     * 批量注册启动任务
     */
    fun registerAll(taskList: List<StartupTask>) {
        taskList.forEach { register(it) }
    }

    /**
     * 获取指定锚点的所有任务
     */
    fun getTasks(anchor: LifecycleAnchor): List<StartupTask> {
        return tasks.filter { it.lifecycle == anchor }
    }

    /**
     * 获取所有已注册任务
     */
    fun getAllTasks(): List<StartupTask> = tasks.toList()

    /**
     * 按名称获取任务
     */
    fun getTask(name: String): StartupTask? = taskMap[name]

    /**
     * 验证任务注册的合法性
     */
    fun validate(): ValidationResult {
        val duplicateNames = mutableListOf<String>()
        val missingDependencies = mutableListOf<Pair<String, String>>()

        // 1. 检测重名
        val nameCount = tasks.groupingBy { it.name }.eachCount()
        nameCount.filter { it.value > 1 }.keys.forEach { duplicateNames.add(it) }

        // 2. 检测缺失依赖
        val allNames = taskMap.keys
        tasks.forEach { task ->
            task.dependsOn.forEach { dep ->
                if (dep !in allNames) {
                    missingDependencies.add(task.name to dep)
                }
            }
        }

        // 3. 检测循环依赖
        val cyclicDeps = detectCycles()

        val valid = duplicateNames.isEmpty() && cyclicDeps.isEmpty() && missingDependencies.isEmpty()

        return ValidationResult(
            valid = valid,
            duplicateNames = duplicateNames,
            cyclicDependencies = cyclicDeps,
            missingDependencies = missingDependencies
        )
    }

    /**
     * 使用 DFS 检测循环依赖
     */
    private fun detectCycles(): List<String> {
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()
        val cycles = mutableListOf<String>()

        fun dfs(name: String): Boolean {
            if (name in inStack) {
                cycles.add(name)
                return true
            }
            if (name in visited) return false

            visited.add(name)
            inStack.add(name)

            val task = taskMap[name]
            task?.dependsOn?.forEach { dep ->
                if (dfs(dep)) {
                    cycles.add(name)
                    return true
                }
            }

            inStack.remove(name)
            return false
        }

        taskMap.keys.forEach { name ->
            if (name !in visited) {
                dfs(name)
            }
        }

        return cycles
    }

    /**
     * 清除所有注册的任务
     */
    fun clear() {
        tasks.clear()
        taskMap.clear()
    }
}

