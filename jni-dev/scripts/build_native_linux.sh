#!/bin/bash
set -e

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java}"
JNI_DIR="$(dirname "$0")/src/main/jni"
BUILD_DIR="$(dirname "$0")/build/native"
OUTPUT_DIR="$(dirname "$0")/src/main/resources"

mkdir -p "$BUILD_DIR"
mkdir -p "$OUTPUT_DIR"

echo "Compiling libclassfinal_native.so ..."

gcc -shared -fPIC -O2 -s \
    -I"${JAVA_HOME}/include" \
    -I"${JAVA_HOME}/include/linux" \
    -I"${JNI_DIR}" \
    -o "${OUTPUT_DIR}/libclassfinal_native.so" \
    "${JNI_DIR}/classfinal_native.c" \
    -lm

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile libclassfinal_native.so"
    exit 1
fi

echo "Successfully built: ${OUTPUT_DIR}/libclassfinal_native.so"
