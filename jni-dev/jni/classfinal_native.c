#include "i_i_i_NativeDecryptor.h"
#include <string.h>
#include <stdlib.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#endif

#define AES_BLOCK_SIZE 16
#define MD5_DIGEST_LENGTH 16

static const unsigned char SECRET_A[] = {0xE8, 0x82, 0xEE, 0x62, 0xE2, 0xD6, 0x98, 0x9A};
static const int SECRET_A_LEN = 8;

static const unsigned char SECRET_B[] = {0x8A, 0x98, 0xAC, 0xB2, 0xAA, 0x6E, 0x64, 0xA2, 0xDA, 0xDA, 0x82, 0xF2};
static const int SECRET_B_LEN = 12;

static const unsigned char OBFUSCATED_PWD[] = {
    'h' ^ 0x5A, 'e' ^ 0x5A, 'l' ^ 0x5A, 'l' ^ 0x5A,
    'o' ^ 0x5A, 'b' ^ 0x5A, 'a' ^ 0x5A, 'b' ^ 0x5A,
    'y' ^ 0x5A
};
static const int PWD_LEN = 9;
static const unsigned char PWD_XOR_KEY = 0x5A;

static const unsigned char OBFUSCATED_SALT[] = {
    'w'^0x3C, 'h'^0x3C, 'o'^0x3C, 'i'^0x3C, 's'^0x3C,
    'y'^0x3C, 'o'^0x3C, 'u'^0x3C, 'r'^0x3C, 'd'^0x3C,
    'a'^0x3C, 'd'^0x3C, 'd'^0x3C, 'y'^0x3C, '#' ^0x3C,
    '$'^0x3C, '@'^0x3C, '#' ^0x3C, '@'^0x3C
};
#define SALT_LEN 19
static const unsigned char SALT_XOR_KEY = 0x3C;

static int g_integrity_ok = 0;

static int is_debugger_present() {
#ifdef _WIN32
    return IsDebuggerPresent();
#else
    FILE *f = fopen("/proc/self/status", "r");
    if (f) {
        char line[256];
        while (fgets(line, sizeof(line), f)) {
            if (strncmp(line, "TracerPid:", 10) == 0) {
                int pid = atoi(line + 10);
                fclose(f);
                return pid != 0;
            }
        }
        fclose(f);
    }
    return 0;
#endif
}

static void md5_hash(const unsigned char *data, int len, unsigned char *out) {
    unsigned int s[4] = {0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476};
    unsigned int shifts[64] = {
        7,12,17,22, 7,12,17,22, 7,12,17,22, 7,12,17,22,
        5, 9,14,20, 5, 9,14,20, 5, 9,14,20, 5, 9,14,20,
        4,11,16,23, 4,11,16,23, 4,11,16,23, 4,11,16,23,
        6,10,15,21, 6,10,15,21, 6,10,15,21, 6,10,15,21
    };
    unsigned int k[64];
    for (int i = 0; i < 64; i++) {
        k[i] = (unsigned int)(fabs(sin(i + 1)) * 4294967296.0);
    }

    int padded_len = ((len + 8) / 64 + 1) * 64;
    unsigned char *padded = (unsigned char *)calloc(padded_len, 1);
    if (!padded) return;
    memcpy(padded, data, len);
    padded[len] = 0x80;
    unsigned long long bit_len = (unsigned long long)len * 8;
    memcpy(padded + padded_len - 8, &bit_len, 8);

    for (int i = 0; i < padded_len; i += 64) {
        unsigned int M[16];
        memcpy(M, padded + i, 64);
        unsigned int a = s[0], b = s[1], c = s[2], d = s[3];
        for (int j = 0; j < 64; j++) {
            unsigned int f, g;
            if (j < 16) { f = (b & c) | (~b & d); g = j; }
            else if (j < 32) { f = (d & b) | (~d & c); g = (5 * j + 1) % 16; }
            else if (j < 48) { f = b ^ c ^ d; g = (3 * j + 5) % 16; }
            else { f = c ^ (b | ~d); g = (7 * j) % 16; }
            f = f + a + k[j] + M[g];
            a = d; d = c; c = b;
            b = b + ((f << shifts[j]) | (f >> (32 - shifts[j])));
        }
        s[0] += a; s[1] += b; s[2] += c; s[3] += d;
    }
    free(padded);
    memcpy(out, s, 16);
}

