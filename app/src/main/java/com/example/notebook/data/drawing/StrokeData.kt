package com.example.notebook.data.drawing

/**
 * 手绘笔画数据模型
 */

/** 点坐标 */
data class PointData(val x: Float, val y: Float)

/** 绘图工具类型 */
enum class DrawingTool {
    PEN,
    ERASER
}

/** 笔画粗细档位 */
enum class StrokeWidth(val value: Float, val label: String) {
    THIN(4f, "细"),
    MEDIUM(8f, "中"),
    THICK(16f, "粗")
}

/** 单条笔画数据 */
data class StrokeData(
    val points: List<PointData>,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean
)

/** 完整画布数据 */
data class DrawingData(
    val strokes: List<StrokeData>,
    val width: Int,
    val height: Int
)

/** 预设颜色（6种） */
val PRESET_COLORS: List<Int> = listOf(
    android.graphics.Color.BLACK,
    android.graphics.Color.RED,
    android.graphics.Color.BLUE,
    android.graphics.Color.parseColor("#4CAF50"),
    android.graphics.Color.parseColor("#FF9800"),
    android.graphics.Color.parseColor("#9C27B0")
)

