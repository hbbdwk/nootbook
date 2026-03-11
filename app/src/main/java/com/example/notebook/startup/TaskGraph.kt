package com.example.notebook.startup

/**
 * 任务依赖图 (DAG)
 *
 * 将注册的任务构建为有向无环图，支持拓扑排序和关键路径分析。
 */
class TaskGraph private constructor(
    /** 按拓扑排序后的层级列表，同层可并行 */
    val levels: List<List<StartupTask>>,
    /** 所有任务的邻接关系 */
    val adjacency: Map<String, List<String>>,
    /** 所有任务映射 */
    val taskMap: Map<String, StartupTask>
) {
    companion object {
        /**
         * 从任务列表构建依赖图
         */
        fun build(tasks: List<StartupTask>): TaskGraph {
            val map = tasks.associateBy { it.name }
            val adjacency = tasks.associate { it.name to it.dependsOn.toList() }

            // Kahn 拓扑排序 — 按层级分组
            val inDegree = mutableMapOf<String, Int>()
            tasks.forEach { inDegree[it.name] = 0 }
            tasks.forEach { task ->
                task.dependsOn.forEach { dep ->
                    // dep 指向 task (task 依赖 dep)
                    // 但 inDegree 计算的是 task 的入度
                }
            }
            // 计算入度: 任务依赖的数量即为入度
            tasks.forEach { task ->
                inDegree[task.name] = task.dependsOn.count { it in map }
            }

            val levels = mutableListOf<List<StartupTask>>()
            val resolved = mutableSetOf<String>()

            // 第一层: 入度为 0 的任务（无依赖）
            var currentLevel = tasks.filter { (inDegree[it.name] ?: 0) == 0 }
                .sortedWith(compareBy<StartupTask> { it.priority.ordinal }
                    .thenByDescending { it.waitOnMainThread })

            while (currentLevel.isNotEmpty()) {
                levels.add(currentLevel)
                resolved.addAll(currentLevel.map { it.name })

                // 找出下一层: 所有依赖都已 resolved 的任务
                val nextLevel = tasks
                    .filter { it.name !in resolved }
                    .filter { task -> task.dependsOn.all { it in resolved || it !in map } }
                    .sortedWith(compareBy<StartupTask> { it.priority.ordinal }
                        .thenByDescending { it.waitOnMainThread })

                currentLevel = nextLevel
            }

            return TaskGraph(levels, adjacency, map)
        }
    }

    /**
     * 获取拓扑排序后的扁平任务列表
     */
    fun flattenedTasks(): List<StartupTask> = levels.flatten()

    /**
     * 获取关键路径信息（简化版: 找最长依赖链）
     */
    fun criticalPath(): List<String> {
        val memo = mutableMapOf<String, List<String>>()

        fun longestPath(name: String): List<String> {
            memo[name]?.let { return it }

            val task = taskMap[name] ?: return listOf(name)
            val deps = task.dependsOn.filter { it in taskMap }

            val path = if (deps.isEmpty()) {
                listOf(name)
            } else {
                val longestDep = deps.maxByOrNull { longestPath(it).size }!!
                longestPath(longestDep) + name
            }

            memo[name] = path
            return path
        }

        // 找所有叶子节点中最长的路径
        return taskMap.keys.map { longestPath(it) }.maxByOrNull { it.size } ?: emptyList()
    }
}