static void aes_decrypt_block(const unsigned char *in, unsigned char *out, const unsigned char *round_keys, int nr) {
    unsigned char state[16];
    memcpy(state, in, 16);

    unsigned char sbox[256] = {
        0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
        0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
        0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
        0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
        0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
        0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
        0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
        0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
        0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
        0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
        0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
        0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
        0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
        0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
        0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
        0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
    };

    int r;
    unsigned char tmp[16];
    for (r = nr; r > 0; r--) {
        int offset = r * 16;
        for (int i = 0; i < 16; i++) {
            state[i] ^= round_keys[offset + i];
        }
        if (r < nr) {
            unsigned char t0 = state[13], t1 = state[14], t2 = state[15], t3 = state[12];
            state[12] = t0; state[13] = t1; state[14] = t2; state[15] = t3;
            t0 = state[7]; t1 = state[4]; t2 = state[5]; t3 = state[6];
            state[4] = t0; state[5] = t1; state[6] = t2; state[7] = t3;
            t0 = state[1]; t1 = state[2]; t2 = state[3]; t3 = state[0];
            state[8] = t0; state[9] = t1; state[10] = t2; state[11] = t3;
            for (int i = 0; i < 16; i++) {
                state[i] = sbox[state[i]];
            }
        }
    }
    for (int i = 0; i < 16; i++) {
        state[i] ^= round_keys[i];
    }
    memcpy(out, state, 16);
}

static void aes_key_expansion(const unsigned char *key, int key_len, unsigned char *round_keys, int *nr) {
    unsigned char sbox[256] = {
        0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
        0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
        0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
        0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
        0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
        0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
        0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
        0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
        0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
        0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
        0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
        0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
        0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
        0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
        0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
        0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
    };
    unsigned char rcon[11] = {0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36};

    int nk = key_len / 4;
    *nr = nk + 6;
    int total_words = (*nr + 1) * 4;

    memcpy(round_keys, key, key_len);

    for (int i = nk; i < total_words; i++) {
        unsigned char tmp[4];
        memcpy(tmp, round_keys + (i - 1) * 4, 4);

        if (i % nk == 0) {
            unsigned char t = tmp[0];
            tmp[0] = sbox[tmp[1]] ^ rcon[i / nk];
            tmp[1] = sbox[tmp[2]];
            tmp[2] = sbox[tmp[3]];
            tmp[3] = sbox[t];
        } else if (nk > 6 && i % nk == 4) {
            for (int j = 0; j < 4; j++) tmp[j] = sbox[tmp[j]];
        }

        for (int j = 0; j < 4; j++) {
            round_keys[i * 4 + j] = round_keys[(i - nk) * 4 + j] ^ tmp[j];
        }
    }
}

static int aes_ecb_decrypt(const unsigned char *input, int input_len,
                           unsigned char *output, const unsigned char *key, int key_len) {
    int nr;
    unsigned char round_keys[240];
    aes_key_expansion(key, key_len, round_keys, &nr);

    for (int i = 0; i < input_len; i += AES_BLOCK_SIZE) {
        aes_decrypt_block(input + i, output + i, round_keys, nr);
    }

    int pad = output[input_len - 1];
    if (pad < 1 || pad > AES_BLOCK_SIZE) return input_len;
    for (int i = input_len - pad; i < input_len; i++) {
        if (output[i] != pad) return input_len;
    }

    memset(round_keys, 0, sizeof(round_keys));
    return input_len - pad;
}

