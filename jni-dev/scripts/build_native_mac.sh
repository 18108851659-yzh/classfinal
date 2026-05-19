#!/bin/bash
set -e

if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || echo "")
    if [ -z "$JAVA_HOME" ]; then
        echo "ERROR: JAVA_HOME is not set and java_home helper not found"
        echo "Please set JAVA_HOME, e.g.: export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home"
        exit 1
    fi
fi

JNI_DIR="$(dirname "$0")/src/main/jni"
BUILD_DIR="$(dirname "$0")/build/native"
OUTPUT_DIR="$(dirname "$0")/src/main/resources"

mkdir -p "$BUILD_DIR"
mkdir -p "$OUTPUT_DIR"

echo "JAVA_HOME: $JAVA_HOME"
echo "Compiling libclassfinal_native.jnilib ..."

clang -shared -fPIC -O2 \
    -I"${JAVA_HOME}/include" \
    -I"${JAVA_HOME}/include/darwin" \
    -I"${JNI_DIR}" \
    -o "${OUTPUT_DIR}/libclassfinal_native.jnilib" \
    "${JNI_DIR}/classfinal_native.c" \
    -lm

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile libclassfinal_native.jnilib"
    exit 1
fi

echo "Successfully built: ${OUTPUT_DIR}/libclassfinal_native.jnilib"
