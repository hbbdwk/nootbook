# 手写板技术设计文档

## 1. 背景

Notebook 当前已支持文本、图片和本地摘要能力，但缺少直接承载草图、涂鸦、手写批注的输入方式。为补齐笔记场景，本次新增手写板能力，目标如下：

- 支持在笔记编辑流程中新增或修改手绘内容
- 支持画笔、橡皮擦、Undo / Redo
- 支持双指缩放与平移，兼顾局部精细绘制
- 支持手绘结果持久化保存，并在列表页/编辑页展示缩略图
- 在移动端设备上尽量保证手写过程流畅，降低卡顿与 OOM 风险

当前实现采用 **Compose 页面 + `AndroidView` + Custom View** 的组合方式：

- 页面导航、工具栏、外层布局继续使用 Compose
- 高频绘制区域交给 `DrawingCanvasView`
- 笔迹以 JSON 形式落盘，缩略图以 PNG 保存
- 笔记实体 `Note` 中只记录手绘文件路径，不直接存储大块绘图内容

该方案的核心原则是：**UI 用 Compose，重绘密集区用原生 View；文件系统承载大对象，Room 只保存路径索引。**

---

## 2. 架构

### 2.1 分层结构

本次手写板实现可分为四层：

#### 1）页面接入层
相关文件：

- `app/src/main/java/com/example/notebook/MainActivity.kt`
- `app/src/main/java/com/example/notebook/ui/NoteEditScreen.kt`
- `app/src/main/java/com/example/notebook/ui/NoteListScreen.kt`
- `app/src/main/java/com/example/notebook/ui/drawing/DrawingScreen.kt`

职责：

- 在笔记编辑页进入手写板
- 从手写板返回时回填 `drawingPath` / `drawingThumbnailPath`
- 在编辑页展示当前手绘缩略图
- 在列表页展示手绘预览

#### 2）绘制交互层
相关文件：

- `app/src/main/java/com/example/notebook/ui/drawing/DrawingCanvasView.kt`
- `app/src/main/java/com/example/notebook/ui/drawing/DrawingToolbar.kt`

职责：

- 接收触摸输入
- 区分单指绘制与双指缩放/平移
- 管理画笔与橡皮擦状态
- 维护 Undo / Redo 栈
- 完成高频绘制与离屏缓存

#### 3）数据模型层
相关文件：

- `app/src/main/java/com/example/notebook/data/drawing/StrokeData.kt`

核心模型：

- `PointData(x, y)`：单个采样点
- `StrokeData(points, color, strokeWidth, isEraser)`：单条笔画
- `DrawingData(strokes, width, height)`：完整画布数据
- `DrawingTool`：画笔 / 橡皮擦
- `StrokeWidth`：细 / 中 / 粗 三档宽度
- `PRESET_COLORS`：6 种预设颜色

#### 4）持久化层
相关文件：

- `app/src/main/java/com/example/notebook/data/drawing/DrawingSerializer.kt`
- `app/src/main/java/com/example/notebook/data/Note.kt`
- `app/src/main/java/com/example/notebook/data/AppDatabase.kt`

职责：

- 将 `DrawingData` 序列化为 JSON 文件
- 将导出的手绘结果保存为 PNG 缩略图
- 通过 `Note.drawingPath`、`Note.drawingThumbnailPath` 建立笔记与手绘文件的关联
- 通过 Room migration 增加手绘字段，保证已有数据可升级

### 2.2 页面关系

整体页面流转如下：

1. 用户进入 `NoteEditScreen`
2. 点击“添加手绘 / 编辑手绘”进入 `DrawingScreen`
3. `DrawingScreen` 内通过 `AndroidView` 挂载 `DrawingCanvasView`
4. 用户保存后，将 JSON 文件路径和缩略图路径回传给编辑页
5. 编辑页最终保存 `Note`
6. 列表页通过缩略图路径显示手绘预览

### 2.3 为什么使用 `AndroidView + Custom View`

手写输入属于高频交互场景，移动过程中会持续触发大量 `MOVE` 事件。若完全采用 Compose 状态驱动整个画布重组：

- 重组频率高
- 绘制路径管理复杂
- 对 UI 线程压力更大

因此当前方案选择：

- Compose 负责外层页面和工具栏
- `DrawingCanvasView` 负责底层绘制与触摸

这样可以直接使用：

- `invalidate()` 控制刷新
- 原生 `Canvas / Paint / Path`
- 更直接的触摸坐标与像素级擦除能力

这是在移动端手写场景下更稳妥的架构选择。

---

## 3. 时序

### 3.1 进入画板时序

1. 用户在 `NoteEditScreen` 点击手绘按钮
2. `MainActivity` 将页面状态切换到 `DrawingScreen`
3. `DrawingScreen` 创建 `DrawingCanvasView`
4. 如果存在 `existingDrawingPath`，则调用 `DrawingSerializer.loadFromFile(path)`
5. 加载出的 `DrawingData` 通过 `DrawingCanvasView.loadDrawingData(data)` 重建画布内容

