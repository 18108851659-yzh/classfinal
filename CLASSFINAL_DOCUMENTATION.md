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
- [6. 常见问题](#6-常见问题)

---

## 1. 概述

ClassFinal 是一款轻量级的 Java class 文件加密工具，主要特性：

| 特性 | 说明 |
|------|------|
| **零侵入** | 无需修改应用源码，通过 Maven 插件或命令行一键加密 |
| **按需解密** | 运行时仅在类加载时解密到内存，不落盘 |
| **Native 解密** | 核心解密逻辑在 C/C++ Native 层执行，增加逆向难度 |
| **JDK CDS 兼容** | 支持 `-Xshare:auto` / `-Xshare:on`，不影响启动性能 |
| **多框架支持** | 原生支持 Spring Boot、JFinal，可扩展其他框架 |
| **配置文件加密** | 支持对 application.yml 等配置文件进行加密 |
| **方法体清除** | 加密后原 class 文件的方法体被清空，双重保护 |

### 适用场景

- 保护 Spring Boot 微服务的核心业务逻辑
- 防止 SaaS 产品被逆向分析
- 保护包含敏感算法或授权逻辑的 Java 应用
- 防止 LLM/大模型自动读取和理解代码逻辑

### 版本兼容性

| JDK | 兼容性 |
|-----|--------|
| JDK 8 | 完全支持 |
| JDK 11 | 完全支持 + CDS 可用 |
| JDK 17+ | 完全支持 + CDS 可用 |
| JDK 21 | 完全支持 + CDS 可用 |

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
│   └── src/main/resources/
│       └── classfinal_native.dll   # 编译好的 Native 库（Windows）
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
     │     ├─ 添加 0xCAFEBABE 头部伪装
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
java -javaagent:classfinal-fatjar-1.2.1.jar="-pwd 密码" -jar xxx-encrypted.jar
     │
     ▼
JVM 启动 → Agent.premain() → CoreAgent.premain()
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
     │     ▼ (Native 层)
     │     ├─ 反调试检测
     │     ├─ DLL 完整性校验（CRC32）
     │     ├─ 组装密钥（分片 XOR + 重排序）
     │     ├─ 密钥派生（key + className + salt → MD5 → 取后16字节hex）
     │     ├─ 跳过 4 字节头部伪装
     │     ├─ AES-CBC 解密
     │     └─ 返回明文 class 字节码
     │
     ▼
返回 byte[] 给 JVM → 正常加载类
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
| **JNI 动态注册** | `classfinal_native.c` - `JNI_OnLoad` | DLL 导出表仅暴露 `JNI_OnLoad`，隐藏解密函数名 |
| **头部伪装** | `JarEncryptor` + `classfinal_native.c` | 加密数据以 `0xCAFEBABE` 开头，看起来像普通 class 文件 |
| **密钥分片混淆** | `classfinal_native.c` - `assemble_key()` | 主密钥分为两段，各自 XOR 混淆后再重排序组合 |
| **DLL CRC32 校验** | `JarEncryptor` + `JarDecryptor` + `classfinal_native.c` | 运行时校验 Native 库是否被篡改 |
| **反调试检测** | `classfinal_native.c` - `is_debugger_present()` | Windows 检测 `IsDebuggerPresent()`，Linux 检测 `/proc/self/status` 的 TracerPid |
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

**步骤 3：查看生成的密码**

Maven 插件会在控制台输出类似以下信息：

```
=========================================================
=                                                       =
=      Java Class Encryption Tool v1.2.1   by Mr.K      =
=                                                       =
=========================================================
加密类型：jar
待加密: com.yourcompany.controller.HelloController
待加密: com.yourcompany.service.UserService
...
加密完成！共加密 15 个类
密码: a3k9f2m7b1c4e8d5
请使用以下命令运行加密后的jar：
java -javaagent:classfinal-fatjar-1.2.1.jar="-pwd a3k9f2m7b1c4e8d5" -jar xxx-encrypted.jar
```

### 3.2 命令行方式

```bash
# 交互模式（逐步引导输入参数）
java -jar classfinal-fatjar-1.2.1.jar

# 或直接指定参数
java -jar classfinal-fatjar-1.2.1.jar \
    -file your-app.jar \
    -packages com.yourcompany \
    -pwd yourpassword \
    -Y
```

### 3.3 启动加密后的应用

```bash
# 基本启动
java -javaagent:classfinal-fatjar-1.2.1.jar="-pwd 你的密码" -jar xxx-encrypted.jar

# 带 CDS 加速（推荐，JDK 类加载加速约 20%-40%）
java -Xshare:auto -javaagent:classfinal-fatjar-1.2.1.jar="-pwd 你的密码" -jar xxx-encrypted.jar

# 强制 CDS 模式（CDS 不可用时拒绝启动）
java -Xshare:on -javaagent:classfinal-fatjar-1.2.1.jar="-pwd 你的密码" -jar xxx-encrypted.jar

# 带 JVM 参数的完整示例
java -Xshare:auto -Xms512m -Xmx1024m \
    -javaagent:classfinal-fatjar-1.2.1.jar="-pwd 你的密码" \
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

| 参数 | 缩写 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `--file` | `-f` | **是** | 无 | 要加密的 jar/war 文件路径 |
| `--packages` | `-p` | **是** | 无 | 要加密的包名前缀 |
| `--password` | `-pwd` | 否 | 随机生成 | 加密密码 |
| `--libjars` | `-l` | 否 | 空 | 内部 lib/jar 名称 |
| `--exclude` | `-e` | 否 | 空 | 排除的类名 |
| `--classpath` | `-cp` | 否 | 空 | 外部依赖路径 |
| `--cfgfiles` | `-c` | 否 | 空 | 配置文件名 |
| `--yes` | `-Y` | 否 | false | 跳过确认提示，直接执行加密 |
| `--debug` | 否 | false | 输出调试日志 |

```bash
# 命令行完整示例
java -jar classfinal-fatjar-1.2.1.jar \
    -file myapp.jar \
    -packages com.myapp \
    -exclude "com.myapp.config.*" \
    -libjars my-sdk.jar \
    -cfgfiles application.yml \
    -pwd mySecretPassword \
    -Y
```

### 4.3 启动参数

| 参数 | 格式 | 说明 |
|------|------|------|
| `password` | `-pwd 密码` | 加密时使用的密码，必须一致才能正确解密 |

```bash
-javaagent:classfinal-fatjar-1.2.1.jar="-pwd 你的密码"
```

---

## 5. 开发指南

### 5.1 环境要求

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 8+ | Java 编译运行 |
| Maven | 3.x | 项目构建 |
| MSVC | 2019+ (x64) | Native DLL 编译（Windows） |
| GCC / MinGW | x86_64-w64-mingw32-gcc | Native SO/Dylib 编译（Linux/Mac） |

### 5.2 编译构建

```bash
# 克隆项目
git clone https://gitee.com/roseboy/classfinal.git
cd classfinal

# 全量构建（含 Native DLL）
mvn clean package -DskipTests

# 构建产物位置：
# classfinal-core/target/classfinal-core-1.2.1.jar        -- 核心 JAR
# classfinal-fatjar/target/classfinal-fatjar-1.2.1.jar      -- Fat JAR（Agent + CLI）
# classfinal-maven-plugin/target/classfinal-maven-plugin-1.2.1.jar  -- Maven 插件
```

### 5.3 Native 层开发

#### 目录结构

```
classfinal-core/src/main/jni/
├── classfinal_native.c          # C 语言源码（核心解密逻辑）
└── build_native_win.bat          # Windows 编译脚本
```

#### 编译 Native DLL（Windows）

编译脚本位于 `classfinal-core/build_native_win.bat`，内部调用 MSVC cl.exe：

```bat
@echo off
call "D:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvarsall.bat" x86_amd64
set JAVA_HOME=D:\env\jdk\openjdk-21_windows-x64_bin\jdk-21
set INCLUDE=%JAVA_HOME%\include;%JAVA_HOME%\include\win32;..\jni;%INCLUDE%
cl.exe /LD /O2 "classfinal_native.c" /Fe:"../resources/classfinal_native.dll" /link
```

**关键点**：
- 必须使用 **x64** 架幅编译（`x86_amd64`），32位 DLL 无法在 64 位 JVM 上加载
- `JAVA_HOME` 需指向包含 `include/jni.h` 的 JDK 安装目录
- 编译产物输出到 `src/main/resources/classfinal_native.dll`，会被 maven-shade-plugin 打入 fat jar

#### Linux/Mac 编译

```bash
# Linux (GCC)
gcc -shared -fPIC -O2 -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux \
    classfinal_native.c -o classfinal_native.so

# Mac (Clang)
gcc -shared -fPIC -O2 -I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin \
    classfinal_native.c -o libclassfinal_native.jnilib
```

#### Native 函数注册机制

本项目采用 **JNI 动态注册** 方式（而非传统的命名约定静态注册）：

```c
// 传统静态注册（已弃用）：函数名暴露在 DLL 导出表
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

**优势**：使用 `dumpbin /exports` 查看 DLL 时，仅能看到 `JNI_OnLoad` 一个导出函数，无法直接定位解密逻辑。

### 5.4 核心模块说明

#### JarEncryptor — 加密器

负责将 jar/war 中的 class 文件加密。核心流程：

1. **`doEncryptJar()`** — 入口方法，协调整个加密流程
2. **`encryptClass()`** — 对单个 class 文件执行 AES 加密 + 头部伪装
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

纯 C 实现的核心解密逻辑，包含：

| 组件 | 功能 |
|------|------|
| `assemble_key()` | 从分片混淆数据组装 32 字节主密钥 |
| `md5_hash()` | 自实现的 MD5 哈希（不依赖 OpenSSL） |
| `aes_key_expansion()` | AES 密钥扩展（支持 128/192/256 位） |
| `aes_decrypt_block()` | 单块 AES 解密 |
| `aes_cbc_decrypt()` | AES-CBC 模式解密 + PKCS5 unpadding |
| `fn_decrypt()` | 解密入口：反调试 → 完整性检查 → 密钥派生 → AES 解密 |
| `check_integrity()` | DLL CRC32 完整性校验 |
| `is_debugger_present()` | 跨平台反调试检测 |
| `compute_crc32()` | 自实现 CRC32 计算 |

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
AES-CBC 解密 (IV 来自密文前 16 字节)
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

## 6. 常见问题

### Q1: 加密后启动报错 `UnsatisfiedLinkError: no classfinal_native in java.library.path`

**原因**：找不到 Native 动态链接库。

**解决方案**：
1. 确保 fat jar 中包含了对应平台的 Native 库（dll/so/jnilib）
2. 或者将 `classfinal_native.dll`（Windows）/ `libclassfinal_native.so`（Linux）放到系统 PATH 目录下

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
# META-INF/.xxxxxxxx/ 下的文件以 0xCAFEBABE 开头（加密数据）

# 3. 尝试不使用 agent 直接启动
java -jar xxx-encrypted.jar
# 应报错或行为异常（因为方法体已被清除）
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
