package net.roseboy.classfinal.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class EncryptUtils {
    private static final byte[] OBFUSCATED_SALT = {
            0x4B, 0x54, 0x53, 0x55, 0x4F, 0x45, 0x53, 0x49,
            0x4E, 0x58, 0x5D, 0x58, 0x58, 0x45, 0x1F, 0x18,
            0x7C, 0x1F, 0x7C
    };
    private static final byte SALT_XOR = 0x3C;
    public static final char[] SALT;

    static {
        SALT = new char[OBFUSCATED_SALT.length];
        for (int i = 0; i < OBFUSCATED_SALT.length; i++) {
            SALT[i] = (char)(OBFUSCATED_SALT[i] ^ SALT_XOR);
        }
    }

    public static byte[] en(byte[] msg, char[] key, int type) {
        return enAES(msg, md5(StrUtils.merger(key, SALT), true));
    }

    public static byte[] de(byte[] msg, char[] key, int type) {
        return deAES(msg, md5(StrUtils.merger(key, SALT), true));
    }

    public static byte[] md5byte(char[] str) {
        byte[] b = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = StrUtils.toBytes(str);
            md.update(buffer);
            b = md.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return b;
    }

    public static char[] md5(char[] str) {
        return md5(str, false);
    }

    public static char[] md5(char[] str, boolean sh0rt) {
        byte s[] = md5byte(str);
        if (s == null) {
            return null;
        }
        int begin = 0;
        int end = s.length;
        if (sh0rt) {
            begin = 8;
            end = 16;
        }
        char[] result = new char[0];
        for (int i = begin; i < end; i++) {
            result = StrUtils.merger(result, Integer.toHexString((0x000000FF & s[i]) | 0xFFFFFF00).substring(6).toCharArray());
        }
        return result;
    }

    public static byte[] enAES(byte[] msg, char[] key) {
        try {
            byte[] raw = StrUtils.toBytes(key);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(msg);
            byte[] result = new byte[16 + encrypted.length];
            System.arraycopy(iv, 0, result, 0, 16);
            System.arraycopy(encrypted, 0, result, 16, encrypted.length);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] deAES(byte[] msg, char[] key) {
        try {
            if (msg == null || msg.length < 32) {
                return null;
            }
            byte[] raw = StrUtils.toBytes(key);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            byte[] iv = new byte[16];
            System.arraycopy(msg, 0, iv, 0, 16);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
            byte[] encrypted = new byte[msg.length - 16];
            System.arraycopy(msg, 16, encrypted, 0, encrypted.length);
            return cipher.doFinal(encrypted);
        } catch (Exception ex) {
        }
        return null;
    }
}