### 3.2 手绘交互时序

#### 单指绘制

1. `ACTION_DOWN`
   - 调用 `startStroke(x, y)`
   - 初始化 `currentPath`
   - 记录首个点到 `currentPoints`

2. `ACTION_MOVE`
   - 调用 `moveStroke(x, y)`
   - 若为画笔模式，使用 `quadTo` 平滑追加路径
   - 若为橡皮擦模式，直接对 `commitCanvas` 进行清除绘制
   - 调用 `invalidate()` 请求重绘

3. `ACTION_UP`
   - 调用 `finishStroke()`
   - 生成 `StrokeData`
   - 写入 `strokeHistory`
   - 清空 `redoHistory`
   - 若是画笔模式，则把 `currentPath` 提交到 `commitBitmap`

#### 双指缩放/平移

1. `ACTION_POINTER_DOWN`
   - 标记 `isMultiTouch = true`
   - 取消当前未完成笔画
   - 初始化 pinch 初始距离和中心点

2. `ACTION_MOVE`
   - 计算双指间距变化
   - 更新 `scale`
   - 根据中心点位移更新 `panX / panY`
   - 调用 `invalidate()` 重绘

3. 绘制阶段通过 `toCanvasX / toCanvasY` 将屏幕坐标逆变换回画布坐标

### 3.3 保存时序

1. 用户点击 `DrawingScreen` 顶部保存按钮
2. 调用 `DrawingCanvasView.getDrawingData()` 获取完整笔迹数据
3. 生成时间戳作为文件 id
4. 调用 `DrawingSerializer.saveToFile(context, id, data)` 保存 JSON
5. 调用 `DrawingCanvasView.exportBitmap()` 导出结果位图
6. 调用 `DrawingSerializer.saveThumbnail(context, id, bitmap)` 保存 PNG 缩略图
7. 回传 `drawingPath` 和 `thumbnailPath`
8. 编辑页保存 `Note`，将两个路径写入数据库

### 3.4 列表展示时序

1. 列表页读取 `Note.drawingThumbnailPath`
2. 若文件存在，则直接使用缩略图渲染
3. 不重新反序列化 JSON、不重放全部笔画

这样避免列表滚动时的高成本重建。

---

## 4. 性能

### 4.1 核心性能目标

手写板性能关注点主要有三类：

1. **写字时不卡顿**：MOVE 高频阶段尽量轻量
2. **内存可控**：避免超大位图导致 OOM
3. **预览高效**：编辑页/列表页不做重型重建

### 4.2 当前性能优化点

#### 1）双缓冲绘制

`DrawingCanvasView` 采用双缓冲思路：

- `commitBitmap + commitCanvas`：保存所有已完成的历史笔画
- `currentPath`：保存当前正在绘制的一笔

`onDraw()` 流程为：

1. 先绘制白底
2. 再绘制 `commitBitmap`
3. 最后叠加 `currentPath`

这意味着每一帧不需要把全部历史笔画重新绘制一遍，只需要复用已经提交的结果并叠加当前笔迹。

#### 2）为什么采用双缓冲

如果不使用双缓冲，而是在每一帧都：

- 遍历 `strokeHistory`
- 重建所有 Path
- 重绘所有笔画

那么随着笔画增多：

- 单帧绘制成本会持续上升
- 高频 `MOVE` 事件会造成更大 UI 线程压力
- 手写跟手性会明显下降
- 更容易出现掉帧和卡顿

而手写场景有一个典型特点：

- 大部分历史内容是稳定不变的
- 只有“当前这一笔”在持续变化

因此最合理的方式就是：

- 历史内容一次提交，缓存到离屏 Bitmap
- 当前内容作为前景临时层实时叠加

双缓冲本质上是在用**额外的一张位图内存，换取更低的每帧计算成本**。这对于移动端手写场景是非常典型且有效的优化策略。

#### 3）Custom View 承担高频绘制

当前没有使用纯 Compose Canvas 做整套高频绘制，而是将重负载逻辑放在 `DrawingCanvasView` 中，原因是：

- `View.invalidate()` 更适合高频增量刷新
- 原生 `Canvas / Paint / Path` 对像素级操作更直接
- 避免大量 Compose 重组参与高频手势过程

#### 4）触摸容差过滤

使用 `TOUCH_TOLERANCE = 4f` 过滤微小移动：

- 降低无意义抖动
- 减少采样点数量
- 减少路径复杂度
- 降低 JSON 文件大小
- 减少绘制与回放成本

#### 5）路径平滑

画笔在移动过程中采用 `quadTo` 连接点，而不是简单 `lineTo`：

- 曲线更平滑
- 视觉质量更好
- 同样数量采样点下更自然

#### 6）橡皮擦直接像素擦除

橡皮擦使用 `PorterDuff.Mode.CLEAR`：

- 直接对 `commitCanvas` 清除像素
- 不需要实时查找“命中的历史笔画”
- 擦除过程更贴近真实橡皮擦体验

#### 7）位图尺寸上限控制

