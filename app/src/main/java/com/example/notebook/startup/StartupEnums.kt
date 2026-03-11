package com.example.notebook.startup

/**
 * 启动任务优先级
 */
enum class Priority {
    /** 首帧前必须完成 */
    CRITICAL,
    /** 首屏数据依赖 */
    HIGH,
    /** 用户交互前完成 */
    NORMAL,
    /** 可延迟到空闲时 */
    LOW
}

/**
 * 任务执行线程策略
 */
enum class ThreadPolicy {
    /** 必须在主线程执行 */
    MAIN_THREAD,
    /** 在 IO 线程池执行 */
    IO,
    /** 在默认线程池执行 */
    DEFAULT
}

/**
 * 生命周期锚点 — 决定任务在启动流程中的触发时机
 */
enum class LifecycleAnchor(val order: Int) {
    /** Application.attachBaseContext — 最早时机 */
    APP_ATTACH(0),
    /** Application.onCreate — 常规初始化 */
    APP_CREATE(1),
    /** MainActivity.onCreate 之前 — UI 相关初始化 */
    ACTIVITY_CREATE(2),
    /** 首帧绘制完成后 — 非首屏必需的初始化 */
    POST_FIRST_FRAME(3),
    /** 主线程空闲时 — 完全可延迟的任务 */
    IDLE(4)
}

