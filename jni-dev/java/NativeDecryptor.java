package i.i.i;

import i.i.i.i.iili1111lliii1;

import java.io.File;
import java.io.InputStream;

public class NativeDecryptor {
    private static boolean loaded = false;

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
            File tempFile = new File(tempDir, libName);

            byte[] bytes = iili1111lliii1.toBytes(is);
            iili1111lliii1.close(is);

            if (tempFile.exists()) {
                tempFile.delete();
            }
            iili1111lliii1.writeFile(tempFile, bytes);
            tempFile.deleteOnExit();

            System.load(tempFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isNativeAvailable() {
        return loaded;
    }

    public static native byte[] nativeGetPwd();

    public static native char[] nativeDeriveKey();

    public static native byte[] nativeDecrypt(byte[] encryptedData, byte[] key);

    public static native byte[] nativeMd5(byte[] data);

    public static native byte[] nativeFullDecrypt(byte[] encryptedData, String fileName, char[] derivedKey);
}