`DrawingCanvasView` 中使用：

- `MAX_BITMAP_SIZE = 4096`

并在初始化时：

- `w.coerceAtMost(MAX_BITMAP_SIZE)`
- `h.coerceAtMost(MAX_BITMAP_SIZE)`

这样可以避免极端大尺寸 View 导致超大 `ARGB_8888` 位图分配，降低 OOM 风险。

#### 8）位图生命周期回收

在 `onDetachedFromWindow()` 中主动：

- `recycle()` 已创建的 `commitBitmap`
- 清空 `commitCanvas`

避免页面退出后仍长期占用大对象内存。

#### 9）缩略图预览替代实时回放

列表页与编辑页优先展示 PNG 缩略图：

- 不需要每次读取 JSON 再重建画布
- 降低 CPU 与内存开销
- 提升列表滑动和页面打开速度

### 4.3 Undo / Redo 的当前策略

当前 Undo / Redo 使用两个栈：

- `strokeHistory`
- `redoHistory`

行为如下：

- Undo：移除最后一笔，调用 `replayAll()` 重放剩余内容
- Redo：恢复一笔，并直接绘制回 `commitBitmap`

这是一种实现简单、行为稳定的方案。其优点是逻辑清晰，缺点是当笔画非常多时，Undo 的重放成本会逐渐上升。

---

## 5. 风险

### 5.1 Undo 成本随笔画数增长

由于历史内容已经被提交进 `commitBitmap`，Undo 无法简单“从位图中减去最后一笔”，因此当前方案需要：

- 清空 `commitBitmap`
- 重新重放剩余 `strokeHistory`

当笔画很多时，Undo 的耗时会变高。这是双缓冲方案下的典型取舍。

### 5.2 JSON 文件体积可能增长

当前以 JSON 保存全部点坐标，优点是可读、可调试，但在大量笔迹场景下：

- 文件体积可能增长较快
- 序列化/反序列化成本上升

### 5.3 画布为单层结构

当前所有内容最终都落在同一个离屏位图中，没有图层系统。这样实现简单、性能更稳，但也意味着：

- 不支持对象级编辑
- 不支持图层隔离
- 后续若要做选区/局部移动，改造成本较高

### 5.4 缩放与绘制状态仍在同一 View 内部管理

目前单指绘制、双指缩放、平移逻辑都集中在 `DrawingCanvasView` 中，优点是集中高效，风险是：

- 手势状态机复杂度会逐步增加
- 后续增加更多交互（例如框选、吸附、尺子）时维护成本变高

### 5.5 橡皮擦依赖像素清除顺序

橡皮擦是基于 `PorterDuff.Mode.CLEAR` 对像素结果生效，因此历史回放时必须保持笔画顺序一致。若未来引入异步分层渲染，需要特别注意顺序一致性问题。

---

## 6. 后续优化

### 6.1 引入 Undo 快照/checkpoint

可考虑每 N 笔生成一次 checkpoint bitmap：

- Undo 时从最近快照开始重放
- 避免每次都从空白画布全量回放

这会明显降低笔画较多时的撤销开销。

### 6.2 点采样压缩

可对笔迹点做进一步稀疏化处理，例如：

- 更智能的采样阈值
- 曲线简化算法
- 根据速度动态调整采样密度

以减少：

- JSON 体积
- 回放成本
- 内存占用

### 6.3 局部刷新 / 脏区刷新

当前主要依赖整 View `invalidate()`。后续可根据路径边界计算 dirty rect，做局部刷新，降低大画布下的重绘成本。

### 6.4 更强的边界约束

缩放和平移当前已经可用，但还可以增加：

- 平移边界限制
- 回弹效果
- 缩放中心校正
- 更自然的惯性手感

### 6.5 分块位图 / 大画布方案

若后续支持超大画布，可将单张位图升级为：

- Tile / 分块位图
- 分区域加载与绘制

进一步控制峰值内存占用。

### 6.6 后台保存与异步落盘

当前保存流程在点击保存时同步完成 JSON 和 PNG 写入。未来可考虑：

- IO 线程异步保存
- 保存进度提示
- 失败重试机制

提升极端场景下的保存体验。

### 6.7 二进制格式演进

若未来 JSON 文件过大，可考虑将 `DrawingData` 升级为：

- 二进制自定义格式
- Proto / FlatBuffers 类方案

以换取更小体积和更快解析速度。

---

## 总结

本次手写板方案的核心思路是：

- 用 Compose 承担页面结构与导航
- 用 `AndroidView + DrawingCanvasView` 承担高频绘制
- 用 JSON 保存笔迹、PNG 保存缩略图
- 用 Room 仅保存文件路径
- 用双缓冲降低每帧实时绘制成本

其中，**双缓冲是整个性能设计的关键**。它把“每次手指移动都重绘全部历史笔画”的高成本模式，转化为“复用已提交位图 + 仅绘制当前笔画”的增量模式，更适合移动端的手写场景，也为后续继续演进 Undo、压缩、分块渲染等能力留下了空间。

