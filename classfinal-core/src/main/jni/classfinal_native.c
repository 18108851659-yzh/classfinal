#include <string.h>
#include <stdlib.h>
#include <jni.h>

#ifdef _WIN32
#include <windows.h>
#elif defined(__APPLE__)
#include <stdio.h>
#include <unistd.h>
#include <sys/sysctl.h>
#else
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#endif

#define AES_BLOCK_SIZE 16
#define MD5_DIGEST_LENGTH 16

static unsigned int g_expected_crc32 = 0;
static int g_integrity_ok = 0;

static const unsigned char KEY_PART_A[] = {
    0x9E,0xC6,0xB3,0xD1,0xAB,0xF2,0x8D,0xE4,
    0x71,0xBC,0x5A,0xCF,0x63,0xDA,0x47,0xE8
};
#define PART_A_LEN 16
static const unsigned char XOR_A = 0xF9;

static const unsigned char KEY_PART_B[] = {
    0xD2,0xE8,0xF1,0xC3,0x9A,0xB7,0x6E,0xAD,
    0x54,0xCB,0x3F,0x82,0x71,0xD6,0x4C,0xE5
};
#define PART_B_LEN 16
static const unsigned char XOR_B = 0x7A;

static const unsigned char KEY_ORDER[] = {
    1,0,3,2,5,4,7,6,9,8,11,10,13,12,15,14,
    17,16,19,18,21,20,23,22,25,24,27,26,29,28,31,30
};
#define KEY_LEN 32

static const unsigned char OBFUSCATED_SALT[] = {
    'w'^0x3C, 'h'^0x3C, 'o'^0x3C, 'i'^0x3C, 's'^0x3C,
    'y'^0x3C, 'o'^0x3C, 'u'^0x3C, 'r'^0x3C, 'd'^0x3C,
    'a'^0x3C, 'd'^0x3C, 'd'^0x3C, 'y'^0x3C, '#' ^0x3C,
    '$'^0x3C, '@'^0x3C, '#' ^0x3C, '@'^0x3C
};
#define SALT_LEN 19
static const unsigned char SALT_XOR_KEY = 0x3C;

static unsigned int crc32_table[256];
static int crc32_table_init = 0;

static void init_crc32_table(void) {
    if (crc32_table_init) return;
    for (unsigned int i = 0; i < 256; i++) {
        unsigned int crc = i;
        for (int j = 0; j < 8; j++) {
            if (crc & 1) crc = 0xEDB88320 ^ (crc >> 1);
            else crc >>= 1;
        }
        crc32_table[i] = crc;
    }
    crc32_table_init = 1;
}

static unsigned int compute_crc32(const unsigned char *data, int len) {
    init_crc32_table();
    unsigned int crc = 0xFFFFFFFF;
    for (int i = 0; i < len; i++) {
        crc = crc32_table[(crc ^ data[i]) & 0xFF] ^ (crc >> 8);
    }
    return crc ^ 0xFFFFFFFF;
}

static void fn_set_crc(JNIEnv *env, jclass cls, jint crc);
static jint fn_verify(JNIEnv *env, jclass cls);
static jbyteArray fn_decrypt(JNIEnv *env, jclass cls, jbyteArray encryptedData, jstring className);

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_integrity_ok = 0;

    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_8) != JNI_OK) {
        return JNI_ERR;
    }

    jclass cls = (*env)->FindClass(env, "net/roseboy/classfinal/jni/NativeDecryptor");
    if (cls == NULL) {
        return JNI_ERR;
    }

    JNINativeMethod methods[] = {
        {"nativeDecryptClass", "([BLjava/lang/String;)[B", (void*)fn_decrypt},
        {"nativeSetCrc", "(I)V", (void*)fn_set_crc},
        {"nativeVerifyIntegrity", "()I", (void*)fn_verify}
    };

    if ((*env)->RegisterNatives(env, cls, methods, sizeof(methods) / sizeof(methods[0])) != 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_8;
}

static void fn_set_crc(JNIEnv *env, jclass cls, jint crc) {
    g_expected_crc32 = (unsigned int)crc;
    g_integrity_ok = 1;
}

