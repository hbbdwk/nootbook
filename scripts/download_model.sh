#!/bin/bash
# ============================================================
# download_model.sh
# 下载 Gemma 3 1B IT int4 MediaPipe task 模型并推送到设备
# ============================================================

set -e

MODEL_NAME="gemma3-1b-it-int4.task"
MODEL_URL="https://huggingface.co/K4N4T/gemma3-1B-it-int4.task/resolve/main/gemma3-1B-it-int4.task"
DOWNLOAD_DIR="./models"
DEVICE_APP_DIR="/data/local/tmp"
PACKAGE_NAME="com.example.notebook"

echo "=== MediaPipe Gemma 3 1B 模型下载工具 ==="
echo ""
echo "模型: Gemma 3 1B IT int4 (MediaPipe .task 格式)"
echo "大小: ~550MB"
echo "用途: 本地 LLM 笔记摘要生成"
echo ""

# 检查 curl
if ! command -v curl &> /dev/null; then
    echo "❌ curl 未找到，请先安装 curl"
    exit 1
fi

# 1. 下载模型
mkdir -p "${DOWNLOAD_DIR}"
if [ -f "${DOWNLOAD_DIR}/${MODEL_NAME}" ]; then
    echo "✅ 模型文件已存在: ${DOWNLOAD_DIR}/${MODEL_NAME}"
    echo "   大小: $(du -h "${DOWNLOAD_DIR}/${MODEL_NAME}" | cut -f1)"
else
    echo "📥 正在下载模型 (约 550MB)..."
    echo "   URL: ${MODEL_URL}"
    curl -L --progress-bar -o "${DOWNLOAD_DIR}/${MODEL_NAME}.tmp" "${MODEL_URL}"
    mv "${DOWNLOAD_DIR}/${MODEL_NAME}.tmp" "${DOWNLOAD_DIR}/${MODEL_NAME}"
    echo "✅ 下载完成"
    echo "   大小: $(du -h "${DOWNLOAD_DIR}/${MODEL_NAME}" | cut -f1)"
fi

# 检查文件完整性（至少 500MB）
FILE_SIZE=$(wc -c < "${DOWNLOAD_DIR}/${MODEL_NAME}" | tr -d ' ')
if [ "$FILE_SIZE" -lt 500000000 ]; then
    echo "❌ 模型文件过小 (${FILE_SIZE} bytes)，可能下载不完整"
    echo "   请删除 ${DOWNLOAD_DIR}/${MODEL_NAME} 后重试"
    exit 1
fi

# 检查 adb
if ! command -v adb &> /dev/null; then
    echo "⚠️  adb 未找到，模型已下载到 ${DOWNLOAD_DIR}/${MODEL_NAME}"
    echo "   请手动推送到设备"
    exit 0
fi

# 2. 检查设备连接
echo ""
echo "📱 检查设备连接..."
DEVICE_COUNT=$(adb devices | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ 未检测到已连接的设备，请连接设备并启用 USB 调试"
    exit 1
fi

# 3. 推送到设备
echo "📱 正在推送模型到设备 (约需几分钟)..."
adb push "${DOWNLOAD_DIR}/${MODEL_NAME}" "${DEVICE_APP_DIR}/"

# 4. 拷贝到 app 私有目录
echo ""
echo "📂 正在拷贝到应用私有目录..."
adb shell "run-as ${PACKAGE_NAME} mkdir -p files/models"
adb shell "run-as ${PACKAGE_NAME} cp ${DEVICE_APP_DIR}/${MODEL_NAME} files/models/${MODEL_NAME}"
adb shell "run-as ${PACKAGE_NAME} chmod 644 files/models/${MODEL_NAME}"

# 5. 验证
echo ""
echo "🔍 验证模型文件..."
adb shell "run-as ${PACKAGE_NAME} ls -la files/models/${MODEL_NAME}"

# 6. 清理
adb shell "rm -f ${DEVICE_APP_DIR}/${MODEL_NAME}" 2>/dev/null || true

echo ""
echo "✅ 完成! 模型已部署到设备，重启 APP 即可使用本地 LLM 摘要。"
echo "   引擎: MediaPipe LLM Inference → TextRank 自动降级"
