package com.example.notebook.startup

/**
 * 启动报告数据类
 */
data class StartupReport(
    /** App.attach 时间戳 */
    val startTimestamp: Long = 0L,
    /** 首帧完成时间戳 */
    val endTimestamp: Long = 0L,
    /** 各锚点到达时间 */
    val anchorTimestamps: MutableMap<LifecycleAnchor, Long> = mutableMapOf(),
    /** 每个任务的执行详情 */
    val taskDetails: MutableList<TaskDetail> = mutableListOf()
) {
    /** 总启动耗时 (ms) */
    val totalStartupTime: Long
        get() = if (endTimestamp > 0 && startTimestamp > 0) endTimestamp - startTimestamp else 0

    /** 串行总耗时 */
    val serialTotalTime: Long
        get() = taskDetails.sumOf { it.duration }

    /** 并行效率 = 串行总耗时 / 实际总耗时 */
    val parallelEfficiency: Float
        get() = if (totalStartupTime > 0) serialTotalTime.toFloat() / totalStartupTime else 0f
}

/**
 * 单个任务的执行详情
 */
data class TaskDetail(
    val name: String,
    val startTime: Long,
    val endTime: Long,
    val threadName: String,
    val success: Boolean,
    val errorMessage: String? = null
) {
    val duration: Long get() = endTime - startTime
}

/**
 * 防劣化检查结果
 */
data class RegressionResult(
    val passed: Boolean,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

/**
 * 任务注册验证结果
 */
data class ValidationResult(
    val valid: Boolean,
    val duplicateNames: List<String> = emptyList(),
    val cyclicDependencies: List<String> = emptyList(),
    val missingDependencies: List<Pair<String, String>> = emptyList() // taskName -> missingDep
)