static jint fn_verify(JNIEnv *env, jclass cls) {
    return g_integrity_ok ? 1 : 0;
}

static int check_integrity(void) {
    if (g_expected_crc32 == 0) return 1;
    return g_integrity_ok;
}

void set_integrity_from_file(const char *lib_path) {
    if (g_expected_crc32 == 0) {
        g_integrity_ok = 1;
        return;
    }
#ifdef _WIN32
    HANDLE hFile = CreateFileA(lib_path, GENERIC_READ, FILE_SHARE_READ, NULL,
                               OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (hFile == INVALID_HANDLE_VALUE) { g_integrity_ok = 0; return; }
    HANDLE hMap = CreateFileMappingA(hFile, NULL, PAGE_READONLY, 0, 0, NULL);
    if (!hMap) { CloseHandle(hFile); g_integrity_ok = 0; return; }
    LPVOID base = MapViewOfFile(hMap, FILE_MAP_READ, 0, 0, 0);
    if (!base) { CloseHandle(hMap); CloseHandle(hFile); g_integrity_ok = 0; return; }
    DWORD fileSize = GetFileSize(hFile, NULL);
    unsigned int crc = compute_crc32((const unsigned char *)base, (int)fileSize);
    UnmapViewOfFile(base);
    CloseHandle(hMap);
    CloseHandle(hFile);
    g_integrity_ok = (crc == g_expected_crc32) ? 1 : 0;
#else
    FILE *f = fopen(lib_path, "rb");
    if (!f) { g_integrity_ok = 0; return; }
    fseek(f, 0, SEEK_END);
    long fileSize = ftell(f);
    fseek(f, 0, SEEK_SET);
    unsigned char *buf = (unsigned char *)malloc(fileSize);
    if (!buf) { fclose(f); g_integrity_ok = 0; return; }
    fread(buf, 1, fileSize, f);
    fclose(f);
    unsigned int crc = compute_crc32(buf, (int)fileSize);
    free(buf);
    g_integrity_ok = (crc == g_expected_crc32) ? 1 : 0;
#endif
}

static int is_debugger_present() {
#ifdef _WIN32
    if (IsDebuggerPresent()) return 1;
    return 0;
#elif defined(__APPLE__)
    /* macOS: 检测 P_TRACED 标志 */
    int mib[4];
    struct kinfo_proc info;
    size_t size = sizeof(info);
    mib[0] = CTL_KERN;
    mib[1] = KERN_PROC;
    mib[2] = KERN_PROC_PID;
    mib[3] = getpid();
    if (sysctl(mib, 4, &info, &size, NULL, 0) == 0) {
        return (info.kp_proc.p_flag & P_TRACED) != 0;
    }
    return 0;
#else
    /* Linux: 读取 /proc/self/status 中的 TracerPid */
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

static void assemble_key(char *out) {
    char part_a[PART_A_LEN];
    for (int i = 0; i < PART_A_LEN; i++) {
        part_a[i] = (char)(KEY_PART_A[i] ^ XOR_A);
    }
    char part_b[PART_B_LEN];
    for (int i = 0; i < PART_B_LEN; i++) {
        part_b[i] = (char)(KEY_PART_B[i] ^ XOR_B);
    }
    char raw[KEY_LEN];
    for (int i = 0; i < PART_A_LEN; i++) {
        raw[i] = part_a[i];
    }
    for (int i = 0; i < PART_B_LEN; i++) {
        raw[PART_A_LEN + i] = part_b[i];
    }
    for (int i = 0; i < KEY_LEN; i++) {
        out[i] = raw[KEY_ORDER[i]];
    }
    memset(part_a, 0, PART_A_LEN);
    memset(part_b, 0, PART_B_LEN);
    memset(raw, 0, KEY_LEN);
}

static void md5_hash(const unsigned char *data, int len, unsigned char *out) {
    unsigned int s[4] = {0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476};
    static const unsigned int shifts[64] = {
        7,12,17,22, 7,12,17,22, 7,12,17,22, 7,12,17,22,
        5, 9,14,20, 5, 9,14,20, 5, 9,14,20, 5, 9,14,20,
        4,11,16,23, 4,11,16,23, 4,11,16,23, 4,11,16,23,
        6,10,15,21, 6,10,15,21, 6,10,15,21, 6,10,15,21
    };
    static const unsigned int k[64] = {
        0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee,
        0xf57c0faf, 0x4787c62a, 0xa8304613, 0xfd469501,
        0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be,
        0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821,
        0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa,
        0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
        0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed,
        0xa9e3e905, 0xfcefa3f8, 0x676f02d9, 0x8d2a4c8a,
        0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c,
        0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70,
        0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05,
        0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
        0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039,
        0x655b59c3, 0x8f0ccc92, 0xffeff47d, 0x85845dd1,
        0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1,
        0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391
    };

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

static unsigned char gmul(unsigned char a, unsigned char b) {
    unsigned char p = 0;
    for (int i = 0; i < 8; i++) {
        if (b & 1) p ^= a;
        unsigned char hi = a & 0x80;
        a <<= 1;
        if (hi) a ^= 0x1b;
        b >>= 1;
    }
    return p;
}

static void inv_mix_column(unsigned char *col) {
    unsigned char a0 = col[0], a1 = col[1], a2 = col[2], a3 = col[3];
    col[0] = gmul(a0, 0x0e) ^ gmul(a1, 0x0b) ^ gmul(a2, 0x0d) ^ gmul(a3, 0x09);
    col[1] = gmul(a0, 0x09) ^ gmul(a1, 0x0e) ^ gmul(a2, 0x0b) ^ gmul(a3, 0x0d);
    col[2] = gmul(a0, 0x0d) ^ gmul(a1, 0x09) ^ gmul(a2, 0x0e) ^ gmul(a3, 0x0b);
    col[3] = gmul(a0, 0x0b) ^ gmul(a1, 0x0d) ^ gmul(a2, 0x09) ^ gmul(a3, 0x0e);
}

static void aes_key_expansion(const unsigned char *key, int key_len, unsigned char *round_keys, int *nr) {
    static const unsigned char sbox[256] = {
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
    static const unsigned char rcon[11] = {0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36};

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

static void aes_decrypt_block(const unsigned char *in, unsigned char *out, const unsigned char *round_keys, int nr) {
    static const unsigned char inv_sbox[256] = {
        0x52,0x09,0x6a,0xd5,0x30,0x36,0xa5,0x38,0xbf,0x40,0xa3,0x9e,0x81,0xf3,0xd7,0xfb,
        0x7c,0xe3,0x39,0x82,0x9b,0x2f,0xff,0x87,0x34,0x8e,0x43,0x44,0xc4,0xde,0xe9,0xcb,
        0x54,0x7b,0x94,0x32,0xa6,0xc2,0x23,0x3d,0xee,0x4c,0x95,0x0b,0x42,0xfa,0xc3,0x4e,
        0x08,0x2e,0xa1,0x66,0x28,0xd9,0x24,0xb2,0x76,0x5b,0xa2,0x49,0x6d,0x8b,0xd1,0x25,
        0x72,0xf8,0xf6,0x64,0x86,0x68,0x98,0x16,0xd4,0xa4,0x5c,0xcc,0x5d,0x65,0xb6,0x92,
        0x6c,0x70,0x48,0x50,0xfd,0xed,0xb9,0xda,0x5e,0x15,0x46,0x57,0xa7,0x8d,0x9d,0x84,
        0x90,0xd8,0xab,0x00,0x8c,0xbc,0xd3,0x0a,0xf7,0xe4,0x58,0x05,0xb8,0xb3,0x45,0x06,
        0xd0,0x2c,0x1e,0x8f,0xca,0x3f,0x0f,0x02,0xc1,0xaf,0xbd,0x03,0x01,0x13,0x8a,0x6b,
        0x3a,0x91,0x11,0x41,0x4f,0x67,0xdc,0xea,0x97,0xf2,0xcf,0xce,0xf0,0xb4,0xe6,0x73,
        0x96,0xac,0x74,0x22,0xe7,0xad,0x35,0x85,0xe2,0xf9,0x37,0xe8,0x1c,0x75,0xdf,0x6e,
        0x47,0xf1,0x1a,0x71,0x1d,0x29,0xc5,0x89,0x6f,0xb7,0x62,0x0e,0xaa,0x18,0xbe,0x1b,
        0xfc,0x56,0x3e,0x4b,0xc6,0xd2,0x79,0x20,0x9a,0xdb,0xc0,0xfe,0x78,0xcd,0x5a,0xf4,
        0x1f,0xdd,0xa8,0x33,0x88,0x07,0xc7,0x31,0xb1,0x12,0x10,0x59,0x27,0x80,0xec,0x5f,
        0x60,0x51,0x7f,0xa9,0x19,0xb5,0x4a,0x0d,0x2d,0xe5,0x7a,0x9f,0x93,0xc9,0x9c,0xef,
        0xa0,0xe0,0x3b,0x4d,0xae,0x2a,0xf5,0xb0,0xc8,0xeb,0xbb,0x3c,0x83,0x53,0x99,0x61,
        0x17,0x2b,0x04,0x7e,0xba,0x77,0xd6,0x26,0xe1,0x69,0x14,0x63,0x55,0x21,0x0c,0x7d
    };

    unsigned char state[16];
    memcpy(state, in, 16);

    for (int i = 0; i < 16; i++) {
        state[i] ^= round_keys[nr * 16 + i];
    }

    for (int r = nr - 1; r >= 1; r--) {
        unsigned char tmp[16];
        tmp[0]  = state[0];  tmp[1]  = state[13]; tmp[2]  = state[10]; tmp[3]  = state[7];
        tmp[4]  = state[4];  tmp[5]  = state[1];  tmp[6]  = state[14]; tmp[7]  = state[11];
        tmp[8]  = state[8];  tmp[9]  = state[5];  tmp[10] = state[2];  tmp[11] = state[15];
        tmp[12] = state[12]; tmp[13] = state[9];  tmp[14] = state[6];  tmp[15] = state[3];
        memcpy(state, tmp, 16);

        for (int i = 0; i < 16; i++) {
            state[i] = inv_sbox[state[i]];
        }

        for (int i = 0; i < 16; i++) {
            state[i] ^= round_keys[r * 16 + i];
        }

        unsigned char col[4];
        col[0] = state[0]; col[1] = state[1]; col[2] = state[2]; col[3] = state[3];
        inv_mix_column(col);
        state[0] = col[0]; state[1] = col[1]; state[2] = col[2]; state[3] = col[3];

        col[0] = state[4]; col[1] = state[5]; col[2] = state[6]; col[3] = state[7];
        inv_mix_column(col);
        state[4] = col[0]; state[5] = col[1]; state[6] = col[2]; state[7] = col[3];

        col[0] = state[8]; col[1] = state[9]; col[2] = state[10]; col[3] = state[11];
        inv_mix_column(col);
        state[8] = col[0]; state[9] = col[1]; state[10] = col[2]; state[11] = col[3];

        col[0] = state[12]; col[1] = state[13]; col[2] = state[14]; col[3] = state[15];
        inv_mix_column(col);
        state[12] = col[0]; state[13] = col[1]; state[14] = col[2]; state[15] = col[3];
    }

    {
        unsigned char tmp[16];
        tmp[0]  = state[0];  tmp[1]  = state[13]; tmp[2]  = state[10]; tmp[3]  = state[7];
        tmp[4]  = state[4];  tmp[5]  = state[1];  tmp[6]  = state[14]; tmp[7]  = state[11];
        tmp[8]  = state[8];  tmp[9]  = state[5];  tmp[10] = state[2];  tmp[11] = state[15];
        tmp[12] = state[12]; tmp[13] = state[9];  tmp[14] = state[6];  tmp[15] = state[3];
        memcpy(state, tmp, 16);
    }

    for (int i = 0; i < 16; i++) {
        state[i] = inv_sbox[state[i]];
    }

    for (int i = 0; i < 16; i++) {
        state[i] ^= round_keys[i];
    }

    memcpy(out, state, 16);
}

static int aes_cbc_decrypt(const unsigned char *input, int input_len,
                           unsigned char *output, const unsigned char *key, int key_len,
                           const unsigned char *iv) {
    int nr;
    unsigned char round_keys[240];
    aes_key_expansion(key, key_len, round_keys, &nr);

    unsigned char prev_block[16];
    memcpy(prev_block, iv, 16);

    for (int i = 0; i < input_len; i += AES_BLOCK_SIZE) {
        unsigned char decrypted[16];
        aes_decrypt_block(input + i, decrypted, round_keys, nr);

        for (int j = 0; j < AES_BLOCK_SIZE; j++) {
            output[i + j] = decrypted[j] ^ prev_block[j];
        }
        memcpy(prev_block, input + i, 16);
    }

    int pad = output[input_len - 1];
    if (pad < 1 || pad > AES_BLOCK_SIZE) {
        memset(round_keys, 0, sizeof(round_keys));
        return input_len;
    }
    for (int i = input_len - pad; i < input_len; i++) {
        if (output[i] != pad) {
            memset(round_keys, 0, sizeof(round_keys));
            return input_len;
        }
    }

    memset(round_keys, 0, sizeof(round_keys));
    return input_len - pad;
}

static int utf8_encode_char(jchar c, unsigned char *out) {
    if (c < 0x80) {
        out[0] = (unsigned char)c;
        return 1;
    } else if (c < 0x800) {
        out[0] = (unsigned char)(0xC0 | (c >> 6));
        out[1] = (unsigned char)(0x80 | (c & 0x3F));
        return 2;
    } else {
        out[0] = (unsigned char)(0xE0 | (c >> 12));
        out[1] = (unsigned char)(0x80 | ((c >> 6) & 0x3F));
        out[2] = (unsigned char)(0x80 | (c & 0x3F));
        return 3;
    }
}

static int chars_to_utf8(const jchar *chars, int char_len, unsigned char *out, int out_cap) {
    int pos = 0;
    for (int i = 0; i < char_len; i++) {
        unsigned char buf[3];
        int n = utf8_encode_char(chars[i], buf);
        if (pos + n > out_cap) return pos;
        memcpy(out + pos, buf, n);
        pos += n;
    }
    return pos;
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

static jbyteArray fn_decrypt(JNIEnv *env, jclass cls, jbyteArray encryptedData, jstring className) {
    if (is_debugger_present()) return NULL;
    if (!check_integrity()) return NULL;

    char key[KEY_LEN + 1];
    assemble_key(key);
    key[KEY_LEN] = '\0';

    const char *fname = (*env)->GetStringUTFChars(env, className, NULL);
    int fnameLen = (int)strlen(fname);

    char salt[SALT_LEN];
    for (int i = 0; i < SALT_LEN; i++) {
        salt[i] = OBFUSCATED_SALT[i] ^ SALT_XOR_KEY;
    }

    int merged_char_len = KEY_LEN + fnameLen;
    jchar *merged = (jchar *)malloc(merged_char_len * sizeof(jchar));
    if (!merged) {
        memset(key, 0, KEY_LEN + 1);
        memset(salt, 0, SALT_LEN);
        (*env)->ReleaseStringUTFChars(env, className, fname);
        return NULL;
    }
    for (int i = 0; i < KEY_LEN; i++) {
        merged[i] = (jchar)key[i];
    }
    for (int i = 0; i < fnameLen; i++) {
        merged[KEY_LEN + i] = (jchar)fname[i];
    }

    int salted_char_len = merged_char_len + SALT_LEN;
    jchar *salted = (jchar *)malloc(salted_char_len * sizeof(jchar));
    if (!salted) {
        memset(merged, 0, merged_char_len * sizeof(jchar));
        free(merged);
        memset(key, 0, KEY_LEN + 1);
        memset(salt, 0, SALT_LEN);
        (*env)->ReleaseStringUTFChars(env, className, fname);
        return NULL;
    }
    memcpy(salted, merged, merged_char_len * sizeof(jchar));
    for (int i = 0; i < SALT_LEN; i++) {
        salted[merged_char_len + i] = (jchar)salt[i];
    }

    int utf8_cap = salted_char_len * 3 + 1;
    unsigned char *utf8_buf = (unsigned char *)malloc(utf8_cap);
    if (!utf8_buf) {
        memset(merged, 0, merged_char_len * sizeof(jchar));
        free(merged);
        memset(salted, 0, salted_char_len * sizeof(jchar));
        free(salted);
        memset(key, 0, KEY_LEN + 1);
        memset(salt, 0, SALT_LEN);
        (*env)->ReleaseStringUTFChars(env, className, fname);
        return NULL;
    }
    int utf8_len = chars_to_utf8(salted, salted_char_len, utf8_buf, utf8_cap);

    unsigned char md5_result[MD5_DIGEST_LENGTH];
    md5_hash(utf8_buf, utf8_len, md5_result);

    char hex_md5[33];
    hex_from_md5(md5_result, 8, 16, hex_md5);

    jsize dataLen = (*env)->GetArrayLength(env, encryptedData);
    jbyte *data = (*env)->GetByteArrayElements(env, encryptedData, NULL);

    if (dataLen < 4 + AES_BLOCK_SIZE + AES_BLOCK_SIZE) {
        (*env)->ReleaseByteArrayElements(env, encryptedData, data, 0);
        memset(utf8_buf, 0, utf8_cap);
        free(utf8_buf);
        memset(merged, 0, merged_char_len * sizeof(jchar));
        free(merged);
        memset(salted, 0, salted_char_len * sizeof(jchar));
        free(salted);
        memset(salt, 0, SALT_LEN);
        memset(md5_result, 0, MD5_DIGEST_LENGTH);
        memset(hex_md5, 0, 33);
        memset(key, 0, KEY_LEN + 1);
        (*env)->ReleaseStringUTFChars(env, className, fname);
        return NULL;
    }

    /* P2: 跳过4字节头部伪装(0xCAFEBABE) */
    unsigned char *iv = (unsigned char *)(data + 4);
    int cipher_len = dataLen - 4 - AES_BLOCK_SIZE;
    unsigned char *output = (unsigned char *)malloc(cipher_len);
    if (!output) {
        (*env)->ReleaseByteArrayElements(env, encryptedData, data, 0);
        memset(utf8_buf, 0, utf8_cap);
        free(utf8_buf);
        memset(merged, 0, merged_char_len * sizeof(jchar));
        free(merged);
        memset(salted, 0, salted_char_len * sizeof(jchar));
        free(salted);
        memset(salt, 0, SALT_LEN);
        memset(md5_result, 0, MD5_DIGEST_LENGTH);
        memset(hex_md5, 0, 33);
        memset(key, 0, KEY_LEN + 1);
        (*env)->ReleaseStringUTFChars(env, className, fname);
        return NULL;
    }

    int actualLen = aes_cbc_decrypt((unsigned char *)(data + 4 + AES_BLOCK_SIZE), cipher_len,
                                    output, (unsigned char *)hex_md5, 16, iv);

    jbyteArray result = (*env)->NewByteArray(env, actualLen);
    (*env)->SetByteArrayRegion(env, result, 0, actualLen, (jbyte *)output);
    memset(output, 0, cipher_len);
    free(output);
    (*env)->ReleaseByteArrayElements(env, encryptedData, data, 0);

    memset(utf8_buf, 0, utf8_cap);
    free(utf8_buf);
    memset(merged, 0, merged_char_len * sizeof(jchar));
    free(merged);
    memset(salted, 0, salted_char_len * sizeof(jchar));
    free(salted);
    memset(salt, 0, SALT_LEN);
    memset(md5_result, 0, MD5_DIGEST_LENGTH);
    memset(hex_md5, 0, 33);
    memset(key, 0, KEY_LEN + 1);
    (*env)->ReleaseStringUTFChars(env, className, fname);

    return result;
}
