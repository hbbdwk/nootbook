package com.example.notebook.startup

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 防劣化检测
 *
 * 对比启动报告与基线数据，检测性能回归。
 * - 总耗时回归 > 15%: Warning
 * - 总耗时回归 > 30%: Error (Debug 模式 crash)
 * - 单任务回归 > 50ms: Warning
 */
class AntiRegression(context: Context) {

    companion object {
        private const val TAG = "AntiRegression"
        private const val BASELINE_FILE = "startup_baseline.json"
        private const val DEFAULT_WARN_PCT = 15
        private const val DEFAULT_ERROR_PCT = 30
        private const val DEFAULT_TASK_ABS_MS = 50L
    }

    private var baseline: BaselineData? = null

    init {
        loadBaseline(context.applicationContext)
    }

    /**
     * 从 assets 加载基线数据
     */
    private fun loadBaseline(context: Context) {
        try {
            val json = context.assets.open(BASELINE_FILE).bufferedReader().readText()
            val root = JSONObject(json)
            val baselineObj = root.getJSONObject("baseline")
            val thresholds = root.getJSONObject("thresholds")
            val tasksObj = baselineObj.optJSONObject("tasks")

            val taskBaselines = mutableMapOf<String, TaskBaseline>()
            tasksObj?.keys()?.forEach { key ->
                val taskObj = tasksObj.getJSONObject(key)
                taskBaselines[key] = TaskBaseline(
                    p50Ms = taskObj.optLong("p50_ms", 0),
                    p90Ms = taskObj.optLong("p90_ms", 0)
                )
            }

            baseline = BaselineData(
                totalStartupMs = baselineObj.optLong("total_startup_ms", 0),
                tasks = taskBaselines,
                warnPct = thresholds.optInt("total_regression_warn_pct", DEFAULT_WARN_PCT),
                errorPct = thresholds.optInt("total_regression_error_pct", DEFAULT_ERROR_PCT),
                taskAbsMs = thresholds.optLong("task_regression_abs_ms", DEFAULT_TASK_ABS_MS)
            )

            Log.d(TAG, "Baseline loaded: totalStartupMs=${baseline?.totalStartupMs}")
        } catch (e: Exception) {
            Log.w(TAG, "No baseline file found or parse error: ${e.message}. Anti-regression disabled.")
            baseline = null
        }
    }

    /**
     * 执行防劣化检查
     */
    fun check(report: StartupReport): RegressionResult {
        val b = baseline ?: return RegressionResult(
            passed = true,
            warnings = listOf("No baseline data, skipping regression check.")
        )

        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 1. 总启动耗时检查
        if (b.totalStartupMs > 0 && report.totalStartupTime > 0) {
            val regressionPct = ((report.totalStartupTime - b.totalStartupMs) * 100.0 / b.totalStartupMs)

            if (regressionPct > b.errorPct) {
                errors.add(
                    "🔴 Total startup time regressed by %.1f%% (%dms → %dms, threshold: %d%%)".format(
                        regressionPct, b.totalStartupMs, report.totalStartupTime, b.errorPct
                    )
                )
            } else if (regressionPct > b.warnPct) {
                warnings.add(
                    "⚠️ Total startup time regressed by %.1f%% (%dms → %dms, threshold: %d%%)".format(
                        regressionPct, b.totalStartupMs, report.totalStartupTime, b.warnPct
                    )
                )
            } else {
                Log.i(TAG, "✅ Total startup time: %dms (baseline: %dms, %.1f%%)".format(
                    report.totalStartupTime, b.totalStartupMs, regressionPct
                ))
            }
        }

        // 2. 单任务耗时检查
        report.taskDetails.forEach { detail ->
            val taskBaseline = b.tasks[detail.name]
            if (taskBaseline != null && taskBaseline.p50Ms > 0) {
                val delta = detail.duration - taskBaseline.p50Ms
                if (delta > b.taskAbsMs) {
                    warnings.add(
                        "⚠️ Task '${detail.name}' regressed by ${delta}ms (%dms → %dms)".format(
                            taskBaseline.p50Ms, detail.duration
                        )
                    )
                }
            }
        }

        // 3. 检测新增的主线程任务（未在基线中的）
        report.taskDetails.forEach { detail ->
            if (detail.name !in b.tasks && detail.threadName.contains("main", ignoreCase = true)) {
                warnings.add("📝 New main-thread task detected: '${detail.name}' (${detail.duration}ms)")
            }
        }

        val passed = errors.isEmpty()

        // 打印结果
        warnings.forEach { Log.w(TAG, it) }
        errors.forEach { Log.e(TAG, it) }

        if (passed) {
            Log.i(TAG, "✅ Anti-regression check passed.")
        } else {
            Log.e(TAG, "❌ Anti-regression check FAILED!")
        }

        return RegressionResult(passed = passed, warnings = warnings, errors = errors)
    }

    /**
     * 基线数据
     */
    private data class BaselineData(
        val totalStartupMs: Long,
        val tasks: Map<String, TaskBaseline>,
        val warnPct: Int,
        val errorPct: Int,
        val taskAbsMs: Long
    )

    private data class TaskBaseline(
        val p50Ms: Long,
        val p90Ms: Long
    )
}

