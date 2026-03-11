package com.example.notebook.startup

/**
 * 启动任务抽象基类
 *
 * 每个启动任务需继承此类，声明：
 * - 执行时机（锚点）
 * - 优先级
 * - 线程策略
 * - 依赖关系
 * - 是否需要阻塞主线程等待完成
 */
abstract class StartupTask {

    /** 任务唯一标识 */
    abstract val name: String

    /** 优先级 */
    open val priority: Priority = Priority.NORMAL

    /** 执行线程策略 */
    open val threadPolicy: ThreadPolicy = ThreadPolicy.DEFAULT

    /** 依赖的前置任务名列表 */
    open val dependsOn: List<String> = emptyList()

    /** 是否需要在主线程等待此任务完成（阻塞首帧） */
    open val waitOnMainThread: Boolean = false

    /** 生命周期锚点 — 期望执行时机 */
    open val lifecycle: LifecycleAnchor = LifecycleAnchor.APP_CREATE

    /** 超时时间(ms)，0 表示不限 */
    open val timeout: Long = 5000L

    /**
     * 任务执行逻辑（由框架在合适的线程调用）
     */
    abstract suspend fun execute()

    /**
     * 任务执行失败时的降级处理（可选覆写）
     */
    open fun onFailure(error: Throwable) {
        // 默认不做额外处理，框架会记录日志
    }

    override fun toString(): String {
        return "StartupTask(name='$name', priority=$priority, thread=$threadPolicy, " +
                "anchor=$lifecycle, dependsOn=$dependsOn, waitOnMain=$waitOnMainThread)"
    }
}

