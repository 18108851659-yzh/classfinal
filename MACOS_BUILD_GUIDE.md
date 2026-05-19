# ClassFinal Native 库 - macOS 编译指南

## 概述

ClassFinal 的核心解密逻辑在 Native 层（C 语言）实现，需要针对不同平台编译对应的动态链接库。macOS 上的产物为 `libclassfinal_native.jnilib`。

本文档涵盖：环境准备、编译步骤、常见问题、以及 C 源码中的 macOS 特有处理。

---

## 1. 环境要求

| 工具 | 最低版本 | 说明 |
|------|----------|------|
| macOS | 10.12+ | Sierra 及以上 |
| Xcode Command Line Tools | 最新 | 提供 clang 编译器和系统头文件 |
| JDK | 8+ | 需要 JDK（非 JRE），提供 `jni.h` 头文件 |
| Maven | 3.x | 构建整个项目 |

### 1.1 安装 Xcode Command Line Tools

```bash
# 安装（弹出对话框确认）
xcode-select --install

# 验证
clang --version
# 应输出类似: Apple clang version 14.0.x ...
```

### 1.2 确认 JDK 安装

```bash
# 查看已安装的 JDK
/usr/libexec/java_home -V

# 输出示例:
# Matching Java Virtual Machines (3):
#     21.0.1, x86_64: "Java SE 21.0.1" /Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
#     17.0.9, x86_64: "Java SE 17.0.9" /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
#     11.0.21, x86_64: "Java SE 11.0.21" /Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home

# 设置 JAVA_HOME（选择你需要的 JDK 版本）
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home

# 或使用 java_home 自动选择
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

---

## 2. 编译步骤

### 2.1 使用编译脚本（推荐）

```bash
cd classfinal-core

# 确保 JAVA_HOME 已设置
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 赋予执行权限（首次）
chmod +x build_native_mac.sh

# 执行编译
./build_native_mac.sh
```

**预期输出**：

```
==========================================
 ClassFinal Native Build - macOS
==========================================
JAVA_HOME : /Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
JNI_DIR   : /path/to/classfinal-core/src/main/jni
OUTPUT    : /path/to/classfinal-core/src/main/resources/libclassfinal_native.jnilib

Compiling libclassfinal_native.jnilib (Universal Binary) ...

==========================================
 Build SUCCESS!
==========================================

Output: /path/to/classfinal-core/src/main/resources/libclassfinal_native.jnilib
-rwxr-xr-x  1 user  staff   18K May 19 19:00 libclassfinal_native.jnilib

Architectures:
/path/to/libclassfinal_native.jnilib: Mach-O universal binary with 2 architectures: [x86_64:arm64]

Exported symbols:
0000000000001c40 T _JNI_OnLoad

Next steps:
  1. Rebuild the project: mvn clean package -DskipTests
  2. The .jnilib will be packaged into classfinal-fatjar automatically
```

### 2.2 手动编译

如果脚本不适用，可以手动执行编译命令：

```bash
# 设置变量
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
JNI_DIR=src/main/jni
OUTPUT_DIR=src/main/resources

# 编译 Universal Binary（同时支持 Intel + Apple Silicon）
clang -arch x86_64 -arch arm64 \
    -shared -fPIC -O2 -s \
    -mmacosx-version-min=10.12 \
    -I"${JAVA_HOME}/include" \
    -I"${JAVA_HOME}/include/darwin" \
    -I"${JNI_DIR}" \
    -o "${OUTPUT_DIR}/libclassfinal_native.jnilib" \
    "${JNI_DIR}/classfinal_native.c"
```

### 2.3 仅编译当前架构

如果不需要 Universal Binary，可以只编译当前机器架构：

```bash
# Apple Silicon (M1/M2/M3)
clang -arch arm64 -shared -fPIC -O2 -s \
    -I"${JAVA_HOME}/include" \
    -I"${JAVA_HOME}/include/darwin" \
    -I"${JNI_DIR}" \
    -o "${OUTPUT_DIR}/libclassfinal_native.jnilib" \
    "${JNI_DIR}/classfinal_native.c"

# Intel Mac
clang -arch x86_64 -shared -fPIC -O2 -s \
    -I"${JAVA_HOME}/include" \
    -I"${JAVA_HOME}/include/darwin" \
    -I"${JNI_DIR}" \
    -o "${OUTPUT_DIR}/libclassfinal_native.jnilib" \
    "${JNI_DIR}/classfinal_native.c"
