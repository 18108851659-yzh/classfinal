#!/bin/bash
set -e

# ============================================================
# ClassFinal Native Library - macOS 编译脚本
# 支持 Intel (x86_64) 和 Apple Silicon (arm64) 通用二进制
# ============================================================

# 检测 JAVA_HOME
if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || echo "")
    if [ -z "$JAVA_HOME" ]; then
        echo "ERROR: JAVA_HOME is not set and java_home helper not found"
        echo "Please set JAVA_HOME, e.g.:"
        echo "  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home"
        echo "  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
        echo "  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
        exit 1
    fi
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JNI_DIR="${SCRIPT_DIR}/src/main/jni"
OUTPUT_DIR="${SCRIPT_DIR}/src/main/resources"

mkdir -p "$OUTPUT_DIR"

echo "=========================================="
echo " ClassFinal Native Build - macOS"
echo "=========================================="
echo "JAVA_HOME : $JAVA_HOME"
echo "JNI_DIR   : $JNI_DIR"
echo "OUTPUT    : $OUTPUT_DIR/libclassfinal_native.jnilib"

# 检查 JNI 头文件是否存在
if [ ! -f "${JAVA_HOME}/include/jni.h" ]; then
    echo "ERROR: ${JAVA_HOME}/include/jni.h not found"
    echo "Is JAVA_HOME pointing to a JDK (not JRE)?"
    exit 1
fi

if [ ! -f "${JAVA_HOME}/include/darwin/jni_md.h" ]; then
    echo "ERROR: ${JAVA_HOME}/include/darwin/jni_md.h not found"
    echo "Expected macOS JDK include directory"
    exit 1
fi

# 检查源文件
if [ ! -f "${JNI_DIR}/classfinal_native.c" ]; then
    echo "ERROR: ${JNI_DIR}/classfinal_native.c not found"
    exit 1
fi

# 检测架构
ARCH=$(uname -m)
echo "Host Arch : $ARCH"

# 编译选项
# -arch x86_64 -arch arm64 生成 Universal Binary (同时支持 Intel 和 Apple Silicon)
# -mmacosx-version-min=10.12 确保兼容性
# -shared -fPIC 生成动态链接库
# -O2 优化级别
# -s 剥离调试符号（减小文件体积）

echo ""
echo "Compiling libclassfinal_native.jnilib (Universal Binary) ..."
echo ""

clang -arch x86_64 -arch arm64 \
    -shared -fPIC -O2 -s \
    -mmacosx-version-min=10.12 \
    -I"${JAVA_HOME}/include" \
    -I"${JAVA_HOME}/include/darwin" \
    -I"${JNI_DIR}" \
    -o "${OUTPUT_DIR}/libclassfinal_native.jnilib" \
    "${JNI_DIR}/classfinal_native.c"

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Compilation failed!"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Ensure Xcode Command Line Tools are installed:"
    echo "     xcode-select --install"
    echo "  2. Ensure JAVA_HOME points to a valid JDK:"
    echo "     ls \$JAVA_HOME/include/jni.h"
    echo "  3. If only building for current architecture, try:"
    echo "     clang -arch $(uname -m) -shared -fPIC -O2 -s \\"
    echo "       -I\$JAVA_HOME/include -I\$JAVA_HOME/include/darwin \\"
    echo "       -o libclassfinal_native.jnilib classfinal_native.c"
    exit 1
fi

echo ""
echo "=========================================="
echo " Build SUCCESS!"
echo "=========================================="

# 显示产物信息
echo ""
echo "Output: ${OUTPUT_DIR}/libclassfinal_native.jnilib"
ls -lh "${OUTPUT_DIR}/libclassfinal_native.jnilib"
echo ""

# 显示支持的架构
echo "Architectures:"
file "${OUTPUT_DIR}/libclassfinal_native.jnilib"
echo ""

# 显示导出符号（验证 JNI 动态注册：仅 JNI_OnLoad）
echo "Exported symbols:"
nm -gU "${OUTPUT_DIR}/libclassfinal_native.jnilib" 2>/dev/null | grep " T " || echo "  (nm not available, skipping)"
echo ""

echo "Next steps:"
echo "  1. Rebuild the project: mvn clean package -DskipTests"
echo "  2. The .jnilib will be packaged into classfinal-fatjar automatically"
