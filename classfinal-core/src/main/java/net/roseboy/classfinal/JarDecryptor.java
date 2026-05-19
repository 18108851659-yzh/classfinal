package net.roseboy.classfinal;

import net.roseboy.classfinal.jni.NativeDecryptor;
import net.roseboy.classfinal.util.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JarDecryptor {
    private static JarDecryptor instance = new JarDecryptor();

    private Set<String> encryptedClasses = new HashSet<>();
    private ConcurrentHashMap<String, JarFile> jarFileCache = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private String cachedProjectPath = null;
    private String encryptPath = null;

    private static final byte INDEX_XOR_KEY = (byte)0x5A;

    private JarDecryptor() {
    }

    public static JarDecryptor getInstance() {
        return instance;
    }

    public synchronized void init(String projectPath) {
        if (initialized && projectPath.equals(cachedProjectPath)) {
            return;
        }
        this.cachedProjectPath = projectPath;
        try {
            loadEncryptPath(projectPath);
            loadEncryptIndex(projectPath);
            verifyIntegrity(projectPath);
        } catch (Exception e) {
            Log.debug("init error: " + e.getMessage());
        }
        initialized = true;
    }

    private void loadEncryptPath(String projectPath) {
        byte[] manifestData = readRawFile(projectPath, "META-INF/MANIFEST.MF");
        if (manifestData == null) {
            encryptPath = ".classes";
            return;
        }
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(manifestData));
            String path = manifest.getMainAttributes().getValue("ClassFinal-Path");
            if (path != null && !path.isEmpty()) {
                encryptPath = path;
            } else {
                encryptPath = ".classes";
            }
        } catch (Exception e) {
            encryptPath = ".classes";
        }
        Log.debug("加密路径: META-INF/" + encryptPath);
    }

    private void loadEncryptIndex(String projectPath) {
        byte[] indexData = readRawFile(projectPath, "META-INF/" + encryptPath + "/" + Const.ENCRYPT_INDEX);
        if (indexData == null) {
            Log.debug("未找到加密索引文件，将使用逐类查找模式");
            return;
        }
        for (int i = 0; i < indexData.length; i++) {
            indexData[i] = (byte)(indexData[i] ^ INDEX_XOR_KEY);
        }
        String content = new String(indexData);
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                encryptedClasses.add(line);
            }
        }
        Log.debug("加载加密索引: " + encryptedClasses.size() + " 个类");
    }

    private void verifyIntegrity(String projectPath) {
        if (!NativeDecryptor.isNativeAvailable()) {
            return;
        }
        byte[] manifestData = readRawFile(projectPath, "META-INF/MANIFEST.MF");
        if (manifestData == null) {
            NativeDecryptor.nativeSetCrc(0);
            return;
        }
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(manifestData));
            String crcStr = manifest.getMainAttributes().getValue("ClassFinal-CRC");
            if (crcStr != null && !crcStr.isEmpty()) {
                int expectedCrc = Integer.parseInt(crcStr);
                String libPath = NativeDecryptor.getNativeLibPath();
                boolean verified = false;
                if (libPath != null) {
                    byte[] dllBytes = IoUtils.readFileToByte(new File(libPath));
                    if (dllBytes != null) {
                        int actualCrc = crc32(dllBytes);
                        verified = (actualCrc == expectedCrc);
                    }
                }
                NativeDecryptor.nativeSetCrc(verified ? expectedCrc : 0);
            } else {
                NativeDecryptor.nativeSetCrc(0);
            }
        } catch (Exception e) {
            NativeDecryptor.nativeSetCrc(0);
        }
        Log.debug("完整性校验完成");
    }

    private static int crc32(byte[] data) {
        int crc = 0xFFFFFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xEDB88320;
                } else {
                    crc = crc >>> 1;
                }
            }
        }
        return crc ^ 0xFFFFFFFF;
    }

    public boolean isEncryptedClass(String className) {
        if (encryptedClasses.isEmpty()) {
            return true;
        }
        return encryptedClasses.contains(className);
    }

    public byte[] doDecrypt(String projectPath, String className) {
        if (!isEncryptedClass(className)) {
            return null;
        }

        long t1 = System.currentTimeMillis();
        byte[] bytes = readEncryptedFile(projectPath, className);
        if (bytes == null) {
            return null;
        }

        if (NativeDecryptor.isNativeAvailable()) {
            byte[] nativeResult = NativeDecryptor.nativeDecryptClass(bytes, className);
            if (nativeResult != null && nativeResult.length > 4
                    && nativeResult[0] == -54 && nativeResult[1] == -2
                    && nativeResult[2] == -70 && nativeResult[3] == -66) {
                long t2 = System.currentTimeMillis();
                Log.debug("解密(native): " + className + " (" + (t2 - t1) + " ms)");
                return nativeResult;
            }
        }

        Log.debug("Native解密失败，类: " + className);
        return null;
    }

    public InputStream decryptConfigFile(String path, InputStream in) {
        try {
            byte[] bytes = IoUtils.toBytes(in);
            if (bytes == null || bytes.length == 0) {
                String projectPath = JarUtils.getRootPath(null);
                bytes = this.doDecrypt(projectPath, path);
            }
            return new ByteArrayInputStream(bytes);
        } catch (Exception e) {
            return in;
        }
    }

    private byte[] readEncryptedFile(String projectPath, String className) {
        String entryName = "META-INF/" + encryptPath + "/" + className;
        return readRawFile(projectPath, entryName);
    }

    private byte[] readRawFile(String projectPath, String entryName) {
        File workDir = new File(projectPath);
        if (workDir.isDirectory()) {
            File file = new File(workDir, entryName);
            if (file.exists()) {
                return IoUtils.readFileToByte(file);
            }
            return null;
        }

        if (workDir.isFile() && (projectPath.endsWith(".jar") || projectPath.endsWith(".war"))) {
            try {
                JarFile jarFile = jarFileCache.computeIfAbsent(projectPath, p -> {
                    try {
                        return new JarFile(new File(p));
                    } catch (Exception e) {
                        return null;
                    }
                });
                if (jarFile == null) {
                    return null;
                }
                JarEntry entry = jarFile.getJarEntry(entryName);
                if (entry == null) {
                    return null;
                }
                InputStream is = jarFile.getInputStream(entry);
                byte[] bytes = IoUtils.toBytes(is);
                IoUtils.close(is);
                return bytes;
            } catch (Exception e) {
                Log.debug("readEncryptedFile error: " + e.getMessage());
                return null;
            }
        }

        return null;
    }

    public void close() {
        for (JarFile jarFile : jarFileCache.values()) {
            try {
                jarFile.close();
            } catch (Exception ignored) {
            }
        }
        jarFileCache.clear();
    }

}