```

### 2.4 编译后构建项目

```bash
cd ..  # 回到项目根目录
mvn clean package -DskipTests
```

---

## 3. 编译产物验证

### 3.1 检查文件类型

```bash
file src/main/resources/libclassfinal_native.jnilib

# Universal Binary 应输出:
# Mach-O universal binary with 2 architectures: [x86_64:arm64]

# 单架构应输出:
# Mach-O 64-bit dynamically linked shared library arm64
# 或
# Mach-O 64-bit dynamically linked shared library x86_64
```

### 3.2 检查导出符号

```bash
nm -gU src/main/resources/libclassfinal_native.jnilib | grep " T "

# 应仅输出（JNI 动态注册）:
# 0000000000001c40 T _JNI_OnLoad
#
# 如果看到 Java_net_roseboy_classfinal_jni_NativeDecryptor_nativeDecryptClass
# 则说明使用的是旧的静态注册方式，需要更新 C 源码
```

### 3.3 端到端测试

```bash
# 1. 加密测试 jar
java -jar classfinal-fatjar/target/classfinal-fatjar-1.2.1.jar \
    -pwd test \
    -packages com.test.demo \
    -jar test-springboot/target/test-springboot-1.0.0.jar

# 2. 启动加密后的应用
java -javaagent:classfinal-fatjar/target/classfinal-fatjar-1.2.1.jar="-pwd test" \
    -jar test-springboot/target/test-springboot-1.0.0-encrypted.jar

# 3. 验证接口
curl http://localhost:8080/hello
# 应返回: Hello, ClassFinal! This is a test application.
```

---

## 4. C 源码中的 macOS 特有处理

### 4.1 条件编译

`classfinal_native.c` 使用预处理器宏区分平台：

```c
#ifdef _WIN32
    // Windows 特有代码
#elif defined(__APPLE__)
    // macOS 特有代码
#else
    // Linux 代码
#endif
```

### 4.2 macOS 头文件

```c
#elif defined(__APPLE__)
#include <stdio.h>
#include <unistd.h>
#include <sys/sysctl.h>    // sysctl() - 用于反调试检测
#include <libproc.h>       // 进程信息（可选）
```

### 4.3 反调试检测（macOS）

macOS 没有 `/proc/self/status`，使用 `sysctl` + `kinfo_proc` 检测调试器：

```c
static int is_debugger_present() {
#ifdef _WIN32
    if (IsDebuggerPresent()) return 1;
    return 0;
#elif defined(__APPLE__)
    /* macOS: 通过 sysctl 读取进程的 P_TRACED 标志 */
    int mib[4];
    struct kinfo_proc info;
    size_t size = sizeof(info);
    mib[0] = CTL_KERN;
    mib[1] = KERN_PROC;
    mib[2] = KERN_PROC_PID;
    mib[3] = getpid();
    if (sysctl(mib, 4, &info, &size, NULL, 0) == 0) {
        return (info.kp_proc.p_flag & P_TRACED) != 0;
    }
    return 0;
#else
    /* Linux: 读取 /proc/self/status 中的 TracerPid */
    FILE *f = fopen("/proc/self/status", "r");
    ...
#endif
}
```

**原理**：macOS 内核为每个进程维护 `kinfo_proc` 结构，其中 `kp_proc.p_flag` 的 `P_TRACED` 位表示进程是否正在被 ptrace/lldb/gdb 跟踪。

### 4.4 DLL 完整性校验（macOS）

非 Windows 平台使用标准 C 文件 I/O 读取 jnilib 文件并计算 CRC32：

```c
void set_integrity_from_file(const char *lib_path) {
    ...
#else
    /* macOS/Linux: 标准 fopen/fread 读取文件 */
    FILE *f = fopen(lib_path, "rb");
    fseek(f, 0, SEEK_END);
    long fileSize = ftell(f);
    fseek(f, 0, SEEK_SET);
    unsigned char *buf = (unsigned char *)malloc(fileSize);
    fread(buf, 1, fileSize, f);
    fclose(f);
    unsigned int crc = compute_crc32(buf, (int)fileSize);
    free(buf);
    g_integrity_ok = (crc == g_expected_crc32) ? 1 : 0;
#endif
}
```

---

## 5. 编译选项说明

| 选项 | 说明 |
|------|------|
| `-arch x86_64 -arch arm64` | 生成 Universal Binary，同时支持 Intel 和 Apple Silicon |
| `-shared` | 编译为动态链接库 |
| `-fPIC` | 生成位置无关代码（共享库必需） |
| `-O2` | 优化级别 2（平衡编译速度和运行性能） |
| `-s` | 剥离调试符号（减小文件体积，增加逆向难度） |
| `-mmacosx-version-min=10.12` | 最低兼容 macOS 10.12 Sierra |
| `-I${JAVA_HOME}/include` | JNI 核心头文件（jni.h） |
| `-I${JAVA_HOME}/include/darwin` | macOS 平台 JNI 头文件（jni_md.h） |
| `-I${JNI_DIR}` | 项目 JNI 头文件目录 |
| `-o ...jnilib` | macOS 上 JNI 库约定使用 `.jnilib` 后缀 |

---

## 6. 常见问题

### Q1: 编译报错 `sys/sysctl.h: No such file or directory`

**原因**：未安装 Xcode Command Line Tools。

```bash
xcode-select --install
```

### Q2: 编译报错 `jni.h: No such file or directory`

**原因**：JAVA_HOME 指向了 JRE 而非 JDK，或未设置 JAVA_HOME。

```bash
# 检查
ls $JAVA_HOME/include/jni.h

