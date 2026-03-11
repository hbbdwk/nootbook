package com.example.notebook.startup.tasks

import android.content.Context
import com.example.notebook.data.AppDatabase
import com.example.notebook.startup.LifecycleAnchor
import com.example.notebook.startup.Priority
import com.example.notebook.startup.StartupTask
import com.example.notebook.startup.ThreadPolicy

/**
 * Room 数据库预初始化任务
 *
 * 在 Application.onCreate 阶段、IO 线程上预创建 Room 数据库实例，
 * 避免首次访问时在主线程阻塞。
 */
class DatabaseInitTask(private val context: Context) : StartupTask() {

    override val name: String = TASK_NAME

    override val priority: Priority = Priority.HIGH

    override val threadPolicy: ThreadPolicy = ThreadPolicy.IO

    override val lifecycle: LifecycleAnchor = LifecycleAnchor.APP_CREATE

    /** ViewModel 需要等待此任务完成后才能获取 DAO */
    override val waitOnMainThread: Boolean = true

    override val timeout: Long = 3000L

    override suspend fun execute() {
        // 触发 Room 数据库的完整构建（包括 schema 验证、migration 等）
        // 后续 AppDatabase.getDatabase() 调用将直接返回已创建的实例
        AppDatabase.getDatabase(context)
    }

    override fun onFailure(error: Throwable) {
        // 数据库初始化失败是致命错误，记录但不 crash
        // 后续 ViewModel 访问时会自然重试（AppDatabase 的 synchronized 逻辑）
        android.util.Log.e("DatabaseInitTask", "Database pre-init failed: ${error.message}", error)
    }

    companion object {
        const val TASK_NAME = "DatabaseInitTask"
    }
}

