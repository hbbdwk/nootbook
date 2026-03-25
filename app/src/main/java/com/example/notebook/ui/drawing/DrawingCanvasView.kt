package com.example.notebook.ui.drawing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.example.notebook.data.drawing.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 高性能手绘画布 View
 *
 * 双缓冲架构：commitBitmap 存放已完成笔画，onDraw 叠加当前 in-progress 笔画。
 * 单指绘画 / 双指缩放平移。
 */
class DrawingCanvasView(context: Context) : View(context) {

    companion object {
        private const val MAX_BITMAP_SIZE = 4096
        private const val TOUCH_TOLERANCE = 4f
    }

    // ============= 双缓冲 =============
    private var commitBitmap: Bitmap? = null
    private var commitCanvas: Canvas? = null

    // ============= 当前笔画 =============
    private val currentPath = Path()
    private val currentPoints = mutableListOf<PointData>()
    private var lastX = 0f
    private var lastY = 0f

    // ============= 历史记录 =============
    private val strokeHistory = mutableListOf<StrokeData>()
    private val redoHistory = mutableListOf<StrokeData>()

    // ============= 工具状态（由外部设置） =============
    var currentTool: DrawingTool = DrawingTool.PEN
    var penColor: Int = Color.BLACK
    var penStrokeWidth: Float = 8f
    var eraserWidth: Float = 30f

    // ============= 缩放 / 平移 =============
    private var scale = 1f
    private var panX = 0f
    private var panY = 0f
    private var isMultiTouch = false
    private var lastMid = PointF()
    private var initialPinchDist = 0f
    private var initialScale = 1f

    // ============= Paints =============
    private val penPaint = Paint().apply {
        isAntiAlias = true; isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val bitmapPaint = Paint(Paint.DITHER_FLAG)
    private val cursorPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE
        color = Color.GRAY; strokeWidth = 1.5f
    }
    private var cursorX = -1f
    private var cursorY = -1f