static void hex_from_md5(const unsigned char *md5_raw, int begin, int end, char *hex_out) {
    int x = 0;
    for (int i = begin; i < end; i++) {
        unsigned char b = md5_raw[i];
        char hi = (b >> 4) & 0x0F;
        char lo = b & 0x0F;
        hex_out[x++] = hi < 10 ? '0' + hi : 'a' + hi - 10;
        hex_out[x++] = lo < 10 ? '0' + lo : 'a' + lo - 10;
    }
    hex_out[x] = '\0';
}

JNIEXPORT jbyteArray JNICALL Java_i_i_i_NativeDecryptor_nativeGetPwd
  (JNIEnv *env, jclass cls) {
    if (is_debugger_present()) return NULL;

    char pwd[9];
    for (int i = 0; i < PWD_LEN; i++) {
        pwd[i] = OBFUSCATED_PWD[i] ^ PWD_XOR_KEY;
    }

    jbyteArray result = (*env)->NewByteArray(env, PWD_LEN);
    (*env)->SetByteArrayRegion(env, result, 0, PWD_LEN, (jbyte *)pwd);
    memset(pwd, 0, sizeof(pwd));
    return result;
}

JNIEXPORT jcharArray JNICALL Java_i_i_i_NativeDecryptor_nativeDeriveKey
  (JNIEnv *env, jclass cls) {
    if (is_debugger_present()) return NULL;

    jchar result[20];
    int x = 0;
    for (int i = 0; i < SECRET_A_LEN; i++) {
        result[x++] = (jchar)((SECRET_A[i] & 0xFF) >> 1);
    }
    for (int i = 0; i < SECRET_B_LEN; i++) {
        result[x++] = (jchar)((SECRET_B[i] & 0xFF) >> 1);
    }

    jcharArray arr = (*env)->NewCharArray(env, 20);
    (*env)->SetCharArrayRegion(env, arr, 0, 20, result);
    return arr;
}

