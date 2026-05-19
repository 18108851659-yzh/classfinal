# ClassFinal JNI 开发维护指南

## 目录
1. [概述](#概述)
2. [架构设计](#架构设计)
3. [文件结构](#文件结构)
4. [编译指南](#编译指南)
5. [API 参考](#api-参考)
6. [安全机制](#安全机制)
7. [故障排查](#故障排查)
8. [维护注意事项](#维护注意事项)

---

## 概述

ClassFinal JNI 模块提供基于本地代码的加密解密能力，将敏感逻辑和密钥保护在编译后的二进制文件中，提高破解难度。

### 核心功能
- 密钥派生和管理（native 实现）
- AES/ECB 解密（native 实现）
- MD5 哈希计算（native 实现）
- 反调试检测
- 内存敏感数据自动清零

### 支持平台
| 平台 | 架构 | 产物文件 |
|------|------|---------|
| Windows | x86_64 | `classfinal_native.dll` |
| Linux | x86_64 | `libclassfinal_native.so` |
| macOS | x86_64 + arm64 (Universal) | `libclassfinal_native.jnilib` |

---

## 架构设计

### 调用流程

```
Java 层 (i.i.i.NativeDecryptor)
    ↓ JNI 调用
Native 层 (classfinal_native.c)
    ├─ 反调试检测
    ├─ 密钥派生 (nativeDeriveKey)
    ├─ 完整解密 (nativeFullDecrypt)
    │   ├─ 密钥混淆还原
    │   ├─ 文件名拼接
    │   ├─ 加盐 MD5
    │   └─ AES 解密
    └─ 内存清零
```

### 安全设计

1. **密钥混淆存储**：密码和盐值使用 XOR 混淆存储
2. **运行时解密**：密钥在运行时动态还原
3. **内存保护**：敏感数据使用后立即清零
4. **反调试**：检测调试器附加

---

## 文件结构

```
classfinal-core/
├── src/
│   ├── main/
│   │   ├── java/i/i/i/
│   │   │   └── NativeDecryptor.java    # JNI 接口类
│   │   ├── jni/
│   │   │   ├── i_i_i_NativeDecryptor.h # JNI 头文件（自动生成）
│   │   │   └── classfinal_native.c     # Native 实现
│   │   └── resources/
│   │       ├── classfinal_native.dll   # Windows 库
│   │       ├── libclassfinal_native.so # Linux 库
│   │       └── libclassfinal_native.jnilib # macOS 库
│   └── test/
├── build_native_win.bat                  # Windows 编译脚本
├── build_native_linux.sh                 # Linux 编译脚本
└── build_native_mac.sh                   # macOS 编译脚本
```

---

## 编译指南

### 前置条件

#### Windows
- Visual Studio 2022/2019（安装 C++ 桌面开发工作负载）
- Windows 11 SDK 或 Windows 10 SDK
- JDK 11+

#### Linux
- GCC 7+
- OpenJDK 11+
- make

#### macOS
- Xcode Command Line Tools
- JDK 11+

### 编译步骤

#### 1. 生成 JNI 头文件（当 Java 接口变更时）

```bash
cd classfinal-core
javac -h src/main/jni -cp target/classes src/main/java/i/i/i/NativeDecryptor.java
```

#### 2. Windows 编译

```batch
cd classfinal-core
build_native_win.bat
```

**手动编译（如脚本失败）：**
```batch
cl /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
   /I"D:\Windows Kits\10\Include\10.0.26100.0\ucrt" ^
   /I"D:\Windows Kits\10\Include\10.0.26100.0\um" ^
   /LD /O2 /MD ^
   src\main\jni\classfinal_native.c ^
   /Fe:src\main\resources\classfinal_native.dll ^
   /link ^
   /LIBPATH:"D:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.35.32215\lib\x64" ^
   /LIBPATH:"D:\Windows Kits\10\Lib\10.0.26100.0\um\x64" ^
   /LIBPATH:"D:\Windows Kits\10\Lib\10.0.26100.0\ucrt\x64"
```

#### 3. Linux 编译

```bash
cd classfinal-core
chmod +x build_native_linux.sh
./build_native_linux.sh
```

#### 4. macOS 编译（Universal Binary）

```bash
cd classfinal-core
chmod +x build_native_mac.sh
./build_native_mac.sh
```

**验证架构支持：**
```bash
lipo -info src/main/resources/libclassfinal_native.jnilib
# 应输出：Architectures in the fat file: x86_64 arm64
```

### 编译产物验证

```bash
# Windows
dumpbin /exports src/main/resources/classfinal_native.dll

# Linux
nm -D src/main/resources/libclassfinal_native.so | grep Java_

# macOS
nm src/main/resources/libclassfinal_native.jnilib | grep Java_
```

---

## API 参考

### Java 层接口

```java
public class NativeDecryptor {
    // 检查 native 库是否可用
    public static boolean isNativeAvailable()
    
    // 获取混淆后的密码（字节数组）
    public static native byte[] nativeGetPwd()
    
    // 派生密钥（20字符 char 数组）
    public static native char[] nativeDeriveKey()
    
    // 解密数据（通用接口）
    public static native byte[] nativeDecrypt(byte[] encryptedData, byte[] key)
    
    // MD5 哈希
    public static native byte[] nativeMd5(byte[] data)
    
    // 完整解密流程（密码+文件名+盐→AES解密）
    public static native byte[] nativeFullDecrypt(
        byte[] encryptedData, 
        String fileName, 
        char[] derivedKey
    )
}
```

### Native 函数签名

```c
// 头文件：i_i_i_NativeDecryptor.h

JNIEXPORT jbyteArray JNICALL Java_i_i_i_NativeDecryptor_nativeGetPwd
  (JNIEnv *, jclass);

JNIEXPORT jcharArray JNICALL Java_i_i_i_NativeDecryptor_nativeDeriveKey
  (JNIEnv *, jclass);

JNIEXPORT jbyteArray JNICALL Java_i_i_i_NativeDecryptor_nativeDecrypt
  (JNIEnv *, jclass, jbyteArray, jbyteArray);

JNIEXPORT jbyteArray JNICALL Java_i_i_i_NativeDecryptor_nativeMd5
  (JNIEnv *, jclass, jbyteArray);

JNIEXPORT jbyteArray JNICALL Java_i_i_i_NativeDecryptor_nativeFullDecrypt
  (JNIEnv *, jclass, jbyteArray, jstring, jcharArray);
```

---

## 安全机制

### 1. 密钥混淆

```c
// 密码使用 XOR 0x5A 混淆存储
static const unsigned char OBFUSCATED_PWD[] = {
    'h' ^ 0x5A, 'e' ^ 0x5A, 'l' ^ 0x5A, 'l' ^ 0x5A,
    'o' ^ 0x5A, 'b' ^ 0x5A, 'a' ^ 0x5A, 'b' ^ 0x5A,
    'y' ^ 0x5A
};

// 运行时还原
for (int i = 0; i < PWD_LEN; i++) {
    pwd[i] = OBFUSCATED_PWD[i] ^ PWD_XOR_KEY;
}
```

### 2. 反调试检测

```c
static int is_debugger_present() {
#ifdef _WIN32
    return IsDebuggerPresent();
#else
    // Linux/macOS: 检查 /proc/self/status 中的 TracerPid
    FILE *f = fopen("/proc/self/status", "r");
    // ... 解析 TracerPid
#endif
}
```

### 3. 内存清零

```c
// 所有敏感数据使用后立即清零
memset(pass, 0, passTotalLen * 2);
memset(salted, 0, saltedLen);
memset(salt, 0, SALT_LEN);
memset(md5_result, 0, MD5_DIGEST_LENGTH);
memset(hex_md5, 0, 33);
```

### 4. 解密流程

```
encryptedData + fileName + derivedKey
    ↓
[密钥替换] key[0] = key[1]
    ↓
[拼接] pass = derivedKey + fileName
    ↓
[加盐] salted = pass + SALT
    ↓
[MD5] md5(salted)
    ↓
[取短] hex(md5[8:16])
    ↓
[AES解密] AES/ECB/PKCS5Padding
    ↓
decryptedData
```

---

## 故障排查

### 问题 1：UnsatisfiedLinkError: no classfinal_native in java.library.path

**原因**：Native 库未找到

**解决**：
1. 检查 `src/main/resources/` 下是否存在对应平台的库文件
2. 确认打包时库文件被包含进 jar
3. 检查 `NativeDecryptor.loadFromJar()` 逻辑

### 问题 2：编译错误 C2057: 应输入常量表达式

**原因**：使用 `const int` 定义数组大小

**解决**：改为 `#define`
```c
// 错误
static const int SALT_LEN = 19;
char salt[SALT_LEN];  // C2057

// 正确
#define SALT_LEN 19
char salt[SALT_LEN];
```

### 问题 3：LINK : fatal error LNK1104: 无法打开文件"uuid.lib"

**原因**：缺少库文件路径

**解决**：添加 Windows SDK 库路径
```batch
/link /LIBPATH:"C:\Program Files (x86)\Windows Kits\10\Lib\10.0.26100.0\um\x64"
```

### 问题 4：macOS 上加载失败

**原因**：架构不匹配（Intel vs Apple Silicon）

**解决**：编译 Universal Binary
```bash
clang -arch x86_64 -arch arm64 -shared ...
```

---

## 维护注意事项

### 修改 Java 接口时

1. **重新生成头文件**：
   ```bash
   javac -h src/main/jni -cp target/classes src/main/java/i/i/i/NativeDecryptor.java
   ```

2. **同步修改 C 实现**：确保函数签名与头文件一致

3. **全平台重新编译**：Windows、Linux、macOS 都要重新编译

### 修改密钥时

1. 更新 `OBFUSCATED_PWD` 数组
2. 使用 XOR 混淆：
   ```java
   char[] pwd = "newpassword".toCharArray();
   for (char c : pwd) {
       System.out.printf("0x%02X, ", c ^ 0x5A);
   }
   ```

### 版本发布 checklist

- [ ] Windows DLL 已编译（x64）
- [ ] Linux SO 已编译（x64）
- [ ] macOS JNILIB 已编译（Universal Binary）
- [ ] 所有库文件已放入 `src/main/resources/`
- [ ] Maven 打包测试通过
- [ ] 加密/解密功能测试通过
- [ ] 跨平台启动测试通过

### 安全增强建议

1. **定期更换混淆密钥**
2. **添加代码完整性校验**
3. **使用 LLVM Obfuscator 编译**
4. **考虑商业保护方案（VMProtect）**

---

## 附录

### 相关文件链接

- [NativeDecryptor.java](classfinal-core/src/main/java/i/i/i/NativeDecryptor.java)
- [classfinal_native.c](classfinal-core/src/main/jni/classfinal_native.c)
- [i_i_i_NativeDecryptor.h](classfinal-core/src/main/jni/i_i_i_NativeDecryptor.h)

### 参考文档

- [JNI Specification](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/)
- [Java Cryptography Architecture](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html)

---

**文档版本**：1.0  
**最后更新**：2026-04-23  
**维护者**：ClassFinal Team
