package com.example.notebook

import android.app.Application
import android.content.pm.ApplicationInfo
import com.example.notebook.startup.LifecycleAnchor
import com.example.notebook.startup.StartupOptimizer
import com.example.notebook.startup.tasks.DatabaseInitTask

/**
 * 自定义 Application
 *
 * 集成 StartupOptimizer 框架，管控启动生命周期。
 *
 * 启动流程:
 *   attachBaseContext() → 框架初始化、标记 APP_ATTACH
 *   onCreate()         → 注册任务、验证、触发 APP_CREATE 锚点任务
 */
class NotebookApplication : Application() {

    private val isDebug: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)

        // 最早期: 初始化启动优化框架
        StartupOptimizer.init(
            context = this,
            debug = isDebug
        )

        // 触发 APP_ATTACH 锚点任务（当前无任务）
        StartupOptimizer.execute(LifecycleAnchor.APP_ATTACH)
    }

    override fun onCreate() {
        super.onCreate()

        // 注册所有启动任务
        registerStartupTasks()

        // 验证任务注册（Debug 模式下如果有循环依赖会 crash）
        StartupOptimizer.validate()

        // 触发 APP_CREATE 锚点任务
        StartupOptimizer.execute(LifecycleAnchor.APP_CREATE)
    }

    /**
     * 注册所有启动任务
     * 后续新增初始化逻辑，统一在此注册
     */
    private fun registerStartupTasks() {
        StartupOptimizer.registerAll(
            listOf(
                // Room 数据库预初始化 — IO 线程异步，首帧前等待完成
                DatabaseInitTask(this)

                // 未来新增任务示例:
                // CoilInitTask(this),        // 图片加载器配置
                // AnalyticsInitTask(this),   // 统计 SDK（IDLE 阶段）
                // CrashReportInitTask(this), // 崩溃上报（IDLE 阶段）
            )
        )
    }
}