JNIEXPORT jbyteArray JNICALL Java_i_i_i_NativeDecryptor_nativeMd5
  (JNIEnv *env, jclass cls, jbyteArray data) {
    if (is_debugger_present()) return NULL;

    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);

    unsigned char md5_result[MD5_DIGEST_LENGTH];
    md5_hash((unsigned char *)bytes, len, md5_result);

    (*env)->ReleaseByteArrayElements(env, data, bytes, 0);

    jbyteArray result = (*env)->NewByteArray(env, MD5_DIGEST_LENGTH);
    (*env)->SetByteArrayRegion(env, result, 0, MD5_DIGEST_LENGTH, (jbyte *)md5_result);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_i_i_i_NativeDecryptor_nativeDecrypt
  (JNIEnv *env, jclass cls, jbyteArray encryptedData, jbyteArray key) {
    if (is_debugger_present()) return NULL;

    jsize dataLen = (*env)->GetArrayLength(env, encryptedData);
    jbyte *data = (*env)->GetByteArrayElements(env, encryptedData, NULL);
    jsize keyLen = (*env)->GetArrayLength(env, key);
    jbyte *keyBytes = (*env)->GetByteArrayElements(env, key, NULL);

    unsigned char *output = (unsigned char *)malloc(dataLen);
    if (!output) {
        (*env)->ReleaseByteArrayElements(env, encryptedData, data, 0);
        (*env)->ReleaseByteArrayElements(env, key, keyBytes, 0);
        return NULL;
    }

    int actualLen = aes_ecb_decrypt((unsigned char *)data, dataLen, output,
                                    (unsigned char *)keyBytes, keyLen);

    (*env)->ReleaseByteArrayElements(env, encryptedData, data, 0);
    (*env)->ReleaseByteArrayElements(env, key, keyBytes, 0);

    jbyteArray result = (*env)->NewByteArray(env, actualLen);
    (*env)->SetByteArrayRegion(env, result, 0, actualLen, (jbyte *)output);
    memset(output, 0, dataLen);
    free(output);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_i_i_i_NativeDecryptor_nativeFullDecrypt
  (JNIEnv *env, jclass cls, jbyteArray encryptedData, jstring fileName, jcharArray derivedKey) {
    if (is_debugger_present()) return NULL;

    jsize keyLen = (*env)->GetArrayLength(env, derivedKey);
    jchar *keyChars = (*env)->GetCharArrayElements(env, derivedKey, NULL);

    if (keyLen >= 2) {
        keyChars[0] = keyChars[1];
    }

    const char *fname = (*env)->GetStringUTFChars(env, fileName, NULL);
    int fnameLen = (int)strlen(fname);

    int passTotalLen = keyLen + fnameLen;
    char *pass = (char *)malloc(passTotalLen * 2);
    if (!pass) {
        (*env)->ReleaseCharArrayElements(env, derivedKey, keyChars, 0);
        (*env)->ReleaseStringUTFChars(env, fileName, fname);
        return NULL;
    }

    int passBytesLen = 0;
    for (int i = 0; i < keyLen; i++) {
        pass[passBytesLen++] = (char)(keyChars[i] & 0xFF);
    }
    for (int i = 0; i < fnameLen; i++) {
        pass[passBytesLen++] = fname[i];
    }

    char salt[SALT_LEN];
    for (int i = 0; i < SALT_LEN; i++) {
        salt[i] = OBFUSCATED_SALT[i] ^ SALT_XOR_KEY;
    }

    int saltedLen = passBytesLen + SALT_LEN;
    char *salted = (char *)malloc(saltedLen);
    if (!salted) {
        memset(pass, 0, passTotalLen * 2);
        free(pass);
        (*env)->ReleaseCharArrayElements(env, derivedKey, keyChars, 0);
        (*env)->ReleaseStringUTFChars(env, fileName, fname);
        return NULL;
    }
    memcpy(salted, pass, passBytesLen);
    memcpy(salted + passBytesLen, salt, SALT_LEN);

    unsigned char md5_result[MD5_DIGEST_LENGTH];
    md5_hash((unsigned char *)salted, saltedLen, md5_result);

    char hex_md5[33];
    hex_from_md5(md5_result, 8, 16, hex_md5);

    jsize dataLen = (*env)->GetArrayLength(env, encryptedData);
    jbyte *data = (*env)->GetByteArrayElements(env, encryptedData, NULL);

    unsigned char *output = (unsigned char *)malloc(dataLen);
    if (!output) {
        (*env)->ReleaseByteArrayElements(env, encryptedData, data, 0);
        memset(pass, 0, passTotalLen * 2);
        memset(salted, 0, saltedLen);
        memset(salt, 0, SALT_LEN);
        memset(md5_result, 0, MD5_DIGEST_LENGTH);
        memset(hex_md5, 0, 33);
        free(pass);
        free(salted);
        (*env)->ReleaseCharArrayElements(env, derivedKey, keyChars, 0);
        (*env)->ReleaseStringUTFChars(env, fileName, fname);
        return NULL;
    }

    int actualLen = aes_ecb_decrypt((unsigned char *)data, dataLen, output,
                                    (unsigned char *)hex_md5, 16);

    jbyteArray result = (*env)->NewByteArray(env, actualLen);
    (*env)->SetByteArrayRegion(env, result, 0, actualLen, (jbyte *)output);
    memset(output, 0, dataLen);
    free(output);
    (*env)->ReleaseByteArrayElements(env, encryptedData, data, 0);

    memset(pass, 0, passTotalLen * 2);
    memset(salted, 0, saltedLen);
    memset(salt, 0, SALT_LEN);
    memset(md5_result, 0, MD5_DIGEST_LENGTH);
    memset(hex_md5, 0, 33);
    free(pass);
    free(salted);
    (*env)->ReleaseCharArrayElements(env, derivedKey, keyChars, 0);
    (*env)->ReleaseStringUTFChars(env, fileName, fname);

    return result;
}
