package net.roseboy.classfinal.jni;

import net.roseboy.classfinal.JarEncryptor;
import net.roseboy.classfinal.util.EncryptUtils;
import net.roseboy.classfinal.util.StrUtils;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class NativeDecryptorTest {

    @Test
    public void testNativeDecryptClassMatchesJavaEncrypt() {
        if (!NativeDecryptor.isNativeAvailable()) {
            System.out.println("SKIP: Native library not available");
            return;
        }

        char[] key = JarEncryptor.ENCRYPT_KEY;
        String className = "com.example.MyClass";

        byte[] originalData = new byte[256];
        for (int i = 0; i < 256; i++) {
            originalData[i] = (byte) i;
        }

        char[] pass = StrUtils.merger(key, className.toCharArray());
        byte[] encryptedData = EncryptUtils.en(originalData, pass, 1);
        assertNotNull("Java encryption should succeed", encryptedData);

        byte[] nativeResult = NativeDecryptor.nativeDecryptClass(encryptedData, className);
        assertNotNull("Native decryption should succeed", nativeResult);

        assertArrayEquals("Native decrypt must match original data", originalData, nativeResult);
        System.out.println("PASS: Native decrypt matches Java encrypt with fixed key");
    }

    @Test
    public void testNativeDecryptWithShortClassName() {
        if (!NativeDecryptor.isNativeAvailable()) {
            System.out.println("SKIP: Native library not available");
            return;
        }

        char[] key = JarEncryptor.ENCRYPT_KEY;
        String className = "a.b.C";

        byte[] originalData = "Hello World!".getBytes();

        char[] pass = StrUtils.merger(key, className.toCharArray());
        byte[] encryptedData = EncryptUtils.en(originalData, pass, 1);
        assertNotNull(encryptedData);

        byte[] nativeResult = NativeDecryptor.nativeDecryptClass(encryptedData, className);
        assertNotNull(nativeResult);

        assertArrayEquals(originalData, nativeResult);
        System.out.println("PASS: Native decrypt works with short class name");
    }

    @Test
    public void testNativeDecryptWithLongClassName() {
        if (!NativeDecryptor.isNativeAvailable()) {
            System.out.println("SKIP: Native library not available");
            return;
        }

        char[] key = JarEncryptor.ENCRYPT_KEY;
        String className = "com.test.demo.controller.HelloController";

        byte[] originalData = new byte[1024];
        for (int i = 0; i < 1024; i++) {
            originalData[i] = (byte) (i % 127 + 1);
        }

        char[] pass = StrUtils.merger(key, className.toCharArray());
        byte[] encryptedData = EncryptUtils.en(originalData, pass, 1);
        assertNotNull(encryptedData);

        byte[] nativeResult = NativeDecryptor.nativeDecryptClass(encryptedData, className);
        assertNotNull(nativeResult);

        assertArrayEquals(originalData, nativeResult);
        System.out.println("PASS: Native decrypt works with long class name");
    }

    @Test
    public void testJavaDecryptStillWorksWithFixedKey() {
        char[] key = JarEncryptor.ENCRYPT_KEY;
        String className = "com.example.MyClass";

        byte[] originalData = "Test data for Java decrypt".getBytes();

        char[] pass = StrUtils.merger(key, className.toCharArray());
        byte[] encryptedData = EncryptUtils.en(originalData, pass, 1);
        assertNotNull(encryptedData);

        byte[] javaResult = EncryptUtils.de(encryptedData, pass, 1);
        assertNotNull(javaResult);

        assertArrayEquals(originalData, javaResult);
        System.out.println("PASS: Java decrypt still works correctly with fixed key");
    }

    @Test
    public void testNativeAndJavaDecryptProduceSameResult() {
        if (!NativeDecryptor.isNativeAvailable()) {
            System.out.println("SKIP: Native library not available");
            return;
        }

        char[] key = JarEncryptor.ENCRYPT_KEY;
        String className = "net.test.DemoService";

        byte[] originalData = new byte[512];
        for (int i = 0; i < 512; i++) {
            originalData[i] = (byte) (i % 256);
        }

        char[] pass = StrUtils.merger(key, className.toCharArray());
        byte[] encryptedData = EncryptUtils.en(originalData, pass, 1);

        byte[] javaResult = EncryptUtils.de(encryptedData, pass, 1);
        byte[] nativeResult = NativeDecryptor.nativeDecryptClass(encryptedData, className);

        assertNotNull(javaResult);
        assertNotNull(nativeResult);
        assertArrayEquals("Native and Java decrypt must produce identical results", javaResult, nativeResult);
        System.out.println("PASS: Native and Java decrypt produce identical results");
    }
}
