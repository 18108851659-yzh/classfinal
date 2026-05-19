package net.roseboy.classfinal.jni;

import net.roseboy.classfinal.util.IoUtils;

import java.io.File;
import java.io.InputStream;

public class NativeDecryptor {
    private static boolean loaded = false;
    private static String nativeLibPath = null;

    static {
        try {
            System.loadLibrary("classfinal_native");
            loaded = true;
        } catch (UnsatisfiedLinkError e1) {
            try {
                loaded = loadFromJar();
            } catch (Exception e2) {
                loaded = false;
            }
        }
    }

    private static boolean loadFromJar() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String libName;
        if (osName.contains("windows")) {
            libName = "classfinal_native.dll";
        } else if (osName.contains("mac")) {
            libName = "libclassfinal_native.jnilib";
        } else {
            libName = "libclassfinal_native.so";
        }

        try {
            InputStream is = NativeDecryptor.class.getResourceAsStream("/" + libName);
            if (is == null) return false;

            File tempDir = new File(System.getProperty("java.io.tmpdir"), "classfinal");
            if (!tempDir.exists()) tempDir.mkdirs();

            String uniqueName = libName + "." + ProcessHandle.current().pid();
            File tempFile = new File(tempDir, uniqueName);

            byte[] bytes = IoUtils.toBytes(is);
            IoUtils.close(is);

            if (!tempFile.exists() || tempFile.length() != bytes.length) {
                IoUtils.writeFile(tempFile, bytes);
            }
            tempFile.deleteOnExit();

            nativeLibPath = tempFile.getAbsolutePath();
            System.load(nativeLibPath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isNativeAvailable() {
        return loaded;
    }

    public static String getNativeLibPath() {
        return nativeLibPath;
    }

    public static native byte[] nativeDecryptClass(byte[] encryptedData, String className);

    public static native void nativeSetCrc(int crc);

    public static native int nativeVerifyIntegrity();
}