# 如果不存在，重新设置 JAVA_HOME 指向 JDK
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Q3: 运行时报错 `UnsatisfiedLinkError: no classfinal_native in java.library.path`

**原因**：jnilib 文件未被正确打包到 fat jar 中。

**解决**：
1. 确认 `classfinal-core/src/main/resources/libclassfinal_native.jnilib` 存在
2. 重新构建：`mvn clean package -DskipTests`
3. 验证 jar 中是否包含：`jar tf classfinal-fatjar-1.2.1.jar | grep jnilib`

### Q4: Apple Silicon (M1/M2) 上运行报错 `Bad CPU type in executable`

**原因**：jnilib 只编译了 x86_64 架构，在 arm64 JVM 上无法加载。

**解决**：使用 Universal Binary 编译（`-arch x86_64 -arch arm64`），或单独编译 arm64 版本。

### Q5: 如何在 Intel Mac 上交叉编译 arm64 版本？

Apple Clang 支持交叉编译，直接使用 `-arch arm64` 即可，无需额外工具链：

```bash
clang -arch arm64 -shared -fPIC -O2 -s \
    -I"${JAVA_HOME}/include" \
    -I"${JAVA_HOME}/include/darwin" \
    -I"${JNI_DIR}" \
    -o libclassfinal_native.jnilib \
    classfinal_native.c
```

### Q6: 编译时出现 `warning: incompatible pointer types`

如果出现与 `sysctl` 相关的类型警告，确保包含了正确的头文件：

```c
#include <sys/sysctl.h>
```

并在编译时确保使用了 macOS SDK 的头文件路径（Xcode Command Line Tools 会自动配置）。

### Q7: 如何调试 Native 层代码？

```bash
# 编译带调试符号的版本（去掉 -s，加上 -g）
clang -arch arm64 -shared -fPIC -O0 -g \
    -I"${JAVA_HOME}/include" \
    -I"${JAVA_HOME}/include/darwin" \
    -I"${JNI_DIR}" \
    -o libclassfinal_native.jnilib \
    classfinal_native.c

# 使用 lldb 调试
lldb -p <java-pid>
# 在 lldb 中设置断点
breakpoint set -name fn_decrypt
continue
```

> **注意**：调试版本会触发反调试检测（`is_debugger_present()`），需要在开发时临时禁用该检测。

---

## 7. 与 Windows/Linux 编译的对比

| 项目 | Windows | macOS | Linux |
|------|---------|-------|-------|
| 编译器 | MSVC cl.exe | Apple Clang | GCC |
| 产物名 | `classfinal_native.dll` | `libclassfinal_native.jnilib` | `libclassfinal_native.so` |
| 架构 | x64 | x86_64 + arm64 (Universal) | x86_64 |
| JNI 头文件目录 | `include/win32` | `include/darwin` | `include/linux` |
| 反调试 | `IsDebuggerPresent()` | `sysctl + P_TRACED` | `/proc/self/status` |
| 文件读取 | `CreateFileA + MapViewOfFile` | `fopen + fread` | `fopen + fread` |
| 编译脚本 | `build_native_win.bat` | `build_native_mac.sh` | `build_native_linux.sh` |