    // ============= 回调 =============
    var onUndoRedoChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    // ============= 生命周期 =============

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && commitBitmap == null) initBitmap(w, h)
    }

    private fun initBitmap(w: Int, h: Int) {
        val bw = w.coerceAtMost(MAX_BITMAP_SIZE)
        val bh = h.coerceAtMost(MAX_BITMAP_SIZE)
        commitBitmap?.recycle()
        commitBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        commitCanvas = Canvas(commitBitmap!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        commitBitmap?.recycle(); commitBitmap = null; commitCanvas = null
    }

    // ============= 绘制 =============

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        val bm = commitBitmap ?: return

        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bm, 0f, 0f, bitmapPaint)

        // 当前笔画预览（仅画笔模式）
        if (currentTool == DrawingTool.PEN && !currentPath.isEmpty) {
            penPaint.color = penColor; penPaint.strokeWidth = penStrokeWidth
            canvas.drawPath(currentPath, penPaint)
        }
        canvas.restore()

        // 橡皮擦光标（屏幕坐标）
        if (currentTool == DrawingTool.ERASER && cursorX >= 0) {
            val sx = cursorX * scale + panX
            val sy = cursorY * scale + panY
            canvas.drawCircle(sx, sy, eraserWidth * scale / 2f, cursorPaint)
        }
    }

    // ============= 触摸事件 =============

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isMultiTouch = false
                startStroke(toCanvasX(event.x), toCanvasY(event.y))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isMultiTouch = true; cancelStroke()
                if (event.pointerCount >= 2) initPinch(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isMultiTouch && event.pointerCount >= 2) handlePinch(event)
                else if (!isMultiTouch) moveStroke(toCanvasX(event.x), toCanvasY(event.y))
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isMultiTouch) finishStroke()
                isMultiTouch = false; cursorX = -1f; cursorY = -1f
                invalidate()
            }
        }
        return true
    }

    private fun toCanvasX(sx: Float) = (sx - panX) / scale
    private fun toCanvasY(sy: Float) = (sy - panY) / scale

    // ============= 笔画操作 =============

    private fun startStroke(x: Float, y: Float) {
        currentPath.reset(); currentPoints.clear()
        currentPath.moveTo(x, y); currentPoints.add(PointData(x, y))
        lastX = x; lastY = y
        if (currentTool == DrawingTool.ERASER) { cursorX = x; cursorY = y }
    }

    private fun moveStroke(x: Float, y: Float) {
        if (abs(x - lastX) < TOUCH_TOLERANCE && abs(y - lastY) < TOUCH_TOLERANCE) return
        if (currentTool == DrawingTool.PEN) {
            currentPath.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
        } else {
            val cv = commitCanvas ?: return
            eraserPaint.strokeWidth = eraserWidth
            val seg = Path().apply { moveTo(lastX, lastY); lineTo(x, y) }
            cv.drawPath(seg, eraserPaint)
            cursorX = x; cursorY = y
        }
        currentPoints.add(PointData(x, y)); lastX = x; lastY = y
    }

    private fun finishStroke() {
        if (currentPoints.size < 2) { currentPath.reset(); currentPoints.clear(); return }
        val stroke = StrokeData(
            currentPoints.toList(),
            if (currentTool == DrawingTool.PEN) penColor else 0,
            if (currentTool == DrawingTool.PEN) penStrokeWidth else eraserWidth,
            currentTool == DrawingTool.ERASER
        )
        strokeHistory.add(stroke); redoHistory.clear()
        if (currentTool == DrawingTool.PEN) {
            penPaint.color = penColor; penPaint.strokeWidth = penStrokeWidth
            commitCanvas?.drawPath(currentPath, penPaint)
        }
        currentPath.reset(); currentPoints.clear()
        notifyState(); invalidate()
    }

    private fun cancelStroke() { currentPath.reset(); currentPoints.clear() }

    // ============= 缩放 / 平移 =============

    private fun initPinch(ev: MotionEvent) {
        lastMid = midPoint(ev); initialPinchDist = pinchDist(ev); initialScale = scale
    }

    private fun handlePinch(ev: MotionEvent) {
        if (ev.pointerCount < 2) return
        val dist = pinchDist(ev)
        if (initialPinchDist > 10f) scale = (initialScale * dist / initialPinchDist).coerceIn(0.5f, 5f)
        val mid = midPoint(ev)
        panX += mid.x - lastMid.x; panY += mid.y - lastMid.y
        lastMid = mid
    }

    private fun pinchDist(ev: MotionEvent): Float {
        val dx = ev.getX(0) - ev.getX(1); val dy = ev.getY(0) - ev.getY(1)
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun midPoint(ev: MotionEvent) = PointF(
        (ev.getX(0) + ev.getX(1)) / 2f, (ev.getY(0) + ev.getY(1)) / 2f
    )

    // ============= Undo / Redo =============

    fun undo(): Boolean {
        if (strokeHistory.isEmpty()) return false
        redoHistory.add(strokeHistory.removeAt(strokeHistory.size - 1))
        replayAll(); notifyState(); return true
    }

    fun redo(): Boolean {
        if (redoHistory.isEmpty()) return false
        val s = redoHistory.removeAt(redoHistory.size - 1)
        strokeHistory.add(s); drawStroke(s); notifyState(); invalidate(); return true
    }

    fun canUndo() = strokeHistory.isNotEmpty()
    fun canRedo() = redoHistory.isNotEmpty()

    private fun replayAll() {
        commitCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        strokeHistory.forEach { drawStroke(it) }; invalidate()
    }

    private fun drawStroke(s: StrokeData) {
        val cv = commitCanvas ?: return; val path = buildPath(s.points)
        if (s.isEraser) { eraserPaint.strokeWidth = s.strokeWidth; cv.drawPath(path, eraserPaint) }
        else { penPaint.color = s.color; penPaint.strokeWidth = s.strokeWidth; cv.drawPath(path, penPaint) }
    }

    private fun buildPath(pts: List<PointData>): Path {
        val p = Path(); if (pts.isEmpty()) return p
        p.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]; val cur = pts[i]
            p.quadTo(prev.x, prev.y, (cur.x + prev.x) / 2f, (cur.y + prev.y) / 2f)
        }
        return p
    }

    private fun notifyState() = onUndoRedoChanged?.invoke(canUndo(), canRedo())

    // ============= 数据导入 / 导出 =============

    fun getDrawingData(): DrawingData {
        val bm = commitBitmap ?: return DrawingData(emptyList(), 0, 0)
        return DrawingData(strokeHistory.toList(), bm.width, bm.height)
    }

    fun loadDrawingData(data: DrawingData) {
        strokeHistory.clear(); redoHistory.clear()
        strokeHistory.addAll(data.strokes)
        if (commitBitmap == null && data.width > 0 && data.height > 0) initBitmap(data.width, data.height)
        replayAll(); notifyState()
    }

    fun exportBitmap(): Bitmap? {
        val bm = commitBitmap ?: return null
        val result = Bitmap.createBitmap(bm.width, bm.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(result); c.drawColor(Color.WHITE); c.drawBitmap(bm, 0f, 0f, null)
        return result
    }

    fun hasContent() = strokeHistory.isNotEmpty()
}

