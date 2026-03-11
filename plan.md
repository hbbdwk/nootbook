# 记事本多图片支持 - 实现计划

## 功能需求
记事本支持添加多个图片，并且在列表页展示预览小图

## 当前状态
- Note 实体只支持单个图片（`imagePath: String?`）
- 编辑页支持单图选择和预览
- 列表页显示单图预览

## 实现方案

### 1. 数据层修改

#### 1.1 修改 Note 实体
- 将 `imagePath: String?` 改为 `imagePaths: List<String>` 或使用 TypeConverter
- 方案选择：使用 Room TypeConverter 将 List<String> 转换为 String 存储

**文件**: `data/Note.kt`

#### 1.2 添加 TypeConverter
- 创建 `Converters` 类处理 List<String> 与 String 的转换
- 使用逗号分隔或 JSON 格式存储

**文件**: 新建 `data/Converters.kt`

#### 1.3 修改 AppDatabase
- 添加 @TypeConverters 注解

**文件**: `data/AppDatabase.kt`

### 2. Repository 层修改

#### 2.1 修改 NoteRepository
- 更新 insertNote/updateNote 方法参数

**文件**: `data/NoteRepository.kt`

### 3. ViewModel 层修改

#### 3.1 修改 NoteViewModel
- 确保新增/编辑时处理多图片

**文件**: `ui/NoteViewModel.kt`

### 4. 编辑页修改 (NoteEditScreen)

#### 4.1 支持多图片选择
- 允许多次点击添加图片按钮
- 显示多个图片预览（网格布局）
- 每个图片可单独删除

**文件**: `ui/NoteEditScreen.kt`

### 5. 列表页修改 (NoteListScreen)

#### 5.1 显示多图预览
- 显示多张缩略图（最多显示 3 张）
- 超出显示"+N"标识

**文件**: `ui/NoteListScreen.kt`

### 6. 图片预览修改

#### 6.1 支持多图浏览
- 点击列表中的图片时，可左右滑动浏览所有图片

**文件**: `ui/ImagePreviewDialog.kt`

## 数据库迁移
- 版本号从 1 升级到 2
- 使用 Migration 或直接重建（开发阶段）

## 依赖变更
- 无新增依赖，使用现有 Coil 库

## 文件清单
| 操作 | 文件路径 |
|------|---------|
| 修改 | data/Note.kt |
| 新建 | data/Converters.kt |
| 修改 | data/AppDatabase.kt |
| 修改 | data/NoteRepository.kt |
| 修改 | ui/NoteViewModel.kt |
| 修改 | ui/NoteEditScreen.kt |
| 修改 | ui/NoteListScreen.kt |
| 修改 | ui/ImagePreviewDialog.kt |

## UI 效果预览

### 编辑页
```
┌─────────────────────────┐
│  返回          保存      │
├─────────────────────────┤
│  标题输入框              │
├─────────────────────────┤
│  ┌─────┐ ┌─────┐ ┌─────┐│
│  │图片1│ │图片2│ │  +  ││
│  └─────┘ └─────┘ └─────┘│
├─────────────────────────┤
│  内容输入框              │
│  ...                    │
└─────────────────────────┘
```

### 列表页
```
┌─────────────────────────┐
│  ┌─────┐ ┌─────┐ ┌─────┐│
│  │图片 │ │     │ │ +1  ││
│  └─────┘ └─────┘ └─────┘│
│  标题                     │
│  内容摘要...              │
│  2024-01-01 12:00    🗑️  │
└─────────────────────────┘
```

---

**请审核后告知是否可以开始编写代码**
