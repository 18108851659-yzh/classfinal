# ClassFinal JNI Native 方案实施文档

## 一、JNI 迁移工作成果

### 1.1 完成的工作

| 步骤 | 内容 | 状态 |
|------|------|------|
| 1 | 重写 `nativeFullDecrypt`，算法与 Java `EncryptUtils.de()` 完全一致 | ✅ |
| 2 | 修复 AES 解密实现（InvMixColumns + 逆向 S-box + 正确操作顺序） | ✅ |
| 3 | 生成 JNI 头文件 `net_roseboy_classfinal_jni_NativeDecryptor.h` | ✅ |
| 4 | 修复打包问题：`CLASSFINAL_FILES` 添加 native 库文件 | ✅ |
| 5 | 修复 `loadFromJar()` 并发安全（进程ID命名临时文件） | ✅ |
| 6 | 修复 macOS 编译脚本 Universal Binary | ✅ |
| 7 | 编译 Windows native 库（新包名，导出函数名正确） | ✅ |
| 8 | 5 项测试全部通过：MD5匹配 + AES加解密一致性 | ✅ |

### 1.2 修复的关键问题

| 问题 | 原因 | 修复方式 |
|------|------|----------|
| 算法不匹配 | Native密钥派生与Java加密端完全不同 | 重写nativeFullDecrypt精确复刻EncryptUtils.de() |
| AES解密实现错误 | 缺少InvMixColumns、使用正向S-box、操作顺序错误 | 按FIPS 197标准重写AES解密 |
| DLL基于旧包名 | 导出函数名为Java_i_i_i_* | 重新编译，导出Java_net_roseboy_classfinal_jni_* |
| 缺少JNI头文件 | C源码include的.h不存在 | javac -h生成 |
| 打包缺失native库 | CLASSFINAL_FILES不含.dll/.so/.jnilib | 添加到数组 |
| loadFromJar并发不安全 | 临时文件名固定 | 使用进程ID命名 |
| macOS非Universal Binary | 编译脚本缺少-arch参数 | 添加-arch x86_64 -arch arm64 |

### 1.3 文件清单

| 文件 | 变更类型 |
|------|----------|
| `src/main/java/net/roseboy/classfinal/jni/NativeDecryptor.java` | 新增 |
| `src/main/jni/classfinal_native.c` | 新增 |
| `src/main/jni/net_roseboy_classfinal_jni_NativeDecryptor.h` | 新增 |
| `src/main/resources/classfinal_native.dll` | 新增 |
| `src/main/java/net/roseboy/classfinal/JarDecryptor.java` | 修改 |
| `src/main/java/net/roseboy/classfinal/Const.java` | 修改 |
| `build_native_win.bat` / `build_native_linux.sh` / `build_native_mac.sh` | 复制+修改 |

---

## 二、混淆机制分析

### 2.1 当前混淆手段

| 手段 | 实现方式 | 安全等级 |
|------|----------|----------|
| 方法体清空 | javassist清空原始class方法体 | 中 |
| AES加密 | AES/ECB/PKCS5Padding，密钥=md5(pwd+className+SALT) | 中 |
| 配置文件伪装 | org.springframework.config.Pass等Spring风格命名 | 低 |
| 密码char数组编码 | {112,97,115,115}形式注入Spring代码 | 低 |
| Native反调试 | IsDebuggerPresent() / /proc/self/status | 中 |
| Native盐值混淆 | XOR 0x3C存储 | 低 |
| Native内存清零 | memset(buf, 0, len) | 中 |

### 2.2 安全薄弱环节

1. 无密码模式明文存储：CONFIG_PASS文件存储随机密码原文
2. AOP注入密码可反编译：密码以char数组字面量硬编码在Spring类中
3. 密码Hash文件可被利用：CONFIG_PASSHASH暴露密码验证逻辑
4. Java解密作为回退：攻击者可使Native加载失败绕过反调试
5. AES ECB模式：相同明文产生相同密文
6. SALT硬编码在Java层：whoisyourdaddy#$@#@完全暴露

### 2.3 优化建议

| 优化项 | 建议 | 安全提升 |
|--------|------|----------|
| 密钥管理 | 密钥硬编码在native中，JAR内不存储任何密钥 | 🔴→🟢 |
| 密码验证 | 消除hash文件，native内部验证 | 🔴→🟢 |
| Java回退 | 仅使用Native解密，移除Java回退 | 🔴→🟢 |
| 配置文件密码 | 配置文件也使用native硬编码密钥解密 | 🔴→🟡 |

---

## 三、密钥硬编码方案

### 3.1 设计思路

加密时使用固定密钥，解密时密钥硬编码在native C代码中（XOR混淆存储），Java层不再传递任何密码。

```
加密端（JarEncryptor）：
  固定密钥 KEY → md5(KEY + className + SALT, short=true) → AES加密

解密端（Native）：
  硬编码密钥 KEY（XOR混淆）→ md5(KEY + className + SALT, short=true) → AES解密
```

### 3.2 密钥

固定密钥硬编码在native C代码中，通过XOR混淆存储，运行时解混淆使用。

### 3.3 消除项

- CONFIG_PASS 文件（密码明文）
- CONFIG_PASSHASH 文件（密码hash）
- CONFIG_CODE 文件（机器码hash）
- 密码获取流程（命令行、环境变量、文件、控制台、GUI）
- 密码验证逻辑
- Java解密回退路径
