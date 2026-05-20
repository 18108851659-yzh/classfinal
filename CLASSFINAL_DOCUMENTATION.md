# ClassFinal v1.2.1 - Java 类文件加密工具

> 对 Spring Boot / JFinal 等 Java 应用的 class 文件进行加密保护，防止反编译和逆向分析。

---

## 目录

- [1. 概述](#1-概述)
- [2. 架构设计](#2-架构设计)
  - [2.1 项目结构](#21-项目结构)
  - [2.2 加密流程](#22-加密流程)
  - [2.3 解密流程（运行时）](#23-解密流程运行时)
  - [2.4 安全机制](#24-安全机制)
- [3. 快速开始](#3-快速开始)
  - [3.1 Maven 插件方式（推荐）](#31-maven-插件方式推荐)
  - [3.2 命令行方式](#32-命令行方式)
  - [3.3 启动加密后的应用](#33-启动加密后的应用)
- [4. 配置参数](#4-配置参数)
  - [4.1 Maven 插件配置](#41-maven-插件配置)
  - [4.2 命令行参数](#42-命令行参数)
  - [4.3 启动参数](#43-启动参数)
- [5. 开发指南](#5-开发指南)
  - [5.1 环境要求](#51-环境要求)
  - [5.2 编译构建](#52-编译构建)
  - [5.3 Native 层开发](#53-native-层开发)
  - [5.4 核心模块说明](#54-核心模块说明)
  - [5.5 扩展与定制](#55-扩展与定制)
- [6. CI/CD 集成](#6-cicd-集成)
- [7. 常见问题](#7-常见问题)

---

## 1. 概述

ClassFinal 是一款轻量级的 Java class 文件加密工具，主要特性：

| 特性 | 说明 |
|------|------|
| **零侵入** | 无需修改应用源码，通过 Maven 插件或命令行一键加密 |
| **按需解密** | 运行时仅在类加载时解密到内存，不落盘 |
| **Native 解密** | 核心解密逻辑在 C/C++ Native 层执行，增加逆向难度 |
| **跨平台** | 支持 Windows (x64)、macOS (Intel + Apple Silicon)、Linux (x86_64) |
| **JDK CDS 兼容** | 支持 `-Xshare:auto` / `-Xshare:on`，不影响启动性能 |
| **多框架支持** | 原生支持 Spring Boot、JFinal，可扩展其他框架 |
| **配置文件加密** | 支持对 application.yml 等配置文件进行加密 |
| **方法体清除** | 加密后原 class 文件的方法体被清空，双重保护 |
| **防 LLM 破解** | JNI 动态注册 + 头部伪装，防止大模型自动分析代码 |

### 适用场景

- 保护 Spring Boot 微服务的核心业务逻辑
- 防止 SaaS 产品被逆向分析
- 保护包含敏感算法或授权逻辑的 Java 应用
- 防止 LLM/大模型自动读取和理解代码逻辑

### 版本兼容性

| JDK | 兼容性 | CDS 支持 |
|-----|--------|----------|
| JDK 8 | 完全支持 | - |
| JDK 11 | 完全支持 | ✅ |
| JDK 17 | 完全支持 | ✅ |
| JDK 21 | 完全支持 | ✅ |

### 平台支持

| 平台 | 架构 | Native 库 |
|------|------|-----------|
| Windows | x86_64 | `classfinal_native.dll` (18KB) |
| macOS | x86_64 + arm64 (Universal) | `libclassfinal_native.jnilib` (83KB) |
| Linux | x86_64 | `libclassfinal_native.so` (18KB) |

---

## 2. 架构设计

### 2.1 项目结构

```
classfinal/
├── pom.xml                          # 父 POM (groupId: net.roseboy, version: 1.2.1)
│
├── classfinal-core/                 # 核心模块
│   ├── src/main/java/
│   │   └── net/roseboy/classfinal/
│   │       ├── Const.java           # 常量定义
│   │       ├── CoreAgent.java       # Agent 入口（premain）
│   │       ├── AgentTransformer.java    # ClassFileTransformer 实现
│   │       ├── JarEncryptor.java    # jar/war 加密器
│   │       ├── JarDecryptor.java    # jar/war 解密器
│   │       ├── jni/
│   │       │   └── NativeDecryptor.java  # JNI 接口层
│   │       └── util/               # 工具类集合
│   │           ├── EncryptUtils.java      # AES 加密/解密
│   │           ├── ClassUtils.java        # Javassist 字节码操作
│   │           ├── JarUtils.java          # jar/war 操作
│   │           ├── IoUtils.java           # IO 操作
│   │           ├── StrUtils.java          # 字符串处理
│   │           └── ...                    # 其他工具类
│   ├── src/main/jni/
│   │   └── classfinal_native.c     # Native 层解密实现（C语言）
│   ├── src/main/resources/
│   │   ├── classfinal_native.dll   # Windows x64 Native 库
│   │   ├── libclassfinal_native.jnilib  # macOS Universal Binary
│   │   └── libclassfinal_native.so      # Linux x86_64 Native 库
│   ├── build_native_win.bat        # Windows 编译脚本
│   ├── build_native_mac.sh         # macOS 编译脚本
│   └── build_native_linux.sh       # Linux 编译脚本
│
├── classfinal-fatjar/               # Fat JAR（独立运行）
│   ├── src/main/java/
│   │   └── net/roseboy/classfinal/
│   │       ├── Agent.java           # javaagent 入口
│   │       └── Main.java            # 命令行入口
│   └── pom.xml                      # maven-shade-plugin 打包
│
├── classfinal-maven-plugin/         # Maven 插件
│   └── src/main/java/
│       └── net/roseboy/classfinal/plugin/
│           └── ClassFinalPlugin.java   # Mojo 实现
│
├── .github/workflows/
│   └── build-native.yml            # GitHub Actions 三平台编译
│
├── test-springboot/                 # 测试用例
│   ├── src/main/java/com/test/demo/
│   │   ├── Application.java         # Spring Boot 启动类
│   │   └── HelloController.java     # 测试 Controller
│   └── pom.xml                      # 已配置 classfinal-maven-plugin
│
└── jni-dev/                         # JNI 开发辅助目录
```

### 2.2 加密流程

```
原始 jar/war
     │
     ▼
JarEncryptor.doEncryptJar()
     │
     ├─ 1. 解压 jar/war 到临时目录
     ├─ 2. 生成随机加密路径 META-INF/.xxxxxxxx
     ├─ 3. 注入 classfinal agent 到 MANIFEST.MF
     │
     ├─ 4. 对每个目标 .class 文件:
     │     ├─ AES/CBC/PKCS5Padding 加密（密钥 = MD5(主密钥 + 类名 + Salt)）
     │     ├─ 添加 0xCAFEBABE 头部伪装（让加密文件看起来像普通 class）
     │     └─ 存入 META-INF/.xxxxxxxx/包名.class
     │
     ├─ 5. 清除原 .class 文件的所有方法体（保留类结构）
     ├─ 6. 写入加密索引文件（XOR 混淆）
     ├─ 7. 写入 Manifest 属性（加密路径、DLL CRC32）
     ├─ 8. 重新打包为 xxx-encrypted.jar/war
     │
     ▼
加密产物：xxx-encrypted.jar
```

### 2.3 解密流程（运行时）

```
java -javaagent:classfinal-fatjar-1.2.1.jar -jar xxx-encrypted.jar
     │
     ▼
JVM 启动 → Agent.premain() → CoreAgent.premain()
     │
     ▼
JNI_OnLoad() → RegisterNatives() 动态注册 native 方法
     │
     ▼
注册 AgentTransformer（ClassFileTransformer）
     │
     ▼
JVM 加载每个类时触发 transform():
     │
     ├─ 判断是否为加密类（查索引文件）
     ├─ 从 jar 中读取加密数据
     ├─ 调用 NativeDecryptor.nativeDecryptClass()
     │     │
     │     ▼ (Native 层 - fn_decrypt)
     │     ├─ 反调试检测（Windows: IsDebuggerPresent / macOS: sysctl P_TRACED / Linux: TracerPid）
     │     ├─ DLL/SO 完整性校验（CRC32）
     │     ├─ 组装密钥（分片 XOR + 重排序）
     │     ├─ 密钥派生（key + className + salt → MD5 → 取后16字节hex）
     │     ├─ 跳过 4 字节头部伪装（0xCAFEBABE）
     │     ├─ AES-CBC 解密
     │     ├─ 内存密钥擦除（memset 清零）
     │     └─ 返回明文 class 字节码
     │
     ▼
返回 byte[] 给 JVM → 正常加载类（全程内存，不落盘）
```

**关键点：解密结果 `byte[]` 直接传入 JVM，全程在内存中完成，不写入磁盘。**

### 2.4 安全机制

#### 多层防护体系

```
┌─────────────────────────────────────────────────────┐
│                  应用层防护                          │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ 方法体清除   │  │ 加密路径随机化│  │ 索引文件混淆│  │
│  └─────────────┘  └──────────────┘  └────────────┘  │
├─────────────────────────────────────────────────────┤
│                  Java 层防护                         │
│  ┌─────────────┐  ┌──────────────┐                   │
│  │ AES-256-CBC │  │ 密钥分片混淆  │                   │
│  └─────────────┘  └──────────────┘                   │
├─────────────────────────────────────────────────────┤
│                  Native 层防护                       │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ JNI动态注册  │  │ DLL完整性校验 │  │ 反调试检测  │  │
│  └─────────────┘  └──────────────┘  └────────────┘  │
│  ┌─────────────┐  ┌──────────────┐                   │
│  │ 头部伪装     │  │ 内存密钥擦除  │                   │
│  └─────────────┘  └──────────────┘                   │
└─────────────────────────────────────────────────────┘
```

| 安全措施 | 实现位置 | 效果 |
|----------|----------|------|
| **JNI 动态注册** | `classfinal_native.c` - `JNI_OnLoad` | DLL/SO 导出表仅暴露 `JNI_OnLoad`，隐藏解密函数名，防止 LLM 定位解密入口 |
| **头部伪装** | `JarEncryptor` + `classfinal_native.c` | 加密数据以 `0xCAFEBABE` 开头，看起来像普通 class 文件，防止 LLM 识别加密数据 |
| **密钥分片混淆** | `classfinal_native.c` - `assemble_key()` | 主密钥分为两段，各自 XOR 混淆后再重排序组合 |
| **DLL CRC32 校验** | `JarEncryptor` + `JarDecryptor` + `classfinal_native.c` | 运行时校验 Native 库是否被篡改 |
| **反调试检测** | `classfinal_native.c` - `is_debugger_present()` | Windows: `IsDebuggerPresent()` / macOS: `sysctl + P_TRACED` / Linux: `/proc/self/status` TracerPid |
| **内存密钥擦除** | `classfinal_native.c` - `fn_decrypt()` | 使用完毕后立即 `memset(key, 0, ...)` 清零 |
| **加密路径随机化** | `JarEncryptor` - `generateRandomPath()` | 每次加密生成随机 8 字符路径如 `.a3f9kz2m` |
| **索引文件 XOR 混淆** | `JarEncryptor` / `JarDecryptor` | 加密类列表用固定 XOR key 混淆存储 |
| **方法体清除** | `ClassUtils.rewriteAllMethods()` | 原 class 文件保留类结构但方法体全部清空 |

---

## 3. 快速开始

### 3.1 Maven 插件方式（推荐）

**步骤 1：在项目的 `pom.xml` 中添加插件**

```xml
<build>
    <plugins>
        <plugin>
            <groupId>net.roseboy</groupId>
            <artifactId>classfinal-maven-plugin</artifactId>
            <version>1.2.1</version>
            <configuration>
                <!-- 必填：要加密的包名 -->
                <packages>com.yourcompany</packages>

                <!-- 选填：排除的类名（通配符匹配） -->
                <!-- <excludes>
                    <exclude>com.yourcompany.config.*</exclude>
                </excludes> -->

                <!-- 选填：需要加密的内部 lib/jar 名称 -->
                <!-- <libjars>my-lib.jar</libjars> -->

                <!-- 选填：需要加密的配置文件 -->
                <!-- <cfgfiles>application.yml,application.properties</cfgfiles> -->

                <!-- 选填：密码（默认自动生成随机密码） -->
                <!-- <password>yourpassword</password> -->

                <!-- 选填：开启调试日志 -->
                <!-- <debug>true</debug> -->
            </configuration>
        </plugin>
    </plugins>
</build>
```

**步骤 2：正常打包**

```bash
mvn clean package
```

打包完成后会在 target 目录下生成两个文件：

| 文件 | 说明 |
|------|------|
| `xxx.jar` | 原始 jar（未加密） |
| `xxx-encrypted.jar` | 加密后的 jar（可直接部署运行） |

**步骤 3：启动加密后的应用**

```bash
java -javaagent:classfinal-fatjar-1.2.1.jar -jar xxx-encrypted.jar
```

> 密码已内嵌到加密 jar 中，无需额外传递密码参数。

### 3.2 命令行方式

```bash
# 交互模式（逐步引导输入参数）
java -jar classfinal-fatjar-1.2.1.jar

# 或直接指定参数
java -jar classfinal-fatjar-1.2.1.jar \
    -file your-app.jar \
    -packages com.yourcompany \
    -Y
```

**命令行参数说明**：

| 参数 | 必填 | 说明 |
|------|------|------|
| `-file` | 是 | 要加密的 jar/war 文件路径 |
| `-packages` | 是 | 要加密的包名前缀，多个用逗号分隔 |
| `-exclude` | 否 | 排除的类名，支持通配符 `*` 和 `?` |
| `-libjars` | 否 | 内部 lib/jar 名称 |
| `-classpath` | 否 | 外部依赖路径 |
| `-cfgfiles` | 否 | 配置文件名 |
| `-Y` | 否 | 跳过确认提示，直接执行加密 |
| `-debug` | 否 | 输出调试日志 |

### 3.3 启动加密后的应用

```bash
# 基本启动
java -javaagent:classfinal-fatjar-1.2.1.jar -jar xxx-encrypted.jar

# 带 CDS 加速（推荐，JDK 类加载加速约 20%-40%）
java -Xshare:auto -javaagent:classfinal-fatjar-1.2.1.jar -jar xxx-encrypted.jar

# 强制 CDS 模式（CDS 不可用时拒绝启动）
java -Xshare:on -javaagent:classfinal-fatjar-1.2.1.jar -jar xxx-encrypted.jar

# 带 JVM 参数的完整示例
java -Xshare:auto -Xms512m -Xmx1024m \
    -javaagent:classfinal-fatjar-1.2.1.jar \
    -jar xxx-encrypted.jar \
    --server.port=8080
```

> **注意**：`-javaagent` 参数必须放在 `-jar` 之前。

---

## 4. 配置参数

### 4.1 Maven 插件配置

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `packages` | String | **是** | 无 | 要加密的包名前缀，多个用逗号分隔。如 `com.company.service,com.company.dao` |
| `excludes` | List | 否 | 空 | 排除的类名，支持通配符 `*` 和 `?` |
| `libjars` | String | 否 | 空 | 需要加密的内部依赖 jar 包名称 |
| `classpath` | String | 否 | 空 | 外部依赖 jar 的 classpath |
| `cfgfiles` | String | 否 | 空 | 需要加密的配置文件名（Spring Boot 的 yml/properties） |
| `password` | String | 否 | 随机生成 | 加密密码。**务必记住此密码，否则无法解密运行** |
| `debug` | boolean | 否 | false | 是否输出详细调试日志 |

**完整示例**：

```xml
<plugin>
    <groupId>net.roseboy</groupId>
    <artifactId>classfinal-maven-plugin</artifactId>
    <version>1.2.1</version>
    <configuration>
        <packages>com.myapp.core,com.myapp.biz</packages>
        <excludes>
            <exclude>com.myapp.core.config.*</exclude>
            <exclude>*Application</exclude>
        </excludes>
        <libjars>my-sdk.jar</libjars>
        <cfgfiles>application-prod.yml</cfgfiles>
        <password>mYs3cur3P@ssw0rd</password>
        <debug>false</debug>
    </configuration>
</plugin>
```

### 4.2 命令行参数

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `-file` | **是** | 无 | 要加密的 jar/war 文件路径 |
| `-packages` | **是** | 无 | 要加密的包名前缀 |
| `-exclude` | 否 | 空 | 排除的类名 |
| `-libjars` | 否 | 空 | 内部 lib/jar 名称 |
| `-classpath` | 否 | 空 | 外部依赖路径 |
| `-cfgfiles` | 否 | 空 | 配置文件名 |
| `-Y` | 否 | false | 跳过确认提示，直接执行加密 |
| `-debug` | 否 | false | 输出调试日志 |

```bash
# 命令行完整示例
java -jar classfinal-fatjar-1.2.1.jar \
    -file myapp.jar \
    -packages com.myapp \
    -exclude "com.myapp.config.*" \
    -libjars my-sdk.jar \
    -cfgfiles application.yml \
    -Y
```

### 4.3 启动参数

启动加密后的应用时，密码已内嵌在 jar 中，通常无需额外参数：

```bash
java -javaagent:classfinal-fatjar-1.2.1.jar -jar xxx-encrypted.jar
```

---

## 5. 开发指南

### 5.1 环境要求

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 8+ | Java 编译运行 |
| Maven | 3.x | 项目构建 |
| MSVC | 2019+ (x64) | Native DLL 编译（Windows） |
| Apple Clang | Xcode CLT | Native jnilib 编译（macOS） |
| GCC | 4.8+ | Native .so 编译（Linux） |

### 5.2 编译构建

```bash
# 克隆项目
git clone https://github.com/18108851659-yzh/classfinal.git
cd classfinal

# 全量构建（Native 库已预编译在 resources 中）
mvn clean package -DskipTests

# 构建产物位置：
# classfinal-core/target/classfinal-core-1.2.1.jar        -- 核心 JAR
# classfinal-fatjar/target/classfinal-fatjar-1.2.1.jar      -- Fat JAR（Agent + CLI，含三平台 Native 库）
# classfinal-maven-plugin/target/classfinal-maven-plugin-1.2.1.jar  -- Maven 插件
```

### 5.3 Native 层开发

#### 目录结构

```
classfinal-core/src/main/jni/
└── classfinal_native.c          # C 语言源码（核心解密逻辑，跨平台）

classfinal-core/
├── build_native_win.bat         # Windows 编译脚本（MSVC）
├── build_native_mac.sh          # macOS 编译脚本（Apple Clang，Universal Binary）
└── build_native_linux.sh        # Linux 编译脚本（GCC）
```

#### 编译 Native DLL（Windows）

编译脚本位于 `classfinal-core/build_native_win.bat`，内部调用 MSVC cl.exe：

```bat
@echo off
call "D:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvarsall.bat" x86_amd64
set JAVA_HOME=<你的JDK路径>
set INCLUDE=%JAVA_HOME%\include;%JAVA_HOME%\include\win32;..\jni;%INCLUDE%
cl.exe /LD /O2 /MD "classfinal_native.c" /Fe:"../resources/classfinal_native.dll" /link
```

**关键点**：
- 必须使用 **x64** 架构编译（`x86_amd64`），32位 DLL 无法在 64 位 JVM 上加载
- `JAVA_HOME` 需指向包含 `include/jni.h` 的 JDK 安装目录
- 编译产物输出到 `src/main/resources/classfinal_native.dll`

#### 编译 Native jnilib（macOS）

```bash
cd classfinal-core
chmod +x build_native_mac.sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./build_native_mac.sh
```

脚本自动编译 **Universal Binary**（同时支持 Intel x86_64 和 Apple Silicon arm64），产物为 `libclassfinal_native.jnilib`。

#### 编译 Native .so（Linux）

```bash
cd classfinal-core
chmod +x build_native_linux.sh
export JAVA_HOME=/usr/lib/jvm/java-21
./build_native_linux.sh
```

#### 使用 GitHub Actions 云编译（推荐）

项目已配置 GitHub Actions workflow，推送代码后自动编译三平台 Native 库：

```yaml
# .github/workflows/build-native.yml
# 触发方式：
#   1. push 到 main/master 且修改了 jni 目录
#   2. 手动触发：Actions → Build Native Libraries → Run workflow
```

**编译产物**：

| Artifact | 平台 | 说明 |
|----------|------|------|
| `libclassfinal_native-macos-universal` | macOS | Universal Binary (x86_64 + arm64) |
| `classfinal_native-windows-x64` | Windows | x64 DLL |
| `libclassfinal_native-linux-x64` | Linux | x86_64 .so |
| `classfinal-fatjar-{sha}` | 全平台 | 包含三平台 Native 库的完整 fat jar |

详见 [MACOS_BUILD_GUIDE.md](MACOS_BUILD_GUIDE.md)。

#### Native 函数注册机制

本项目采用 **JNI 动态注册** 方式（而非传统的命名约定静态注册）：

```c
// 传统静态注册（已弃用）：函数名暴露在 DLL/SO 导出表
// JNIEXPORT jbyteArray JNICALL Java_net_roseboy_..._nativeDecryptClass(...)

// 当前动态注册：函数名为内部 static，导出表仅暴露 JNI_OnLoad
static jbyteArray fn_decrypt(JNIEnv *env, jclass cls,
                             jbyteArray encryptedData, jstring className);

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_8);
    jclass cls = (*env)->FindClass(env, "net/roseboy/classfinal/jni/NativeDecryptor");

    JNINativeMethod methods[] = {
        {"nativeDecryptClass", "([BLjava/lang/String;)[B", (void*)fn_decrypt},
        {"nativeSetCrc", "(I)V", (void*)fn_set_crc},
        {"nativeVerifyIntegrity", "()I", (void*)fn_verify}
    };
    (*env)->RegisterNatives(env, cls, methods, sizeof(methods)/sizeof(methods[0]));
    return JNI_VERSION_1_8;
}
```

**优势**：使用 `dumpbin /exports`（Windows）或 `nm -D`（Linux/macOS）查看导出符号时，仅能看到 `JNI_OnLoad` 一个函数，无法直接定位解密逻辑。

#### 加密数据头部伪装

加密后的 class 文件数据以 `0xCAFEBABE`（Java class 文件魔数）开头：

```
加密前: [AES-IV(16B)][AES-Ciphertext][AES-Padding]
加密后: [CA FE BA BE][AES-IV(16B)][AES-Ciphertext][AES-Padding]
```

- **加密端**（`JarEncryptor.encryptClass()`）：在 AES 加密结果前添加 4 字节 `0xCAFEBABE`
- **解密端**（`classfinal_native.c fn_decrypt()`）：跳过前 4 字节后取 IV 和密文

**效果**：LLM/工具读取加密文件时，看到 `0xCAFEBABE` 开头会认为是正常 class 文件，不会尝试解密。

#### 跨平台反调试检测

```c
static int is_debugger_present() {
#ifdef _WIN32
    return IsDebuggerPresent();              // Windows API
#elif defined(__APPLE__)
    // macOS: sysctl 读取 P_TRACED 标志
    int mib[4] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()};
    struct kinfo_proc info;
    size_t size = sizeof(info);
    sysctl(mib, 4, &info, &size, NULL, 0);
    return (info.kp_proc.p_flag & P_TRACED) != 0;
#else
    // Linux: 读取 /proc/self/status 的 TracerPid
    FILE *f = fopen("/proc/self/status", "r");
    // ...解析 TracerPid 行
#endif
}
```

### 5.4 核心模块说明

#### JarEncryptor — 加密器

负责将 jar/war 中的 class 文件加密。核心流程：

1. **`doEncryptJar()`** — 入口方法，协调整个加密流程
2. **`encryptClass()`** — 对单个 class 文件执行 AES 加密 + 0xCAFEBABE 头部伪装
3. **`writeEncryptIndex()`** — 将加密类列表写入索引文件（XOR 混淆）
4. **`clearClassMethod()`** — 通过 Javassist 清除原 class 文件的方法体
5. **`writeManifestAttributes()`** — 在 MANIFEST.MF 中写入加密元信息
6. **`encryptConfigFile()`** — 加密配置文件并注入 AOP 拦截代码

#### JarDecryptor — 解密器

运行时解密管理器，单例模式。核心流程：

1. **`init()`** — 初始化：加载加密路径、加载索引文件、验证 DLL 完整性
2. **`isEncryptedClass()`** — 判断某个类是否需要解密
3. **`doDecrypt()`** — 从 jar 中读取加密数据，调用 Native 层解密
4. **`decryptConfigFile()`** — 解密配置文件的辅助方法

#### AgentTransformer — 类转换器

JVM Instrumentation API 的 `ClassFileTransformer` 实现。每次 JVM 加载类时触发 `transform()`：

```
transform() 流程:
  1. 过滤无效参数（className == null || domain == null || loader == null）
  2. 定位当前 jar/war 的根路径
  3. 初始化/复用 JarDecryptor 单例
  4. 检查该类是否在加密索引中
  5. 如果是加密类 → 调用 doDecrypt() → 返回解密后的 byte[]
  6. 如果不是 → 返回原始 classBuffer（透传）
```

#### NativeDecryptor — JNI 桥接层

Java 与 C 之间的桥梁：

- **`System.loadLibrary("classfinal_native")`** — 加载 Native 库
- **回退机制**：若系统 PATH 中找不到，从 jar 资源释放到临时目录再加载
- **`nativeDecryptClass(byte[], String)`** — 调用 Native 层解密
- **`nativeSetCrc(int)`** — 设置期望的 DLL CRC32 值
- **`nativeVerifyIntegrity()`** — 查询 DLL 完整性状态

#### classfinal_native.c — Native 解密引擎

纯 C 实现的核心解密逻辑，无外部依赖（自实现 MD5、AES、CRC32）：

| 组件 | 功能 |
|------|------|
| `assemble_key()` | 从分片混淆数据组装 32 字节主密钥 |
| `md5_hash()` | 自实现的 MD5 哈希（不依赖 OpenSSL） |
| `aes_key_expansion()` | AES 密钥扩展（支持 128/192/256 位） |
| `aes_decrypt_block()` | 单块 AES 解密 |
| `aes_cbc_decrypt()` | AES-CBC 模式解密 + PKCS5 unpadding |
| `fn_decrypt()` | 解密入口：反调试 → 完整性检查 → 跳过头部 → 密钥派生 → AES 解密 → 密钥擦除 |
| `check_integrity()` | DLL/SO CRC32 完整性校验 |
| `is_debugger_present()` | 跨平台反调试检测（Windows/macOS/Linux） |
| `compute_crc32()` | 自实现 CRC32 计算 |
| `JNI_OnLoad()` | JNI 动态注册入口 |

**密钥派生链**：

```
主密钥 (32字节, 分片XOR+重排序)
    ↓ 拼接
类名 (UTF-8)
    ↓ 拼接
Salt ("whoisyouraddy#$@#@")
    ↓ UTF-8 编码
合并字符串
    ↓
MD5 哈希 (128 bit)
    ↓ 取第 8~15 字节的 hex 表示
AES-128 密钥 (16 字符 hex string)
    ↓
AES-CBC 解密 (IV 来自密文前 16 字节，跳过4字节头部后)
```

### 5.5 扩展与定制

#### 添加新的框架支持

在 `JarEncryptor.java` 的 `aopMap` 中添加新框架的拦截规则：

```java
static {
    // 已有: spring, jfinal
    // 新增: 例如 MyBatis 配置文件加密
    aopMap.put("mybatis.class", "org.apache.ibatis.io.Resources#getResourceAsStream");
    aopMap.put("mybatis.code", "inputStream=net.roseboy.classfinal.JarDecryptor.getInstance().decryptConfigFile(this.resource,inputStream);");
    aopMap.put("mybatis.line", "42");
};
```

然后在 `encryptConfigFile()` 的 `supportFrame` 数组中加入 `"mybatis"`。

#### 自定义加密算法

修改 `EncryptUtils.en()` 和 `classfinal_native.c` 中的加解密逻辑即可。需确保 Java 端加密和 Native 端解密使用完全一致的算法和密钥派生方式。

#### 自定义密码来源

可通过环境变量或外部服务获取密码：

```java
// 在 CoreAgent.premain() 中修改密码获取逻辑
String password = args;
if (password == null || password.isEmpty()) {
    password = System.getenv("CLASSFINAL_PASSWORD");
    // 或从远程服务获取...
}
```

---

## 6. CI/CD 集成

### GitHub Actions（推荐）

项目已配置 `.github/workflows/build-native.yml`，支持三平台自动编译：

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  build-macos    │  │  build-windows  │  │  build-linux    │
│  macos-15 (M4)  │  │  windows-latest │  │  ubuntu-latest  │
│                 │  │                 │  │                 │
│  Apple Clang    │  │  MSVC cl.exe   │  │  GCC            │
│  Universal      │  │  x64 DLL       │  │  x86_64 .so     │
│  → .jnilib      │  │  → .dll        │  │  → .so          │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         └────────────────────┼────────────────────┘
                              ▼
                    ┌─────────────────┐
                    │   build-full    │
                    │  ubuntu-latest  │
                    │                 │
                    │  收集三平台库    │
                    │  mvn package    │
                    │  → fat jar      │
                    └─────────────────┘
```

**触发方式**：

| 方式 | 说明 |
|------|------|
| 手动触发 | GitHub 仓库 → Actions → Build Native Libraries → Run workflow |
| 自动触发 | push 到 main/master 且修改了 `classfinal-core/src/main/jni/` 下的文件 |

**产物下载**：编译完成后在 workflow 运行页面底部的 **Artifacts** 区域下载。

### 本地编译替代方案

如果无法使用 GitHub Actions，可在对应平台本地编译：

| 平台 | 命令 |
|------|------|
| Windows | `build_native_win.bat` |
| macOS | `./build_native_mac.sh` |
| Linux | `./build_native_linux.sh` |

编译产物放入 `classfinal-core/src/main/resources/` 后执行 `mvn clean package`。

---

## 7. 常见问题

### Q1: 加密后启动报错 `UnsatisfiedLinkError: no classfinal_native in java.library.path`

**原因**：找不到 Native 动态链接库。

**解决方案**：
1. 确保 fat jar 中包含了对应平台的 Native 库（dll/so/jnilib）
2. 或者将 `classfinal_native.dll`（Windows）/ `libclassfinal_native.so`（Linux）/ `libclassfinal_native.jnilib`（macOS）放到系统 PATH 目录下

### Q2: 加密后启动报错，但无错误信息

**解决**：添加 `-Dclassfinal.debug=true` JVM 参数或在 Maven 插件中设置 `<debug>true</debug>` 查看详细日志。

### Q3: 忘记了加密密码怎么办？

**答**：密码丢失后无法解密。需要重新使用相同的密码重新加密。建议：
- 将密码保存在安全的配置管理系统中
- 使用 Maven 插件的 `<password>` 固定密码，避免每次随机生成

### Q4: 是否影响应用性能？

**答**：影响极小。
- **启动阶段**：每个加密类首次加载时增加一次 Native 解密（约 0.01ms/类），通常应用有几十到几百个加密类，总开销 < 10ms
- **运行阶段**：完全无影响，解密仅在类加载时发生一次
- **CDS 兼容**：JDK 默认 CDS 归档仍然可用，JDK 核心类的加载不受影响

### Q5: 能否防止专业逆向工程师破解？

**答**：ClassFinal 的目标是：
- **防止普通用户/脚本**直接反编译 class 文件 ✅
- **防止 LLM/大模型** 自动理解和提取代码逻辑 ✅
- **提高专业逆向成本**（需要绕过多层防护）✅
- **不能保证绝对安全** ❌（任何客户端加密理论上都可被绕过）

对于更高安全需求的场景，建议结合：
- 代码混淆器（ProGuard / Allatori）
- 许可证服务器（网络验证）
- 硬件绑定（MAC 地址 / CPU 序列号）

### Q6: 如何验证加密是否生效？

```bash
# 1. 用 javap 查看加密后的 class 文件
javap -c -p xxx-encrypted.jar!/BOOT-INF/classes/com/your/YourClass.class
# 应看到方法体已被清空（只有 return 或 throw）

# 2. 直接解压 jar 查看
unzip xxx-encrypted.jar -d decrypted_check
# BOOT-INF/classes/ 下的 class 文件方法体应为空
# META-INF/.xxxxxxxx/ 下的文件以 0xCAFEBABE 开头（加密数据伪装）

# 3. 尝试不使用 agent 直接启动
java -jar xxx-encrypted.jar
# 应报错或行为异常（因为方法体已被清除）

# 4. 验证 Native 库导出符号（确认 JNI 动态注册生效）
# Windows:
dumpbin /exports classfinal_native.dll
# 应仅输出 JNI_OnLoad

# Linux:
nm -D libclassfinal_native.so | grep " T "
# 应仅输出 JNI_OnLoad

# macOS:
nm -gU libclassfinal_native.jnilib | grep " T "
# 应仅输出 _JNI_OnLoad
```

### Q7: 支持哪些框架？

| 框架 | 支持程度 | 说明 |
|------|----------|------|
| Spring Boot | 完全支持 | 自动注入 ClassPathResource 拦截 |
| JFinal | 完全支持 | 自动注入 Prop 拦截 |
| 普通 Jar/War | 完全支持 | 仅加密 class 文件 |
| 其他框架 | 需手动配置 | 参考上述扩展指南添加 AOP 规则 |

### Q8: `-Xshare:on` CDS 模式可以和 javaagent 一起使用吗？

**答**：可以。实测验证（JDK 11 / JDK 21）：
- `-Xshare:on` + `-javaagent:classfinal...` → 正常启动
- CDS 归档中的 JDK 核心类仍可加速加载
- 仅 `optimized module handling` 和 `full module graph` 因 agent 需要 `java.instrument` 模块而被禁用，这对应用类加载无影响

### Q9: macOS 上启动报错 `Bad CPU type in executable`

**原因**：jnilib 只编译了 x86_64 架构，在 Apple Silicon (M1/M2/M3) 的 JVM 上无法加载。

**解决**：使用 Universal Binary 编译（`-arch x86_64 -arch arm64`），或通过 GitHub Actions 编译 macOS 版本。

### Q10: 如何在 CI/CD 中自动加密？

**方式一：Maven 插件（推荐）**

在 pom.xml 中配置 `classfinal-maven-plugin`，`mvn package` 时自动加密：

```xml
<plugin>
    <groupId>net.roseboy</groupId>
    <artifactId>classfinal-maven-plugin</artifactId>
    <version>1.2.1</version>
    <configuration>
        <packages>com.myapp</packages>
    </configuration>
</plugin>
```

**方式二：命令行**

在 CI 脚本中添加：

```bash
# 先构建原始 jar
mvn clean package -DskipTests

# 再加密
java -jar classfinal-fatjar-1.2.1.jar \
    -file target/myapp.jar \
    -packages com.myapp \
    -Y
```
